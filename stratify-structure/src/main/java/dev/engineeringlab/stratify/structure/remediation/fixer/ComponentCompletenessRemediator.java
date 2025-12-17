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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-009: Components must be complete (at minimum: api + core).
 *
 * <p>A component is a set of related modules with a common base name. Each component must have at
 * minimum an API module and a Core module:
 *
 * <ul>
 *   <li>{base}-api - Public interfaces and contracts (required)
 *   <li>{base}-core - Implementation (required)
 * </ul>
 *
 * <p>This fixer creates the missing counterpart module when one half of the pair exists:
 *
 * <ul>
 *   <li>If {base}-api exists but {base}-core is missing, creates {base}-core
 *   <li>If {base}-core exists but {base}-api is missing, creates {base}-api
 * </ul>
 */
public class ComponentCompletenessRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-009"};
  private static final int PRIORITY = 85; // High priority, but after SPI module creation

  public ComponentCompletenessRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ComponentCompletenessRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates missing API or Core modules to complete component pairs (MS-009)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!"MS-009".equals(ruleId)) {
      return FixResult.skipped(violation, "Not an MS-009 violation");
    }

    try {
      Path moduleRoot = context.moduleRoot();

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

      // Parse violation message to determine what's missing
      String found = violation.found();
      if (found == null || found.isEmpty()) {
        return FixResult.skipped(violation, "Cannot determine missing component from violation");
      }

      // Extract component info from violation
      // Expected format: "Incomplete components: xxx-api exists but xxx-core is missing"
      ComponentInfo componentInfo = parseComponentInfo(found);
      if (componentInfo == null) {
        return FixResult.skipped(violation, "Cannot parse component info from violation: " + found);
      }

      String baseName = componentInfo.baseName;
      String missingType = componentInfo.missingType;
      String newModuleName = baseName + "-" + missingType;
      Path newModulePath = moduleRoot.resolve(newModuleName);

      // Check if target module already exists
      if (Files.exists(newModulePath)) {
        return FixResult.skipped(violation, "Module directory already exists: " + newModuleName);
      }

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Read parent pom to get groupId and version
      Path parentPom = moduleRoot.resolve("pom.xml");
      String groupId = "dev.engineeringlab";
      String version = "0.2.0-SNAPSHOT";
      String parentArtifactId = extractModuleName(moduleRoot);

      if (Files.exists(parentPom)) {
        String parentContent = readFile(parentPom);
        groupId = extractXmlValue(parentContent, "groupId", groupId);
        version = extractXmlValue(parentContent, "version", version);
        parentArtifactId = extractXmlValue(parentContent, "artifactId", parentArtifactId);
      }

      if (context.dryRun()) {
        diffs.add("+ Would create directory: " + newModuleName);
        diffs.add("+ Would create: " + newModuleName + "/pom.xml");
        diffs.add("+ Would create: " + newModuleName + "/src/main/java/");
        diffs.add("+ Would add module to parent pom.xml");

        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description(
                "Would create "
                    + missingType.toUpperCase()
                    + " module to complete component: "
                    + newModuleName)
            .diffs(diffs)
            .build();
      }

      // Create module directory
      Files.createDirectories(newModulePath);
      context.log("Created directory: %s", newModulePath);

      // Create pom.xml
      Path pomFile = newModulePath.resolve("pom.xml");
      String pomContent =
          generatePomXml(groupId, parentArtifactId, newModuleName, version, missingType, baseName);
      writeFile(pomFile, pomContent);
      modifiedFiles.add(pomFile);
      context.log("Created: %s", pomFile);

      // Create src/main/java directory with base package
      String basePackage = groupId + "." + baseName.replace("-", ".");
      Path srcDir =
          newModulePath
              .resolve("src/main/java")
              .resolve(basePackage.replace(".", "/"))
              .resolve(missingType);
      Files.createDirectories(srcDir);
      context.log("Created: %s", srcDir);

      // Create package-info.java
      Path packageInfo = srcDir.resolve("package-info.java");
      String layerDescription =
          missingType.equals("api")
              ? "API layer - public interfaces and contracts"
              : "Core layer - implementations";
      String packageInfoContent =
          generatePackageInfo(basePackage + "." + missingType, baseName, layerDescription);
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
          .description(
              "Created "
                  + missingType.toUpperCase()
                  + " module to complete component: "
                  + newModuleName)
          .modifiedFiles(modifiedFiles)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to complete component: " + e.getMessage());
    }
  }

  /**
   * Parses the violation found message to extract component info. Expected formats: - "Incomplete
   * components: xxx-api exists but xxx-core is missing" - "xxx-api exists but xxx-core is missing"
   */
  private ComponentInfo parseComponentInfo(String found) {
    // Pattern: "{base}-{type} exists but {base}-{missingType} is missing"
    Pattern pattern =
        Pattern.compile(
            "([a-z][a-z0-9-]*)-(api|core|spi|common|facade)\\s+exists\\s+but\\s+"
                + "([a-z][a-z0-9-]*)-(api|core|spi|common|facade)\\s+is\\s+missing",
            Pattern.CASE_INSENSITIVE);

    Matcher matcher = pattern.matcher(found);
    if (matcher.find()) {
      String existingBase = matcher.group(1);
      String missingBase = matcher.group(3);
      String missingType = matcher.group(4);

      // Validate that base names match
      if (existingBase.equals(missingBase)) {
        return new ComponentInfo(existingBase, missingType);
      }
    }

    // Try simpler pattern for just the missing module
    Pattern simplePattern =
        Pattern.compile("([a-z][a-z0-9-]*)-(api|core)\\s+is\\s+missing", Pattern.CASE_INSENSITIVE);
    matcher = simplePattern.matcher(found);
    if (matcher.find()) {
      return new ComponentInfo(matcher.group(1), matcher.group(2));
    }

    return null;
  }

  private static class ComponentInfo {
    final String baseName;
    final String missingType;

    ComponentInfo(String baseName, String missingType) {
      this.baseName = baseName;
      this.missingType = missingType.toLowerCase();
    }
  }

  // extractModuleName(), extractXmlValue() inherited from AbstractStructureFixer

  private String generatePomXml(
      String groupId,
      String parentArtifactId,
      String artifactId,
      String version,
      String moduleType,
      String baseName) {

    String description =
        moduleType.equals("api")
            ? "API module for " + baseName + " - contains public interfaces and contracts"
            : "Core module for " + baseName + " - contains implementations";

    String name =
        moduleType.equals("api")
            ? toPascalCase(baseName) + " API"
            : toPascalCase(baseName) + " Core";

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

    pom.append("    <dependencies>\n");
    if (moduleType.equals("core")) {
      // Core depends on API
      String apiArtifactId = baseName + "-api";
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

  // generatePackageInfo(), addModuleToParentPom(), toPascalCase() inherited from
  // AbstractStructureFixer
}
