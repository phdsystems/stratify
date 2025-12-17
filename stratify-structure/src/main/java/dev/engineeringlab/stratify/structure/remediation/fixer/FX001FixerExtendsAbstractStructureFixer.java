package dev.engineeringlab.stratify.structure.remediation.fixer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
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
 * Validates that all Fixer implementations extend AbstractStructureFixer.
 *
 * <p>FX-001: All classes implementing the Fixer interface must extend AbstractStructureFixer
 * instead of implementing Fixer directly. This ensures:
 *
 * <ul>
 *   <li>Compile-time verification of fix implementations
 *   <li>Automatic backup and rollback capabilities
 *   <li>Consistent error handling across all fixers
 *   <li>Built-in dry-run support
 *   <li>File I/O utilities for common operations
 * </ul>
 */
public class FX001FixerExtendsAbstractStructureFixer {

  private static final String RULE_ID = "FX-001";
  private static final String CATEGORY = "FixerDesign";
  private static final String FIXER_INTERFACE = "Fixer";
  private static final String ABSTRACT_STRUCTURE_FIXER = "AbstractStructureFixer";

  /**
   * Validates that all Fixer implementations in the module extend AbstractStructureFixer.
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

  /** Scans a source path for Fixer implementations that don't extend AbstractStructureFixer. */
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

  /** Analyzes a single Java file for Fixer implementations. */
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
              // Check if this class implements Fixer
              boolean implementsFixer =
                  classDecl.getImplementedTypes().stream()
                      .anyMatch(type -> type.getNameAsString().equals(FIXER_INTERFACE));

              if (implementsFixer) {
                // Check if it extends AbstractStructureFixer
                boolean extendsAbstractStructureFixer =
                    classDecl.getExtendedTypes().stream()
                        .anyMatch(type -> type.getNameAsString().equals(ABSTRACT_STRUCTURE_FIXER));

                if (!extendsAbstractStructureFixer) {
                  // Violation: implements Fixer but doesn't extend AbstractStructureFixer
                  violations.add(createViolation(javaFile, classDecl.getNameAsString()));
                }
              }
            });

    return violations;
  }

  /** Creates a violation for a Fixer that doesn't extend AbstractStructureFixer. */
  private StructureViolation createViolation(Path javaFile, String className) {
    return StructureViolation.builder()
        .ruleId(RULE_ID)
        .ruleCategory(CATEGORY)
        .message("Fixer must extend AbstractStructureFixer")
        .location(javaFile)
        .found(className + " implements Fixer directly")
        .expected(className + " should extend AbstractStructureFixer")
        .suggestedFix(
            "Change 'implements Fixer' to 'extends AbstractStructureFixer':\n"
                + "  1. Replace 'implements Fixer' with 'extends AbstractStructureFixer'\n"
                + "  2. Add import: dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer\n"
                + "  3. Remove redundant method implementations (if AbstractStructureFixer provides them)\n"
                + "  4. Use AbstractStructureFixer's utility methods (backup, restore, readFile, writeFile, etc.)")
        .reference("remediation-design.md § FX-001")
        .build();
  }
}
