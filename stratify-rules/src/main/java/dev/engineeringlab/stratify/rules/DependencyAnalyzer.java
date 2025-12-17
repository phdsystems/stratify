package dev.engineeringlab.stratify.rules;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;

/**
 * Analyzes class dependencies using reflection.
 *
 * <p>Extracts dependencies from:
 *
 * <ul>
 *   <li>Superclass
 *   <li>Implemented interfaces
 *   <li>Field types
 *   <li>Method parameter and return types
 *   <li>Constructor parameter types
 *   <li>Annotation types
 * </ul>
 */
public final class DependencyAnalyzer {

  private DependencyAnalyzer() {}

  /**
   * Gets all classes that the given class depends on.
   *
   * @param clazz the class to analyze
   * @return set of dependency classes
   */
  public static Set<Class<?>> getDependencies(Class<?> clazz) {
    Set<Class<?>> dependencies = new HashSet<>();

    // Superclass
    Class<?> superclass = clazz.getSuperclass();
    if (superclass != null && superclass != Object.class) {
      dependencies.add(superclass);
    }

    // Interfaces
    for (Class<?> iface : clazz.getInterfaces()) {
      dependencies.add(iface);
    }

    // Fields
    for (Field field : getDeclaredFieldsSafely(clazz)) {
      addTypeAndGenerics(field.getType(), field.getGenericType(), dependencies);
    }

    // Methods
    for (Method method : getDeclaredMethodsSafely(clazz)) {
      // Return type
      addTypeAndGenerics(method.getReturnType(), method.getGenericReturnType(), dependencies);

      // Parameter types
      for (Parameter param : method.getParameters()) {
        addTypeAndGenerics(param.getType(), param.getParameterizedType(), dependencies);
      }

      // Exception types
      for (Class<?> exception : method.getExceptionTypes()) {
        dependencies.add(exception);
      }
    }

    // Constructors
    for (Constructor<?> constructor : getDeclaredConstructorsSafely(clazz)) {
      for (Parameter param : constructor.getParameters()) {
        addTypeAndGenerics(param.getType(), param.getParameterizedType(), dependencies);
      }
    }

    // Annotations on class
    for (Annotation annotation : getAnnotationsSafely(clazz)) {
      dependencies.add(annotation.annotationType());
    }

    // Remove self-reference and primitives
    dependencies.remove(clazz);
    dependencies.removeIf(Class::isPrimitive);

    return dependencies;
  }

  /**
   * Gets the package names that the given class depends on.
   *
   * @param clazz the class to analyze
   * @return set of package names
   */
  public static Set<String> getDependencyPackages(Class<?> clazz) {
    Set<String> packages = new HashSet<>();
    for (Class<?> dep : getDependencies(clazz)) {
      Package pkg = dep.getPackage();
      if (pkg != null) {
        packages.add(pkg.getName());
      }
    }
    return packages;
  }

  /**
   * Checks if a class depends on any class in the specified packages.
   *
   * @param clazz the class to check
   * @param packagePatterns package patterns (supports ".." for subpackages)
   * @return true if the class depends on any of the packages
   */
  public static boolean dependsOnPackages(Class<?> clazz, String... packagePatterns) {
    Set<String> depPackages = getDependencyPackages(clazz);

    for (String depPkg : depPackages) {
      for (String pattern : packagePatterns) {
        if (matchesPackagePattern(depPkg, pattern)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Gets the classes that this class depends on which match the given package patterns.
   *
   * @param clazz the class to check
   * @param packagePatterns package patterns
   * @return set of matching dependency classes
   */
  public static Set<Class<?>> getDependenciesInPackages(Class<?> clazz, String... packagePatterns) {
    Set<Class<?>> matching = new HashSet<>();

    for (Class<?> dep : getDependencies(clazz)) {
      Package pkg = dep.getPackage();
      if (pkg == null) continue;

      String pkgName = pkg.getName();
      for (String pattern : packagePatterns) {
        if (matchesPackagePattern(pkgName, pattern)) {
          matching.add(dep);
          break;
        }
      }
    }
    return matching;
  }

  /**
   * Checks if a package name matches a pattern.
   *
   * <p>Pattern supports ".." for matching subpackages. Example: "com.example..spi.." matches
   * "com.example.foo.spi.bar"
   */
  static boolean matchesPackagePattern(String packageName, String pattern) {
    // Convert pattern to regex
    String regex = pattern.replace(".", "\\.").replace("\\.\\.", "(?:\\.[^.]+)*");

    // Handle trailing .. (matches subpackages)
    if (pattern.endsWith("..")) {
      regex = regex.substring(0, regex.length() - "(?:\\.[^.]+)*".length()) + "(?:\\..+)?";
    }

    return packageName.matches("^" + regex + "$");
  }

  private static void addTypeAndGenerics(
      Class<?> rawType, Type genericType, Set<Class<?>> dependencies) {
    dependencies.add(rawType);

    // Extract generic type parameters
    if (genericType instanceof ParameterizedType paramType) {
      for (Type arg : paramType.getActualTypeArguments()) {
        if (arg instanceof Class<?> argClass) {
          dependencies.add(argClass);
        } else if (arg instanceof ParameterizedType nestedParam) {
          if (nestedParam.getRawType() instanceof Class<?> rawClass) {
            dependencies.add(rawClass);
          }
        }
      }
    }
  }

  private static Field[] getDeclaredFieldsSafely(Class<?> clazz) {
    try {
      return clazz.getDeclaredFields();
    } catch (NoClassDefFoundError | SecurityException e) {
      return new Field[0];
    }
  }

  private static Method[] getDeclaredMethodsSafely(Class<?> clazz) {
    try {
      return clazz.getDeclaredMethods();
    } catch (NoClassDefFoundError | SecurityException e) {
      return new Method[0];
    }
  }

  private static Constructor<?>[] getDeclaredConstructorsSafely(Class<?> clazz) {
    try {
      return clazz.getDeclaredConstructors();
    } catch (NoClassDefFoundError | SecurityException e) {
      return new Constructor[0];
    }
  }

  private static Annotation[] getAnnotationsSafely(Class<?> clazz) {
    try {
      return clazz.getAnnotations();
    } catch (NoClassDefFoundError | SecurityException e) {
      return new Annotation[0];
    }
  }
}
