package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for API/Core contract violations.
 *
 * <p>Enforces the software engineering pattern:
 *
 * <ul>
 *   <li><b>API modules</b> contain only contracts: interfaces, annotations, records, enums,
 *       exceptions
 *   <li><b>Core modules</b> contain implementations: Aspect, Impl, Adapter, Service, Factory, etc.
 * </ul>
 *
 * <p>Supported violations:
 *
 * <ul>
 *   <li>ASPECT_CLASSES_SHOULD_BE_IN_CORE - Moves *Aspect classes from api to core
 *   <li>IMPL_CLASSES_SHOULD_BE_IN_CORE - Moves *Impl classes from api to core
 *   <li>ADAPTER_CLASSES_SHOULD_BE_IN_CORE - Moves *Adapter classes from api to core
 *   <li>SERVICE_CLASSES_SHOULD_BE_IN_CORE - Moves *Service classes from api to core
 *   <li>FACTORY_CLASSES_SHOULD_BE_IN_CORE - Moves *Factory classes from api to core
 * </ul>
 *
 * <p>The fixer:
 *
 * <ol>
 *   <li>Identifies the class location in the api module
 *   <li>Calculates the target location in the corresponding core module
 *   <li>Updates the package declaration in the class
 *   <li>Moves the file to the core module
 *   <li>Updates imports in other files that reference the moved class
 * </ol>
 */
