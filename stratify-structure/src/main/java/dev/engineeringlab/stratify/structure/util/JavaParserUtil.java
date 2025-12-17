package dev.engineeringlab.stratify.structure.util;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Utility class for JavaParser AST operations.
 *
 * <p>Provides common patterns for parsing Java files and extracting class information using AST
 * instead of regex, which is more reliable and accurate.
 */
public final class JavaParserUtil {

  private static final Set<String> EXCEPTION_SUPERCLASSES;

  static {
    // Configure parser to support Java 21 features (including records)
    StaticJavaParser.getParserConfiguration()
        .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);

    // Initialize exception superclasses set
    Set<String> exceptions = new HashSet<>();
    exceptions.add("Exception");
    exceptions.add("RuntimeException");
    exceptions.add("Throwable");
    exceptions.add("Error");
    exceptions.add("IllegalArgumentException");
    exceptions.add("IllegalStateException");
    exceptions.add("IOException");
    exceptions.add("SQLException");
    exceptions.add("NullPointerException");
    exceptions.add("IndexOutOfBoundsException");
    exceptions.add("UnsupportedOperationException");
    EXCEPTION_SUPERCLASSES = Collections.unmodifiableSet(exceptions);
  }

  private JavaParserUtil() {
    // Utility class
  }

  /**
   * Parses a Java file and returns the CompilationUnit.
   *
   * @param javaFile Path to the Java file
   * @return Optional containing the CompilationUnit, or empty if parsing fails
   */
  public static Optional<CompilationUnit> parse(Path javaFile) {
    try {
      return Optional.of(StaticJavaParser.parse(javaFile));
    } catch (IOException | ParseProblemException e) {
      return Optional.empty();
    }
  }

  /**
   * Parses a Java file and processes it with the given consumer.
   *
   * @param javaFile Path to the Java file
   * @param processor Consumer to process the CompilationUnit
   */
  public static void parseAndProcess(Path javaFile, Consumer<CompilationUnit> processor) {
    parse(javaFile).ifPresent(processor);
  }

  /**
   * Finds all class/interface declarations in a Java file.
   *
   * @param javaFile Path to the Java file
   * @return List of ClassOrInterfaceDeclaration, empty if parsing fails
   */
  public static List<ClassOrInterfaceDeclaration> findAllClasses(Path javaFile) {
    return parse(javaFile)
        .map(cu -> cu.findAll(ClassOrInterfaceDeclaration.class))
        .orElse(Collections.emptyList());
  }

  /**
   * Finds all class declarations (not interfaces) in a Java file.
   *
   * @param javaFile Path to the Java file
   * @return List of class declarations
   */
  public static List<ClassOrInterfaceDeclaration> findClasses(Path javaFile) {
    return findAllClasses(javaFile).stream()
        .filter(decl -> !decl.isInterface())
        .collect(Collectors.toList());
  }

  /**
   * Finds all interface declarations in a Java file.
   *
   * @param javaFile Path to the Java file
   * @return List of interface declarations
   */
  public static List<ClassOrInterfaceDeclaration> findInterfaces(Path javaFile) {
    return findAllClasses(javaFile).stream()
        .filter(ClassOrInterfaceDeclaration::isInterface)
        .collect(Collectors.toList());
  }

  /**
   * Finds all abstract class declarations in a Java file.
   *
   * @param javaFile Path to the Java file
   * @return List of abstract class declarations
   */
  public static List<ClassOrInterfaceDeclaration> findAbstractClasses(Path javaFile) {
    return findAllClasses(javaFile).stream()
        .filter(decl -> !decl.isInterface() && decl.isAbstract())
        .collect(Collectors.toList());
  }

  /**
   * Finds all concrete (non-abstract, non-interface) class declarations in a Java file.
   *
   * @param javaFile Path to the Java file
   * @return List of concrete class declarations
   */
  public static List<ClassOrInterfaceDeclaration> findConcreteClasses(Path javaFile) {
    return findAllClasses(javaFile).stream()
        .filter(decl -> !decl.isInterface() && !decl.isAbstract())
        .collect(Collectors.toList());
  }

  /**
   * Finds classes matching a predicate in a Java file.
   *
   * @param javaFile Path to the Java file
   * @param predicate Filter predicate
   * @return List of matching class declarations
   */
  public static List<ClassOrInterfaceDeclaration> findClassesMatching(
      Path javaFile, Predicate<ClassOrInterfaceDeclaration> predicate) {
    return findAllClasses(javaFile).stream().filter(predicate).collect(Collectors.toList());
  }

  /**
   * Gets the superclass name of a class declaration.
   *
   * @param decl Class declaration
   * @return Optional containing the superclass name, or empty if no superclass
   */
  public static Optional<String> getSuperclassName(ClassOrInterfaceDeclaration decl) {
    return decl.getExtendedTypes().stream().findFirst().map(ClassOrInterfaceType::getNameAsString);
  }

  /**
   * Gets all implemented interface names of a class declaration.
   *
   * @param decl Class declaration
   * @return Set of implemented interface names
   */
  public static Set<String> getImplementedInterfaces(ClassOrInterfaceDeclaration decl) {
    return decl.getImplementedTypes().stream()
        .map(ClassOrInterfaceType::getNameAsString)
        .collect(Collectors.toSet());
  }

  /**
   * Checks if a class implements a specific interface.
   *
   * @param decl Class declaration
   * @param interfaceName Interface name to check
   * @return true if the class implements the interface
   */
  public static boolean implementsInterface(
      ClassOrInterfaceDeclaration decl, String interfaceName) {
    return getImplementedInterfaces(decl).contains(interfaceName);
  }

  /**
   * Checks if a class implements any of the given interfaces.
   *
   * @param decl Class declaration
   * @param interfaceNames Set of interface names to check
   * @return true if the class implements any of the interfaces
   */
  public static boolean implementsAnyInterface(
      ClassOrInterfaceDeclaration decl, Set<String> interfaceNames) {
    Set<String> implemented = getImplementedInterfaces(decl);
    return interfaceNames.stream().anyMatch(implemented::contains);
  }

  /**
   * Checks if a class extends an exception type.
   *
   * @param decl Class declaration
   * @return true if the class extends an exception type
   */
  public static boolean isExceptionClass(ClassOrInterfaceDeclaration decl) {
    return getSuperclassName(decl).map(JavaParserUtil::isExceptionSuperclass).orElse(false);
  }

  /**
   * Checks if a class name is an exception superclass.
   *
   * @param className Class name to check
   * @return true if the class name is an exception superclass
   */
  public static boolean isExceptionSuperclass(String className) {
    return EXCEPTION_SUPERCLASSES.contains(className)
        || className.endsWith("Exception")
        || className.endsWith("Error");
  }

  /**
   * Checks if a class has a method with the given name.
   *
   * @param decl Class declaration
   * @param methodName Method name to check
   * @return true if the class has a method with the given name
   */
  public static boolean hasMethod(ClassOrInterfaceDeclaration decl, String methodName) {
    return decl.getMethods().stream().anyMatch(m -> m.getNameAsString().equals(methodName));
  }

  /**
   * Checks if a class has a static method with the given name prefix.
   *
   * @param decl Class declaration
   * @param namePrefix Method name prefix
   * @return true if the class has a static method with the given prefix
   */
  public static boolean hasStaticMethodWithPrefix(
      ClassOrInterfaceDeclaration decl, String namePrefix) {
    return decl.getMethods().stream()
        .anyMatch(
            m ->
                m.isStatic()
                    && m.getNameAsString().toLowerCase().startsWith(namePrefix.toLowerCase()));
  }

  /**
   * Checks if a class has factory-like methods (create*, newInstance*, getInstance*).
   *
   * @param decl Class declaration
   * @return true if the class has factory-like methods
   */
  public static boolean hasFactoryMethods(ClassOrInterfaceDeclaration decl) {
    return decl.getMethods().stream()
        .anyMatch(
            method -> {
              String name = method.getNameAsString().toLowerCase();
              return method.isStatic()
                  && (name.startsWith("create")
                      || name.startsWith("newinstance")
                      || name.startsWith("getinstance"));
            });
  }

  /**
   * Checks if a class has builder-like methods (build() and fluent setters returning this).
   *
   * @param decl Class declaration
   * @return true if the class has builder-like methods
   */
  public static boolean hasBuilderMethods(ClassOrInterfaceDeclaration decl) {
    boolean hasBuildMethod = hasMethod(decl, "build");

    boolean hasFluentSetters =
        decl.getMethods().stream()
            .anyMatch(
                m -> {
                  String returnType = m.getTypeAsString();
                  return returnType.equals(decl.getNameAsString())
                      || m.getBody()
                          .map(body -> body.toString().contains("return this"))
                          .orElse(false);
                });

    return hasBuildMethod && hasFluentSetters;
  }

  /**
   * Gets all method declarations from a class.
   *
   * @param decl Class declaration
   * @return List of method declarations
   */
  public static List<MethodDeclaration> getMethods(ClassOrInterfaceDeclaration decl) {
    return decl.getMethods();
  }

  /**
   * Gets method names from a class.
   *
   * @param decl Class declaration
   * @return List of method names
   */
  public static List<String> getMethodNames(ClassOrInterfaceDeclaration decl) {
    return decl.getMethods().stream()
        .map(MethodDeclaration::getNameAsString)
        .collect(Collectors.toList());
  }

  /**
   * Checks if a class has an annotation.
   *
   * @param decl Class declaration
   * @param annotationName Annotation name (simple name without @)
   * @return true if the class has the annotation
   */
  public static boolean hasAnnotation(ClassOrInterfaceDeclaration decl, String annotationName) {
    return decl.getAnnotationByName(annotationName).isPresent();
  }

  /**
   * Gets the package name from a compilation unit.
   *
   * @param cu Compilation unit
   * @return Optional containing the package name, or empty if no package
   */
  public static Optional<String> getPackageName(CompilationUnit cu) {
    return cu.getPackageDeclaration().map(pd -> pd.getNameAsString());
  }

  /**
   * Gets the primary type (class/interface) declaration from a compilation unit.
   *
   * @param cu Compilation unit
   * @return Optional containing the primary type declaration
   */
  public static Optional<ClassOrInterfaceDeclaration> getPrimaryType(CompilationUnit cu) {
    return cu.getPrimaryType()
        .filter(type -> type instanceof ClassOrInterfaceDeclaration)
        .map(type -> (ClassOrInterfaceDeclaration) type);
  }
}
