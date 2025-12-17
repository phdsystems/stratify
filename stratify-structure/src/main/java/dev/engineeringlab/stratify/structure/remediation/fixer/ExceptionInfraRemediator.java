package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.util.FileGenerator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Remediator for Exception Infrastructure violations (EI-001, EI-002, EI-003).
 *
 * <p>Creates exception package with BaseException and ErrorCode classes.
 */
public class ExceptionInfraRemediator extends AbstractStructureFixer {

  private static final int PRIORITY = 95;

  private final FileGenerator fileGenerator = new FileGenerator();

  public ExceptionInfraRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ExceptionInfraRemediator";
  }

  @Override
  public String[] getAllSupportedRules() {
    return new String[] {"EI-001", "EI-002", "EI-003"};
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    try {
      // Find the actual base package by scanning existing source files
      Path sourceRoot = findSourceRoot(context.moduleRoot());
      if (sourceRoot == null) {
        return FixResult.failed(violation, "Could not find src/main/java directory");
      }

      String basePackage = detectBasePackageFromSources(sourceRoot);
      if (basePackage == null) {
        return FixResult.failed(
            violation, "Could not detect base package from existing source files");
      }

      // Convert package to directory path
      Path basePackageDir = sourceRoot.resolve(basePackage.replace(".", "/"));
      Path exceptionDir = basePackageDir.resolve("exception");

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      String packageName = basePackage + ".exception";
      String moduleName = extractModuleName(context.moduleRoot());

      // EI-001: Create exception package
      if (!Files.exists(exceptionDir)) {
        Files.createDirectories(exceptionDir);
        context.log("Created exception package: " + exceptionDir);
      }

      // EI-002: Create BaseException if missing
      String exceptionClassName = capitalize(moduleName) + "Exception";
      Path exceptionFile = exceptionDir.resolve(exceptionClassName + ".java");
      if (!Files.exists(exceptionFile)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + exceptionFile);
        } else {
          fileGenerator.generateBaseException(exceptionDir, packageName, moduleName);
          modifiedFiles.add(exceptionFile);
          context.log("Created: " + exceptionFile);
        }
      }

      // EI-003: Create ErrorCode if missing
      Path errorCodeFile = exceptionDir.resolve("ErrorCode.java");
      if (!Files.exists(errorCodeFile)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + errorCodeFile);
        } else {
          fileGenerator.generateErrorCode(exceptionDir, packageName);
          modifiedFiles.add(errorCodeFile);
          context.log("Created: " + errorCodeFile);
        }
      }

      // Generate package-info.java
      Path packageInfoFile = exceptionDir.resolve("package-info.java");
      if (!Files.exists(packageInfoFile)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + packageInfoFile);
        } else {
          fileGenerator.generatePackageInfo(
              exceptionDir, packageName, "Exception infrastructure for " + moduleName + " module.");
          modifiedFiles.add(packageInfoFile);
          context.log("Created: " + packageInfoFile);
        }
      }

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation, modifiedFiles, "Would create exception infrastructure", diffs);
      }

      return FixResult.success(
          violation,
          modifiedFiles,
          "Created exception infrastructure with " + modifiedFiles.size() + " files");

    } catch (IOException e) {
      return FixResult.failed(
          violation, "Failed to create exception infrastructure: " + e.getMessage());
    }
  }

  private Path findSourceRoot(Path moduleRoot) {
    Path sourceRoot = moduleRoot.resolve("src/main/java");
    if (Files.exists(sourceRoot)) {
      return sourceRoot;
    }
    return null;
  }

  private String detectBasePackageFromSources(Path sourceRoot) {
    // Find first .java file and extract its package
    try (var stream = Files.walk(sourceRoot)) {
      return stream
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.getFileName().toString().equals("package-info.java"))
          .findFirst()
          .map(this::extractPackageFromFile)
          .map(this::getBasePackage)
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private String extractPackageFromFile(Path javaFile) {
    try {
      return Files.readAllLines(javaFile).stream()
          .filter(line -> line.startsWith("package "))
          .findFirst()
          .map(line -> line.substring(8).replace(";", "").trim())
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }

  private String getBasePackage(String packageName) {
    // For api/core/model/util subpackages, go up to the base
    // e.g., dev.engineeringlab.architecture.remediation.api ->
    // dev.engineeringlab.architecture.remediation
    // e.g., dev.engineeringlab.architecture.remediation.util ->
    // dev.engineeringlab.architecture.remediation
    // Recursively strip common subpackages
    String result = packageName;
    boolean stripped = true;
    while (stripped && result.contains(".")) {
      stripped = false;
      String[] parts = result.split("\\.");
      if (parts.length >= 2) {
        String lastPart = parts[parts.length - 1];
        // If last part is a common subpackage, remove it
        if (lastPart.equals("api")
            || lastPart.equals("core")
            || lastPart.equals("model")
            || lastPart.equals("config")
            || lastPart.equals("spi")
            || lastPart.equals("util")
            || lastPart.equals("impl")
            || lastPart.equals("internal")
            || lastPart.equals("exception")) {
          result = result.substring(0, result.lastIndexOf('.'));
          stripped = true;
        }
      }
    }
    return result;
  }

  // extractModuleName() inherited from AbstractStructureFixer

  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : str.toCharArray()) {
      if (c == '-' || c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
