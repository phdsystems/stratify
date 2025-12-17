package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Remediator for MS-001 and MS-002: Missing API and Core modules.
 *
 * <p>Creates missing module directories with proper structure:
 *
 * <ul>
 *   <li>MS-001: Creates API module with pom.xml and basic structure
 *   <li>MS-002: Creates Core module with pom.xml and basic structure
 * </ul>
 *
 * <p>The fixer will:
 *
 * <ol>
 *   <li>Create the module directory
 *   <li>Generate a pom.xml with correct parent reference
 *   <li>Create src/main/java directory structure
 *   <li>Add the module to parent pom.xml if needed
 * </ol>
 */
public class MissingModuleRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-001", "MS-002"};
  private static final int PRIORITY = 90; // High priority - run early

  public MissingModuleRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MissingModuleRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates missing API (MS-001) and Core (MS-002) modules";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!"MS-001".equals(ruleId) && !"MS-002".equals(ruleId)) {
      return FixResult.skipped(violation, "Not an MS-001 or MS-002 violation");
    }

    try {
      Path moduleRoot = context.moduleRoot();

      // Check if this is a parent module (packaging=pom)
      // Only parent modules can have submodules
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
      String moduleName = extractModuleName(moduleRoot);
      String moduleType = "MS-001".equals(ruleId) ? "api" : "core";

      // Determine the new module directory name
      String newModuleName = moduleName + "-" + moduleType;
      Path newModulePath = moduleRoot.resolve(newModuleName);

      // Check if already exists
      if (Files.exists(newModulePath)) {
        return FixResult.skipped(violation, "Module directory already exists: " + newModuleName);
      }

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Read parent pom to get groupId and version
      Path parentPom = moduleRoot.resolve("pom.xml");
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
            .status(FixStatus.FIXED)
            .description("Would create " + moduleType.toUpperCase() + " module: " + newModuleName)
            .diffs(diffs)
            .build();
      }

      // Create module directory
      Files.createDirectories(newModulePath);
      context.log("Created directory: %s", newModulePath);

      // Create pom.xml
      Path pomFile = newModulePath.resolve("pom.xml");
      String pomContent =
          generatePomXml(groupId, parentArtifactId, newModuleName, version, moduleType, moduleName);
      writeFile(pomFile, pomContent);
      modifiedFiles.add(pomFile);
      context.log("Created: %s", pomFile);

      // Create src/main/java directory
      String basePackage = groupId + "." + moduleName.replace("-", ".");
      Path srcDir =
          newModulePath
              .resolve("src/main/java")
              .resolve(basePackage.replace(".", "/"))
              .resolve(moduleType);
      Files.createDirectories(srcDir);
      context.log("Created: %s", srcDir);

      // Create a package-info.java
      Path packageInfo = srcDir.resolve("package-info.java");
      String layerDescription = getLayerDescription(moduleType);
      String packageInfoContent =
          generatePackageInfo(basePackage + "." + moduleType, moduleName, layerDescription);
      writeFile(packageInfo, packageInfoContent);
      modifiedFiles.add(packageInfo);

      // Add module to parent pom.xml
      if (Files.exists(parentPom)) {
        addModuleToParentPom(parentPom, newModuleName, context);
        modifiedFiles.add(parentPom);
      }

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Created " + moduleType.toUpperCase() + " module: " + newModuleName)
          .modifiedFiles(modifiedFiles)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to create module: " + e.getMessage());
    }
  }

  // extractModuleName(), extractXmlValue() inherited from AbstractStructureFixer

  private String generatePomXml(
      String groupId,
      String parentArtifactId,
      String artifactId,
      String version,
      String moduleType,
      String moduleName) {

    String description =
        moduleType.equals("api")
            ? "API module for " + moduleName + " - contains public interfaces and contracts"
            : "Core module for " + moduleName + " - contains implementations";

    String name =
        moduleType.equals("api")
            ? toPascalCase(moduleName) + " API"
            : toPascalCase(moduleName) + " Core";

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

    pom.append("    <artifactId>").append(artifactId).append("</artifactId>\n");
    pom.append("    <packaging>jar</packaging>\n\n");

    pom.append("    <name>").append(name).append("</name>\n");
    pom.append("    <description>").append(description).append("</description>\n\n");

    // Add dependencies section
    pom.append("    <dependencies>\n");
    if (moduleType.equals("core")) {
      // Core depends on API
      String apiArtifactId = artifactId.replace("-core", "-api");
      pom.append("        <dependency>\n");
      pom.append("            <groupId>").append(groupId).append("</groupId>\n");
      pom.append("            <artifactId>").append(apiArtifactId).append("</artifactId>\n");
      pom.append("            <version>${project.version}</version>\n");
      pom.append("        </dependency>\n");
    }
    pom.append("    </dependencies>\n\n");

    pom.append("</project>\n");

    return pom.toString();
  }

  private String getLayerDescription(String moduleType) {
    return switch (moduleType) {
      case "api" -> "API layer - public interfaces and contracts";
      case "core" -> "Core layer - implementations";
      case "spi" -> "SPI layer - service provider interfaces";
      case "facade" -> "Facade layer - unified entry points";
      default -> moduleType.toUpperCase() + " layer";
    };
  }

  // generatePackageInfo(), addModuleToParentPom(), toPascalCase() inherited from
  // AbstractStructureFixer
}
