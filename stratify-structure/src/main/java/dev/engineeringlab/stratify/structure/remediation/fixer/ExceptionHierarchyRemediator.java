package dev.engineeringlab.stratify.structure.remediation.fixer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.util.JavaParserUtil;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Remediator for EI-006: Domain exceptions must extend module's BaseException.
 *
 * <p>Updates exception classes to extend the module's base exception class instead of
 * RuntimeException or Exception directly.
 *
 * <p>Uses JavaParser AST for reliable code manipulation (ADR-003).
 */
public class ExceptionHierarchyRemediator extends AbstractStructureFixer {

  private static final int PRIORITY = 90;

  public ExceptionHierarchyRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ExceptionHierarchyRemediator";
  }

  @Override
  public String[] getAllSupportedRules() {
    return new String[] {"EI-006"};
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    try {
      Path exceptionDir = violation.location();

      if (!Files.exists(exceptionDir) || !Files.isDirectory(exceptionDir)) {
        return FixResult.failed(violation, "Exception directory not found: " + exceptionDir);
      }

      // Find the base exception class in this directory
      String baseExceptionName = findBaseExceptionName(exceptionDir);
      if (baseExceptionName == null) {
        return FixResult.failed(
            violation, "No base exception found. Run EI-001/EI-002 fixer first.");
      }

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Find all exception classes that need to be updated
      try (Stream<Path> files = Files.list(exceptionDir)) {
        List<Path> exceptionFiles =
            files
                .filter(f -> f.getFileName().toString().endsWith("Exception.java"))
                .filter(f -> !f.getFileName().toString().equals(baseExceptionName + ".java"))
                .toList();

        for (Path exceptionFile : exceptionFiles) {
          Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(exceptionFile);
          if (cuOpt.isEmpty()) {
            context.log("Could not parse: " + exceptionFile);
            continue;
          }

          CompilationUnit cu = cuOpt.get();
          boolean modified = false;

          // Find exception classes using AST
          for (ClassOrInterfaceDeclaration classDecl :
              cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (classDecl.isInterface()) {
              continue;
            }

            // Check if extends RuntimeException or Exception directly
            Optional<ClassOrInterfaceType> extendedType =
                classDecl.getExtendedTypes().stream()
                    .filter(
                        t ->
                            t.getNameAsString().equals("RuntimeException")
                                || t.getNameAsString().equals("Exception"))
                    .findFirst();

            if (extendedType.isPresent()) {
              // Replace with baseExceptionName using AST
              classDecl.getExtendedTypes().clear();
              classDecl.addExtendedType(new ClassOrInterfaceType(null, baseExceptionName));
              modified = true;
            }
          }

          if (modified) {
            if (context.dryRun()) {
              diffs.add(
                  "Would update "
                      + exceptionFile.getFileName()
                      + " to extend "
                      + baseExceptionName);
            } else {
              backup(exceptionFile, context.projectRoot());
              writeFile(exceptionFile, cu.toString());
              modifiedFiles.add(exceptionFile);
              context.log(
                  "Updated " + exceptionFile.getFileName() + " to extend " + baseExceptionName);
            }
          }
        }
      }

      if (modifiedFiles.isEmpty() && diffs.isEmpty()) {
        return FixResult.skipped(
            violation, "All exception classes already extend " + baseExceptionName);
      }

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation, modifiedFiles, "Would update " + diffs.size() + " exception classes", diffs);
      }

      return FixResult.success(
          violation,
          modifiedFiles,
          "Updated " + modifiedFiles.size() + " exception classes to extend " + baseExceptionName);

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to fix exception hierarchy: " + e.getMessage());
    }
  }

  private String findBaseExceptionName(Path exceptionDir) throws IOException {
    // Look for a class that:
    // 1. Ends with "Exception" or "BaseException"
    // 2. Extends RuntimeException directly
    // 3. Has "Base" in the name OR is the only one extending RuntimeException

    try (Stream<Path> files = Files.list(exceptionDir)) {
      List<Path> candidates =
          files.filter(f -> f.getFileName().toString().endsWith("Exception.java")).toList();

      // First, look for *BaseException
      for (Path file : candidates) {
        String name = file.getFileName().toString().replace(".java", "");
        if (name.contains("Base") && name.endsWith("Exception")) {
          return name;
        }
      }

      // Then look for module-named exception using AST
      for (Path file : candidates) {
        Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(file);
        if (cuOpt.isEmpty()) {
          continue;
        }

        String name = file.getFileName().toString().replace(".java", "");

        // Check if this extends RuntimeException and has ErrorCode field using AST
        for (ClassOrInterfaceDeclaration classDecl :
            cuOpt.get().findAll(ClassOrInterfaceDeclaration.class)) {
          boolean extendsRuntime =
              classDecl.getExtendedTypes().stream()
                  .anyMatch(t -> t.getNameAsString().equals("RuntimeException"));
          boolean hasErrorCode =
              classDecl.getFields().stream()
                  .anyMatch(f -> f.getElementType().asString().contains("ErrorCode"));

          if (extendsRuntime && hasErrorCode) {
            return name;
          }
        }
      }

      // Finally, any exception that extends RuntimeException directly using AST
      for (Path file : candidates) {
        Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(file);
        if (cuOpt.isEmpty()) {
          continue;
        }

        String name = file.getFileName().toString().replace(".java", "");

        for (ClassOrInterfaceDeclaration classDecl :
            cuOpt.get().findAll(ClassOrInterfaceDeclaration.class)) {
          boolean extendsRuntime =
              classDecl.getExtendedTypes().stream()
                  .anyMatch(t -> t.getNameAsString().equals("RuntimeException"));

          if (extendsRuntime) {
            return name;
          }
        }
      }
    }

    return null;
  }
}
