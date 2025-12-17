package dev.engineeringlab.stratify.structure.remediation.fixer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.util.JavaParserUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Validates that Fixer implementations do not return null from the fix() method.
 *
 * <p>FX-002: All classes implementing the Fixer interface or extending AbstractStructureFixer must
 * return a proper FixResult from their fix() method, never null. This ensures:
 *
 * <ul>
 *   <li>Consistent error handling and result tracking
 *   <li>Proper status reporting (fixed, skipped, failed)
 *   <li>Prevention of NullPointerExceptions in the fix execution pipeline
 *   <li>Clear communication of fix outcomes to callers
 * </ul>
 *
 * <p>Valid return values from fix() method:
 *
 * <ul>
 *   <li>{@code FixResult.success(violation, files, description)} - when fix succeeds
 *   <li>{@code FixResult.failed(violation, errorMessage)} - when fix fails
 *   <li>{@code FixResult.skipped(violation, reason)} - when fix is skipped
 * </ul>
 */
public class FX002FixerNullReturn {

  private static final String RULE_ID = "FX-002";
  private static final String CATEGORY = "FixerDesign";
  private static final String FIXER_INTERFACE = "Fixer";
  private static final String ABSTRACT_STRUCTURE_FIXER = "AbstractStructureFixer";
  private static final String FIX_METHOD_NAME = "fix";

  /**
   * Validates that all Fixer implementations in the module do not return null from fix() method.
   *
   * @param moduleInfo Module information
   * @return List of violations (empty if compliant)
   */
  public List<StructureViolation> validate(ModuleInfo moduleInfo) {
    List<StructureViolation> violations = new ArrayList<>();

    // Skip if module has no submodules (likely a parent/aggregator)
    if (!moduleInfo.hasAnySubmodules()) {
      return violations;
    }

    // Scan all source directories in the module
    List<Path> sourcePaths = findSourcePaths(moduleInfo);
    if (sourcePaths.isEmpty()) {
      return violations;
    }

    // Check each source path for Fixer implementations
    for (Path sourcePath : sourcePaths) {
      violations.addAll(scanSourcePath(sourcePath));
    }

    return violations;
  }

  /** Finds all source paths in the module (including submodules). */
  private List<Path> findSourcePaths(ModuleInfo moduleInfo) {
    List<Path> sourcePaths = new ArrayList<>();
    Path basePath = moduleInfo.path();
    String baseName = moduleInfo.baseName();

    // Add main module source path
    Path mainSourcePath = basePath.resolve("src/main/java");
    if (Files.exists(mainSourcePath)) {
      sourcePaths.add(mainSourcePath);
    }

    // Add submodule source paths based on boolean flags
    if (moduleInfo.hasApi()) {
      Path apiSourcePath = basePath.resolve(baseName + "-api").resolve("src/main/java");
      if (Files.exists(apiSourcePath)) {
        sourcePaths.add(apiSourcePath);
      }
    }

    if (moduleInfo.hasCore()) {
      Path coreSourcePath = basePath.resolve(baseName + "-core").resolve("src/main/java");
      if (Files.exists(coreSourcePath)) {
        sourcePaths.add(coreSourcePath);
      }
    }

    if (moduleInfo.hasSpi()) {
      Path spiSourcePath = basePath.resolve(baseName + "-spi").resolve("src/main/java");
      if (Files.exists(spiSourcePath)) {
        sourcePaths.add(spiSourcePath);
      }
    }

    if (moduleInfo.hasFacade()) {
      Path facadeSourcePath = basePath.resolve(baseName + "-facade").resolve("src/main/java");
      if (Files.exists(facadeSourcePath)) {
        sourcePaths.add(facadeSourcePath);
      }
    }

    return sourcePaths;
  }

  /** Scans a source path for Fixer implementations with null returns in fix() method. */
  private List<StructureViolation> scanSourcePath(Path sourcePath) {
    List<StructureViolation> violations = new ArrayList<>();

    try (Stream<Path> files = Files.walk(sourcePath)) {
      files
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.toString().contains("package-info.java"))
          .forEach(
              javaFile -> {
                violations.addAll(analyzeJavaFile(javaFile));
              });
    } catch (IOException e) {
      // Skip if can't read files
    }

