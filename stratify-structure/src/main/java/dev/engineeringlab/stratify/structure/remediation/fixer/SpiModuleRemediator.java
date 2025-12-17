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
 * Remediator for MS-004: Missing SPI module.
 *
 * <p>Creates missing SPI module directories with proper structure:
 *
 * <ul>
 *   <li>Creates SPI module with pom.xml and basic structure
 *   <li>Generates provider interface templates
 *   <li>Adds the module to parent pom.xml
 * </ul>
 *
 * <p>The SPI (Service Provider Interface) module contains:
 *
 * <ul>
 *   <li>Provider interfaces that external implementations must implement
 *   <li>Registration mechanism for providers
 *   <li>Provider metadata and discovery support
 * </ul>
 */
public class SpiModuleRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-004"};
  private static final int PRIORITY = 90; // High priority - run early

  public SpiModuleRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "SpiModuleRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates missing SPI (MS-004) module with provider interfaces";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!"MS-004".equals(ruleId)) {
      return FixResult.skipped(violation, "Not an MS-004 violation");
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

      String moduleName = extractModuleName(moduleRoot);
      String newModuleName = moduleName + "-spi";
      Path newModulePath = moduleRoot.resolve(newModuleName);

      // Check if already exists
      if (Files.exists(newModulePath)) {
        return FixResult.skipped(
            violation, "SPI module directory already exists: " + newModuleName);
      }

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Read parent pom to get groupId and version
      Path parentPom = moduleRoot.resolve("pom.xml");
      String groupId = "dev.engineeringlab";
      String version = "0.2.0-SNAPSHOT";
      String parentArtifactId = moduleName;

      String apiArtifactId = moduleName + "-api"; // default
      if (Files.exists(parentPom)) {
        String parentContent = readFile(parentPom);
        groupId = extractXmlValue(parentContent, "groupId", groupId);
        version = extractXmlValue(parentContent, "version", version);
        parentArtifactId = extractXmlValue(parentContent, "artifactId", moduleName);
        // Find actual API module from parent's modules list
        String detectedApiModule = findApiModule(parentContent, moduleName);
        if (detectedApiModule != null) {
          apiArtifactId = detectedApiModule;
        }
      }

      if (context.dryRun()) {
        diffs.add("+ Would create directory: " + newModuleName);
        diffs.add("+ Would create: " + newModuleName + "/pom.xml");
        diffs.add("+ Would create: " + newModuleName + "/src/main/java/");
        diffs.add("+ Would create provider interface template");
        diffs.add("+ Would add module to parent pom.xml");

        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would create SPI module: " + newModuleName)
            .diffs(diffs)
            .build();
      }

      // Create module directory
      Files.createDirectories(newModulePath);
      context.log("Created directory: %s", newModulePath);

      // Create pom.xml
      Path pomFile = newModulePath.resolve("pom.xml");
      String pomContent =
          generateSpiPomXml(
              groupId, parentArtifactId, newModuleName, version, moduleName, apiArtifactId);
      writeFile(pomFile, pomContent);
      modifiedFiles.add(pomFile);
      context.log("Created: %s", pomFile);

      // Create src/main/java directory with base package
      String basePackage = groupId + "." + moduleName.replace("-", ".");
      Path srcDir =
          newModulePath
              .resolve("src/main/java")
              .resolve(basePackage.replace(".", "/"))
              .resolve("spi");
      Files.createDirectories(srcDir);
      context.log("Created: %s", srcDir);

      // Create package-info.java
      Path packageInfo = srcDir.resolve("package-info.java");
      String packageInfoContent = generatePackageInfo(basePackage + ".spi", moduleName);
      writeFile(packageInfo, packageInfoContent);
      modifiedFiles.add(packageInfo);

      // Create provider interface template
      String providerName = toPascalCase(moduleName) + "Provider";
      Path providerFile = srcDir.resolve(providerName + ".java");
      String providerContent =
          generateProviderInterface(basePackage + ".spi", providerName, moduleName);
      writeFile(providerFile, providerContent);
      modifiedFiles.add(providerFile);
      context.log("Created provider interface: %s", providerFile);

      // Create annotation package for provider annotations
      Path annotationDir = srcDir.resolve("annotation");
      Files.createDirectories(annotationDir);

      // Create provider annotation
      Path annotationFile = annotationDir.resolve(providerName + ".java");
      String annotationContent =
          generateProviderAnnotation(basePackage + ".spi.annotation", providerName, moduleName);
      writeFile(annotationFile, annotationContent);
      modifiedFiles.add(annotationFile);

      // Create annotation package-info.java
      Path annotationPackageInfo = annotationDir.resolve("package-info.java");
      String annotationPkgContent =
          generateAnnotationPackageInfo(basePackage + ".spi.annotation", moduleName);
      writeFile(annotationPackageInfo, annotationPkgContent);
      modifiedFiles.add(annotationPackageInfo);

      // Add module to parent pom.xml
      if (Files.exists(parentPom)) {
        addModuleToParentPom(parentPom, newModuleName, context);
        modifiedFiles.add(parentPom);
      }

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Created SPI module: " + newModuleName)
          .modifiedFiles(modifiedFiles)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to create SPI module: " + e.getMessage());
    }
  }

  // extractModuleName() inherited from AbstractStructureFixer

  /**
   * Finds the API module from the parent pom's modules list. Looks for modules ending with -api
   * that might match the base module name.
   */
  private String findApiModule(String parentPomContent, String baseName) {
    // Extract all modules from parent pom
    java.util.regex.Pattern modulePattern =
        java.util.regex.Pattern.compile("<module>([^<]+)</module>");
    java.util.regex.Matcher matcher = modulePattern.matcher(parentPomContent);

    String bestMatch = null;
    while (matcher.find()) {
      String module = matcher.group(1).trim();
      if (module.endsWith("-api")) {
        // Check if this is a match for our base name
        // e.g., swq-api matches swq-engine, cache-api matches cache
        String moduleBase = module.replace("-api", "");
        if (baseName.contains(moduleBase)
            || moduleBase.contains(baseName.replace("-engine", "").replace("-parent", ""))) {
          bestMatch = module;
        }
        // Exact match takes priority
        if (module.equals(baseName + "-api")) {
          return module;
        }
      }
    }
    return bestMatch;
  }

  // extractXmlValue() inherited from AbstractStructureFixer

  private String generateSpiPomXml(
      String groupId,
      String parentArtifactId,
      String artifactId,
      String version,
      String moduleName,
      String apiArtifactId) {

    // apiArtifactId is now passed in after being detected from parent pom

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

    pom.append("    <name>").append(toPascalCase(moduleName)).append(" SPI</name>\n");
    pom.append("    <description>Service Provider Interface for ").append(moduleName);
    pom.append(" - contains provider contracts for extensibility</description>\n\n");

    pom.append("    <dependencies>\n");
    pom.append("        <!-- Depend on API module for shared types -->\n");
    pom.append("        <dependency>\n");
    pom.append("            <groupId>").append(groupId).append("</groupId>\n");
    pom.append("            <artifactId>").append(apiArtifactId).append("</artifactId>\n");
    pom.append("            <version>${project.version}</version>\n");
    pom.append("        </dependency>\n");
    pom.append("    </dependencies>\n\n");

    pom.append("</project>\n");

    return pom.toString();
  }

  private String generatePackageInfo(String packageName, String moduleName) {
    return """
                /**
                 * Service Provider Interface (SPI) for %s.
                 *
                 * <p>This package contains provider interfaces that external implementations
                 * must implement to extend the %s module functionality.
                 *
                 * <p>Key interfaces:
                 * <ul>
                 *   <li>{@link %sProvider} - Main provider interface</li>
                 * </ul>
                 *
                 * @see <a href="compliance-checklist.md">SEA Compliance Checklist</a>
                 */
                package %s;
                """
        .formatted(moduleName, moduleName, toPascalCase(moduleName), packageName);
  }

  private String generateProviderInterface(
      String packageName, String providerName, String moduleName) {
    return """
                package %s;

                /**
                 * Provider interface for %s implementations.
                 *
                 * <p>Implementations of this interface provide custom %s functionality
                 * that can be discovered and loaded at runtime.
                 *
                 * <p>To register a provider:
                 * <ol>
                 *   <li>Implement this interface</li>
                 *   <li>Annotate with @{@link annotation.%s}</li>
                 *   <li>Register via META-INF/services or ServiceLoader</li>
                 * </ol>
                 *
                 * @see annotation.%s
                 */
                public interface %s {

                    /**
                     * Returns the unique identifier for this provider.
                     *
                     * @return provider identifier, never null
                     */
                    String getId();

                    /**
                     * Returns the display name for this provider.
                     *
                     * @return human-readable name, never null
                     */
                    String getName();

                    /**
                     * Returns the priority of this provider.
                     * Higher values indicate higher priority.
                     *
                     * @return priority value, default is 0
                     */
                    default int getPriority() {
                        return 0;
                    }

                    /**
                     * Checks if this provider is enabled.
                     *
                     * @return true if enabled, false otherwise
                     */
                    default boolean isEnabled() {
                        return true;
                    }
                }
                """
        .formatted(packageName, moduleName, moduleName, providerName, providerName, providerName);
  }

  private String generateProviderAnnotation(
      String packageName, String providerName, String moduleName) {
    return """
                package %s;

                import java.lang.annotation.Documented;
                import java.lang.annotation.ElementType;
                import java.lang.annotation.Retention;
                import java.lang.annotation.RetentionPolicy;
                import java.lang.annotation.Target;

                /**
                 * Marks a class as a %s provider implementation.
                 *
                 * <p>Classes annotated with this annotation will be discovered
                 * and registered as providers for the %s module.
                 *
                 * <p>Example usage:
                 * <pre>{@code
                 * @%s(id = "my-provider", name = "My Provider")
                 * public class MyProvider implements %sProvider {
                 *     // implementation
                 * }
                 * }</pre>
                 */
                @Documented
                @Retention(RetentionPolicy.RUNTIME)
                @Target(ElementType.TYPE)
                public @interface %s {

                    /**
                     * Unique identifier for this provider.
                     *
                     * @return provider id
                     */
                    String id();

                    /**
                     * Display name for this provider.
                     *
                     * @return provider name
                     */
                    String name() default "";

                    /**
                     * Priority of this provider. Higher values = higher priority.
                     *
                     * @return priority value
                     */
                    int priority() default 0;
                }
                """
        .formatted(
            packageName,
            moduleName,
            moduleName,
            providerName,
            toPascalCase(moduleName),
            providerName);
  }

  private String generateAnnotationPackageInfo(String packageName, String moduleName) {
    return """
                /**
                 * Annotations for %s provider implementations.
                 *
                 * <p>This package contains annotations used to mark and configure
                 * provider implementations.
                 */
                package %s;
                """
        .formatted(moduleName, packageName);
  }

  // addModuleToParentPom(), toPascalCase() inherited from AbstractStructureFixer
}
