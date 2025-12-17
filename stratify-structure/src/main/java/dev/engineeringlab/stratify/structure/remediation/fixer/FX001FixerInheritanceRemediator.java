package dev.engineeringlab.stratify.structure.remediation.fixer;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Remediator for FX-001: Fixer classes must extend AbstractStructureFixer.
 *
 * <p>This remediator automatically fixes Fixer implementations that directly implement the Fixer
 * interface instead of extending AbstractStructureFixer.
 *
 * <p>The fix performs the following transformations:
 *
 * <ul>
 *   <li>Changes {@code implements Fixer} to {@code extends AbstractStructureFixer}
 *   <li>Removes redundant {@code implements Fixer} declarations
 *   <li>Adds import for AbstractStructureFixer if not present
 *   <li>Removes Fixer import if it becomes unused
 * </ul>
 */
public class FX001FixerInheritanceRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"FX-001"};
  private static final int PRIORITY = 95; // High priority - fundamental structural fix

  private static final String FIXER_INTERFACE = "Fixer";
  private static final String ABSTRACT_STRUCTURE_FIXER = "AbstractStructureFixer";
  private static final String FIXER_PACKAGE =
      "dev.engineeringlab.stratify.structure.remediation.api";
  private static final String ABSTRACT_STRUCTURE_FIXER_PACKAGE =
      "dev.engineeringlab.stratify.structure.remediation.core";

  public FX001FixerInheritanceRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "FX001FixerInheritanceRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes Fixer classes to extend AbstractStructureFixer instead of implementing Fixer directly (FX-001)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!"FX-001".equals(violation.ruleId())) {
      return FixResult.skipped(violation, "Not an FX-001 violation");
    }

    FixResult result = performFix(violation, context);

    // Clean up backups on success
    if (result.status() == FixStatus.FIXED && result.modifiedFiles() != null) {
      cleanupBackupsOnSuccess(result.modifiedFiles(), context.projectRoot());
    }

    return result;
  }

  /** Performs the actual fix logic. */
  private FixResult performFix(StructureViolation violation, FixerContext context) {
    try {
      Path javaFile = resolveJavaFile(violation, context);
      if (javaFile == null || !Files.exists(javaFile)) {
        return FixResult.skipped(violation, "Cannot resolve Java file from violation location");
      }

      // Read the file content
      String originalContent = readFile(javaFile);

      // Parse with JavaParser
      JavaParser parser = new JavaParser();
      CompilationUnit cu =
          parser
              .parse(originalContent)
              .getResult()
              .orElseThrow(
                  () -> new IllegalStateException("Failed to parse Java file: " + javaFile));

      // Track if we made any changes
      boolean modified = false;

      // Find the class declaration
      Optional<ClassOrInterfaceDeclaration> classDecl =
          cu.findFirst(ClassOrInterfaceDeclaration.class);
      if (classDecl.isEmpty()) {
        return FixResult.skipped(violation, "No class declaration found in file");
      }

      ClassOrInterfaceDeclaration clazz = classDecl.get();

      // Check if class already extends AbstractStructureFixer
      if (extendsAbstractStructureFixer(clazz)) {
        return FixResult.skipped(violation, "Class already extends AbstractStructureFixer");
      }

      // Check if class implements Fixer
      if (!implementsFixer(clazz)) {
        return FixResult.skipped(violation, "Class does not implement Fixer interface");
      }

      List<String> diffs = new ArrayList<>();

      if (context.dryRun()) {
        diffs.add("- Would change 'implements Fixer' to 'extends AbstractStructureFixer'");
        diffs.add("- Would add import for AbstractStructureFixer");
        diffs.add("- Would remove Fixer import if unused");

        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would fix Fixer inheritance to extend AbstractStructureFixer")
            .diffs(diffs)
            .build();
      }

      // Backup the file before making changes
      backup(javaFile, context.projectRoot());

      // Transform: implements Fixer -> extends AbstractStructureFixer
      if (removeFixerImplementation(clazz)) {
        addAbstractStructureFixerExtension(clazz);
        modified = true;
        diffs.add("Changed 'implements Fixer' to 'extends AbstractStructureFixer'");
      }

      // Add import for AbstractStructureFixer if not present
      if (addAbstractStructureFixerImport(cu)) {
        modified = true;
        diffs.add("Added import for AbstractStructureFixer");
      }

      // Remove Fixer import if it's no longer used
      if (removeFixerImportIfUnused(cu, clazz)) {
        modified = true;
        diffs.add("Removed unused Fixer import");
      }

      if (!modified) {
        return FixResult.skipped(violation, "No changes needed");
      }

      // Write the modified content back
      String modifiedContent = cu.toString();
      writeFile(javaFile, modifiedContent);

      context.log("Fixed Fixer inheritance in: %s", javaFile);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Fixed Fixer class to extend AbstractStructureFixer")
          .modifiedFiles(List.of(javaFile))
          .diffs(diffs)
          .build();

    } catch (Exception e) {
      context.log("Error fixing FX-001: %s", e.getMessage());
      return FixResult.failed(violation, "Failed to fix: " + e.getMessage());
    }
  }

  /** Resolves the Java file path from the violation location. */
  private Path resolveJavaFile(StructureViolation violation, FixerContext context) {
    Path location = violation.location();
    if (location == null) {
      return null;
    }

    // Make absolute if relative
    if (!location.isAbsolute()) {
      location = context.projectRoot().resolve(location);
    }

    // If location is already a .java file, use it
    if (Files.isRegularFile(location) && location.toString().endsWith(".java")) {
      return location;
    }

    // If location is a directory, try to find the Fixer class
    String found = violation.found();
    if (found != null && found.contains(".java")) {
      String[] parts = found.split("\\s+");
      for (String part : parts) {
        if (part.endsWith(".java")) {
          Path potentialFile = location.resolve(part);
          if (Files.exists(potentialFile)) {
            return potentialFile;
          }
        }
      }
    }

    return location;
  }

  /** Checks if the class extends AbstractStructureFixer. */
  private boolean extendsAbstractStructureFixer(ClassOrInterfaceDeclaration clazz) {
    return clazz.getExtendedTypes().stream()
        .anyMatch(t -> t.getNameAsString().equals(ABSTRACT_STRUCTURE_FIXER));
  }

  /** Checks if the class implements Fixer. */
  private boolean implementsFixer(ClassOrInterfaceDeclaration clazz) {
    return clazz.getImplementedTypes().stream()
        .anyMatch(t -> t.getNameAsString().equals(FIXER_INTERFACE));
  }

  /**
   * Removes the Fixer interface from the implemented types. Returns true if the interface was found
   * and removed.
   */
  private boolean removeFixerImplementation(ClassOrInterfaceDeclaration clazz) {
    NodeList<ClassOrInterfaceType> implementedTypes = clazz.getImplementedTypes();
    Optional<ClassOrInterfaceType> fixerType =
        implementedTypes.stream()
            .filter(t -> t.getNameAsString().equals(FIXER_INTERFACE))
            .findFirst();

    if (fixerType.isPresent()) {
      implementedTypes.remove(fixerType.get());
      return true;
    }
    return false;
  }

  /** Adds AbstractStructureFixer as the extended type. */
  private void addAbstractStructureFixerExtension(ClassOrInterfaceDeclaration clazz) {
    ClassOrInterfaceType abstractStructureFixerType =
        new ClassOrInterfaceType(null, ABSTRACT_STRUCTURE_FIXER);
    clazz.getExtendedTypes().add(abstractStructureFixerType);
  }

  /**
   * Adds the import for AbstractStructureFixer if not already present. Returns true if the import
   * was added.
   */
  private boolean addAbstractStructureFixerImport(CompilationUnit cu) {
    String importName = ABSTRACT_STRUCTURE_FIXER_PACKAGE + "." + ABSTRACT_STRUCTURE_FIXER;

    // Check if import already exists
    boolean exists =
        cu.getImports().stream().anyMatch(imp -> imp.getNameAsString().equals(importName));

    if (!exists) {
      cu.addImport(importName);
      return true;
    }
    return false;
  }

  /**
   * Removes the Fixer import if it's no longer used in the file. Returns true if the import was
   * removed.
   */
  private boolean removeFixerImportIfUnused(CompilationUnit cu, ClassOrInterfaceDeclaration clazz) {
    String fixerImport = FIXER_PACKAGE + "." + FIXER_INTERFACE;

    // Check if Fixer is still referenced in the class
    boolean stillUsed = isFixerStillReferenced(cu);

    if (!stillUsed) {
      // Find and remove the Fixer import
      Optional<ImportDeclaration> importToRemove =
          cu.getImports().stream()
              .filter(imp -> imp.getNameAsString().equals(fixerImport))
              .findFirst();

      if (importToRemove.isPresent()) {
        cu.getImports().remove(importToRemove.get());
        return true;
      }
    }
    return false;
  }

  /** Checks if the Fixer interface is still referenced anywhere in the compilation unit. */
  private boolean isFixerStillReferenced(CompilationUnit cu) {
    String code = cu.toString();

    // Remove imports section to avoid false positives
    String[] lines = code.split("\n");
    StringBuilder codeWithoutImports = new StringBuilder();
    boolean inImports = false;

    for (String line : lines) {
      if (line.trim().startsWith("import ")) {
        inImports = true;
      } else if (inImports && !line.trim().isEmpty() && !line.trim().startsWith("import ")) {
        inImports = false;
      }

      if (!inImports && !line.trim().startsWith("import ")) {
        codeWithoutImports.append(line).append("\n");
      }
    }

    // Check if "Fixer" (as a word boundary) appears in the code
    // Exclude "AbstractStructureFixer" matches
    String cleanCode = codeWithoutImports.toString();
    cleanCode = cleanCode.replaceAll("AbstractStructureFixer", "");

    return cleanCode.matches("(?s).*\\bFixer\\b.*");
  }
}