    return violations;
  }

  /** Analyzes a single Java file for Fixer implementations with null returns. */
  private List<StructureViolation> analyzeJavaFile(Path javaFile) {
    List<StructureViolation> violations = new ArrayList<>();

    Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(javaFile);
    if (cuOpt.isEmpty()) {
      return violations; // Skip files that can't be parsed
    }

    CompilationUnit cu = cuOpt.get();

    // Find all class declarations
    cu.findAll(ClassOrInterfaceDeclaration.class).stream()
        .filter(decl -> !decl.isInterface()) // Only check concrete classes
        .forEach(
            classDecl -> {
              // Check if this class implements Fixer or extends AbstractStructureFixer
              boolean isFixer =
                  implementsFixerInterface(classDecl) || extendsAbstractStructureFixer(classDecl);

              if (isFixer) {
                // Find the fix() method
                Optional<MethodDeclaration> fixMethod =
                    classDecl.getMethods().stream()
                        .filter(m -> m.getNameAsString().equals(FIX_METHOD_NAME))
                        .filter(
                            m ->
                                m.getParameters().size()
                                    == 2) // fix(StructureViolation, FixerContext)
                        .findFirst();

                if (fixMethod.isPresent()) {
                  // Check if the fix() method returns null
                  if (methodReturnsNull(fixMethod.get())) {
                    violations.add(
                        createViolation(
                            javaFile,
                            classDecl.getNameAsString(),
                            fixMethod.get().getBegin().map(pos -> pos.line).orElse(-1)));
                  }
                }
              }
            });

    return violations;
  }

  /** Checks if a class implements the Fixer interface. */
  private boolean implementsFixerInterface(ClassOrInterfaceDeclaration classDecl) {
    return classDecl.getImplementedTypes().stream()
        .anyMatch(type -> type.getNameAsString().equals(FIXER_INTERFACE));
  }

  /** Checks if a class extends AbstractStructureFixer. */
  private boolean extendsAbstractStructureFixer(ClassOrInterfaceDeclaration classDecl) {
    return classDecl.getExtendedTypes().stream()
        .anyMatch(type -> type.getNameAsString().equals(ABSTRACT_STRUCTURE_FIXER));
  }

  /** Checks if a method returns null in any of its return statements. */
  private boolean methodReturnsNull(MethodDeclaration method) {
    // Find all return statements in the method
    List<ReturnStmt> returnStatements = method.findAll(ReturnStmt.class);

    // Check if any return statement returns null
    return returnStatements.stream()
        .filter(ret -> ret.getExpression().isPresent())
        .anyMatch(ret -> ret.getExpression().get() instanceof NullLiteralExpr);
  }

  /** Creates a violation for a fix() method that returns null. */
  private StructureViolation createViolation(Path javaFile, String className, int lineNumber) {
    Path locationPath =
        lineNumber > 0
            ? javaFile // Path doesn't support line numbers, use file path
            : javaFile;

    return StructureViolation.builder()
        .ruleId(RULE_ID)
        .ruleCategory(CATEGORY)
        .message("Fixer fix() method must not return null")
        .location(locationPath)
        .found(className + ".fix() returns null" + (lineNumber > 0 ? " at line " + lineNumber : ""))
        .expected(className + ".fix() should return FixResult")
        .suggestedFix(
            "Replace 'return null;' with an appropriate FixResult factory method:\n\n"
                + "For successful fixes:\n"
                + "  return FixResult.success(violation, modifiedFiles, \"Description of fix\");\n\n"
                + "For failed fixes:\n"
                + "  return FixResult.failed(violation, \"Error message explaining why it failed\");\n\n"
                + "For skipped fixes:\n"
                + "  return FixResult.skipped(violation, \"Reason for skipping\");\n\n"
                + "Choose the appropriate method based on the actual outcome of the fix attempt.")
        .reference("remediation-design.md § FX-002")
        .build();
  }
}