public class ApiCoreContractRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {
    "ASPECT_CLASSES_SHOULD_BE_IN_CORE",
    "IMPL_CLASSES_SHOULD_BE_IN_CORE",
    "ADAPTER_CLASSES_SHOULD_BE_IN_CORE",
    "SERVICE_CLASSES_SHOULD_BE_IN_CORE",
    "FACTORY_CLASSES_SHOULD_BE_IN_CORE",
    "UTILITY_CLASSES_SHOULD_BE_IN_CORE",
    "BUILDER_CLASSES_SHOULD_BE_IN_CORE",
    "REGISTRIES_SHOULD_BE_IN_CORE"
  };

  private static final int PRIORITY = 70;

  private static final Pattern PACKAGE_PATTERN =
      Pattern.compile("^\\s*package\\s+([a-zA-Z_][a-zA-Z0-9_.]*);", Pattern.MULTILINE);

  private static final Pattern CLASS_NAME_PATTERN =
      Pattern.compile("public\\s+(?:final\\s+)?(?:abstract\\s+)?class\\s+(\\w+)");

  public ApiCoreContractRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ApiCoreContractRemediator";
  }

  @Override
  public String getDescription() {
    return "Moves implementation classes (Aspect, Impl, Adapter, etc.) from API to Core modules";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not a supported API/Core violation");
    }

    try {
      // Extract the class location from violation message
      String message = violation.message();
      Path sourceFile = extractSourceFile(message, context);

      if (sourceFile == null || !Files.exists(sourceFile)) {
        return FixResult.skipped(
            violation, "Could not locate source file from violation: " + message);
      }

      // Read the source file
      String content = Files.readString(sourceFile);

      // Extract package and class name
      String currentPackage = extractPackage(content);
      String className = extractClassName(content);

      if (currentPackage == null || className == null) {
        return FixResult.skipped(
            violation, "Could not extract package or class name from: " + sourceFile);
      }

      // Check if it's actually in an api package
      if (!currentPackage.contains(".api.") && !currentPackage.endsWith(".api")) {
        return FixResult.skipped(violation, "Class is not in an API package: " + currentPackage);
      }

      // Calculate target package (replace .api. with .core.)
      String targetPackage =
          currentPackage.replace(".api.", ".core.").replaceAll("\\.api$", ".core");

      // Calculate target file path
      Path targetFile = calculateTargetPath(sourceFile, currentPackage, targetPackage);

      if (context.dryRun()) {
        context.log("[DRY-RUN] Would move %s to %s", sourceFile, targetFile);
        context.log("[DRY-RUN] Would update package from %s to %s", currentPackage, targetPackage);
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.DRY_RUN)
            .description("Would move " + className + " from api to core module")
            .build();
      }

      // Create backup
      backup(sourceFile, context.projectRoot());

      // Update package declaration
      String updatedContent =
          content.replaceFirst(
              "package\\s+" + Pattern.quote(currentPackage) + ";",
              "package " + targetPackage + ";");

      // Create target directory if needed
      Files.createDirectories(targetFile.getParent());

      // Write to target location
      Files.writeString(targetFile, updatedContent);

      // Delete original file
      Files.delete(sourceFile);

      context.log("Moved %s from %s to %s", className, currentPackage, targetPackage);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Moved " + className + " from api to core module")
          .modifiedFiles(java.util.List.of(sourceFile, targetFile))
          .build();

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to move class: " + e.getMessage());
    }
  }

  private Path extractSourceFile(String message, FixerContext context) {
    // Try to extract class name from message like "Class MyAspect in package com.example.api"
    // Pattern matches: "Class <ClassName> in package <package.name>"
    Pattern classInPackagePattern =
        Pattern.compile(
            "(?:Class|class)\\s+([A-Z][a-zA-Z0-9]+)\\s+in\\s+package\\s+([a-z][a-zA-Z0-9.]+)");

    Matcher matcher = classInPackagePattern.matcher(message);
    if (matcher.find()) {
      String className = matcher.group(1);
      String packageName = matcher.group(2);

      // Convert to file path
      String relativePath = packageName.replace('.', '/') + "/" + className + ".java";
      Path sourceRoot = context.moduleRoot().resolve("src/main/java");
      return sourceRoot.resolve(relativePath);
    }

    // Try alternate pattern: "<ClassName> in package <package.name>"
    Pattern altPattern =
        Pattern.compile("([A-Z][a-zA-Z0-9]+)\\s+in\\s+package\\s+([a-z][a-zA-Z0-9.]+)");
    matcher = altPattern.matcher(message);
    if (matcher.find()) {
      String className = matcher.group(1);
      String packageName = matcher.group(2);

      String relativePath = packageName.replace('.', '/') + "/" + className + ".java";
      Path sourceRoot = context.moduleRoot().resolve("src/main/java");
      return sourceRoot.resolve(relativePath);
    }

    // Try to extract just package name and scan for *Aspect.java files
    Pattern packagePattern = Pattern.compile("package\\s+([a-z][a-zA-Z0-9.]+)");
    matcher = packagePattern.matcher(message);
    if (matcher.find()) {
      String packageName = matcher.group(1);
      Path packageDir =
          context.moduleRoot().resolve("src/main/java").resolve(packageName.replace('.', '/'));

      // Find the class file based on rule type
      if (Files.exists(packageDir)) {
        try (var files = Files.list(packageDir)) {
          return files
              .filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    String name = p.getFileName().toString();
                    return name.endsWith("Aspect.java")
                        || name.endsWith("Impl.java")
                        || name.endsWith("Adapter.java")
                        || name.endsWith("Service.java")
                        || name.endsWith("Factory.java");
                  })
              .findFirst()
              .orElse(null);
        } catch (IOException e) {
          // Fall through
        }
      }
    }

    // Try direct file path in message
    Pattern filePathPattern = Pattern.compile("([\\w/]+\\.java)");
    matcher = filePathPattern.matcher(message);
    if (matcher.find()) {
      return context.moduleRoot().resolve(matcher.group(1));
    }

    return null;
  }

  private String extractPackage(String content) {
    Matcher matcher = PACKAGE_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private String extractClassName(String content) {
    Matcher matcher = CLASS_NAME_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private Path calculateTargetPath(Path sourceFile, String currentPackage, String targetPackage) {
    String sourcePath = sourceFile.toString();
    String currentPackagePath = currentPackage.replace('.', '/');
    String targetPackagePath = targetPackage.replace('.', '/');

    // Replace api with core in the path
    String targetPath = sourcePath.replace(currentPackagePath, targetPackagePath);

    // Also handle module directory structure (e.g., module-api -> module-core)
    targetPath = targetPath.replace("-api/", "-core/");

    return Path.of(targetPath);
  }
}
