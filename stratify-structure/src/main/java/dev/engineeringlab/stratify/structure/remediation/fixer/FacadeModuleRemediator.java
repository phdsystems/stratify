package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Remediator for MS-003: Missing Facade module.
 *
 * <p>Creates the missing facade module directory with proper structure:
 *
 * <ul>
 *   <li>Creates the facade module directory
 *   <li>Generates a pom.xml with correct parent reference
 *   <li>Creates src/main/java directory structure
 *   <li>Adds the module to parent pom.xml
 * </ul>
 *
 * <p>The facade module aggregates and re-exports API and Core modules, providing a single entry
 * point for consumers.
 */
public class FacadeModuleRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-003"};
  private static final int PRIORITY = 90; // High priority - run early

  public FacadeModuleRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "FacadeModuleRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates missing Facade (MS-003) module";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!"MS-003".equals(ruleId)) {
      return FixResult.skipped(violation, "Not an MS-003 violation");
    }

    Path newModulePath = null;
    Path parentPom = null;

    try {
      // Derive moduleRoot from violation location, not context
      Path moduleRoot = deriveModuleRoot(violation, context);

      // Check if this is a parent module (packaging=pom)
      Path pomPath = moduleRoot.resolve("pom.xml");
      if (Files.exists(pomPath)) {
        String pomContent = readFile(pomPath);
        String packaging = extractXmlValue(pomContent, "packaging", "jar");
        if (!"pom".equals(packaging)) {
          return FixResult.skipped(
              violation,
              "Module is not a parent (packaging="
                  + packaging
                  + "). "
                  + "Only parent modules with packaging=pom can have submodules.");
        }
      } else {
        return FixResult.skipped(violation, "No pom.xml found in module root");
      }

      // Extract the expected facade name from the violation message
      // Message format: "Facade module 'xxx-facade' must exist."
      String newModuleName = extractExpectedFacadeName(violation);
      String moduleName;
      if (newModuleName != null) {
        // Derive module name from facade name (remove -facade suffix)
        moduleName = newModuleName.replaceAll("-facade$", "");
      } else {
        // Fallback to deriving from module root
        moduleName = extractModuleName(moduleRoot);
        newModuleName = moduleName + "-facade";
      }
      newModulePath = moduleRoot.resolve(newModuleName);

      // Check if already exists
      if (Files.exists(newModulePath)) {
        return FixResult.skipped(violation, "Module directory already exists: " + newModuleName);
      }

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Read parent pom to get groupId and version
      parentPom = moduleRoot.resolve("pom.xml");
      String groupId = "dev.engineeringlab";
      String version = "0.2.0-SNAPSHOT";
      String parentArtifactId = moduleName;

      if (Files.exists(parentPom)) {
        String parentContent = readFile(parentPom);
        groupId = extractXmlValue(parentContent, "groupId", groupId);
        version = extractXmlValue(parentContent, "version", version);
        parentArtifactId = extractXmlValue(parentContent, "artifactId", moduleName);
      }

      if (context.dryRun()) {
        diffs.add("+ Would create directory: " + newModuleName);
        diffs.add("+ Would create: " + newModuleName + "/pom.xml");
        diffs.add("+ Would create: " + newModuleName + "/src/main/java/");
        diffs.add("+ Would add module to parent pom.xml");

        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.DRY_RUN)
            .description("Would create Facade module: " + newModuleName)
            .diffs(diffs)
            .build();
      }

      // Create module directory
      Files.createDirectories(newModulePath);
      context.log("Created directory: %s", newModulePath);

      // Create pom.xml
      Path pomFile = newModulePath.resolve("pom.xml");
      String pomContent =
          generateFacadePomXml(groupId, parentArtifactId, newModuleName, version, moduleName);
      writeFile(pomFile, pomContent);
      modifiedFiles.add(pomFile);
      context.log("Created: %s", pomFile);

      // Create src/main/java directory
      // Facade package is the "clean" consumer-facing package without .facade suffix
      String basePackage = groupId + "." + moduleName.replace("-", ".");
      Path srcDir = newModulePath.resolve("src/main/java").resolve(basePackage.replace(".", "/"));
      Files.createDirectories(srcDir);
      context.log("Created: %s", srcDir);

      // Create a package-info.java
      Path packageInfo = srcDir.resolve("package-info.java");
      String packageInfoContent = generatePackageInfo(basePackage, moduleName, "Facade layer");
      writeFile(packageInfo, packageInfoContent);
      modifiedFiles.add(packageInfo);

      // Add module to parent pom.xml
      if (Files.exists(parentPom)) {
        addModuleToParentPom(parentPom, newModuleName, context);
        modifiedFiles.add(parentPom);
      }

      // Cleanup backup of parent pom on success
      cleanupBackupsOnSuccess(List.of(parentPom), context.projectRoot());

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Created Facade module: " + newModuleName)
          .modifiedFiles(modifiedFiles)
          .build();

    } catch (Exception e) {
      // Rollback: restore parent pom and delete created module directory
      if (parentPom != null) {
        rollbackOnFailure(List.of(parentPom), context.projectRoot());
      }
      if (newModulePath != null && Files.exists(newModulePath)) {
        try {
          deleteDirectoryRecursively(newModulePath);
        } catch (IOException ignored) {
        }
      }
      return FixResult.failed(violation, "Failed to create facade module: " + e.getMessage());
    }
  }

  // deleteDirectoryRecursively(), extractModuleName(), extractXmlValue() inherited from
  // AbstractStructureFixer

  private String generateFacadePomXml(
      String groupId,
      String parentArtifactId,
      String artifactId,
      String version,
      String moduleName) {

    String description =
        "Facade module for " + moduleName + " - aggregates and re-exports API and Core";
    String name = toPascalCase(moduleName) + " Facade";

    StringBuilder pom = new StringBuilder();
    pom.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\"\n");
    pom.append("         xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n");
    pom.append("         xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 ");
    pom.append("http://maven.apache.org/xsd/maven-4.0.0.xsd\">\n");
    pom.append("    <modelVersion>4.0.0</modelVersion>\n\n");

    pom.append("    <parent>\n");
    pom.append("        <groupId>").append(groupId).append("</groupId>\n");
    pom.append("        <artifactId>").append(parentArtifactId).append("</artifactId>\n");
    pom.append("        <version>").append(version).append("</version>\n");
    pom.append("    </parent>\n\n");

    pom.append("    <groupId>").append(groupId).append("</groupId>\n");
    pom.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
    pom.append("    <packaging>jar</packaging>\n\n");

    pom.append("    <name>").append(name).append("</name>\n");
    pom.append("    <description>").append(description).append("</description>\n\n");

    // Add dependencies - facade depends on core (which transitively includes api and spi)
    pom.append("    <dependencies>\n");
    String coreArtifactId = moduleName + "-core";
    pom.append("        <dependency>\n");
    pom.append("            <groupId>").append(groupId).append("</groupId>\n");
    pom.append("            <artifactId>").append(coreArtifactId).append("</artifactId>\n");
    pom.append("            <version>${project.version}</version>\n");
    pom.append("        </dependency>\n");
    pom.append("    </dependencies>\n\n");

    pom.append("</project>\n");

    return pom.toString();
  }

  /**
   * Extracts the expected facade module name from the violation message. Message format: "Facade
   * module 'xxx-facade' must exist."
   *
   * @param violation the violation
   * @return the expected facade name, or null if not found in message
   */
  private String extractExpectedFacadeName(StructureViolation violation) {
    String message = violation.message();
    if (message == null) {
      return null;
    }
    // Pattern: Facade module 'xxx-facade' must exist.
    int start = message.indexOf("'");
    int end = message.indexOf("'", start + 1);
    if (start >= 0 && end > start) {
      return message.substring(start + 1, end);
    }
    return null;
  }

  // generatePackageInfo(), addModuleToParentPom(), toPascalCase(), deriveModuleRoot() inherited
  // from AbstractStructureFixer
}
