package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.config.DependencyConfig;
import dev.engineeringlab.stratify.structure.config.DependencyConfig.CommonDependency;
import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.core.MavenWrapperGenerator;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for modular design architecture violations (MD-001 to MD-014).
 *
 * <p>This fixer handles the following violations:
 *
 * <ul>
 *   <li>MD-001: Remove parent from Pure Aggregator
 *   <li>MD-002: Remove parent from Parent Aggregator
 *   <li>MD-003: Remove parent from standalone Library
 *   <li>MD-004: Add parent to Submodule
 *   <li>MD-005: Add or remove mvnw based on hierarchy level
 *   <li>MD-006: Create Pure Aggregator pom.xml when multiple sibling modules exist
 *   <li>MD-007: Generate Maven wrapper when Maven is not available
 *   <li>MD-008: Rename Pure Aggregator from '-parent' suffix to '-aggregator'
 *   <li>MD-009: Remove dependencyManagement from Pure Aggregator
 *   <li>MD-010: Add dependencyManagement to Parent Aggregator
 *   <li>MD-011: Add BOM import to standalone module for dependency version management
 *   <li>MD-012: Create Parent Aggregator with common dependencies
 *   <li>MD-013: Remove versions from common deps in module, add to parent
 *   <li>MD-014: Add missing common deps to parent's dependencyManagement
 *   <li>MD-015: Create missing config files (bootstrap.yaml, common-dependencies.yaml)
 *   <li>MD-016: Report cross-aggregator dependencies (not auto-fixable - requires manual
 *       restructuring)
 * </ul>
 *
 * @see <a href="doc/3-design/modular-design-architecture.md">Modular Design Architecture</a>
 * @since 0.2.0
 */
public class ModularDesignRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {
    "MD-001", "MD-002", "MD-003", "MD-004", "MD-005", "MD-006", "MD-007", "MD-008",
    "MD-009", "MD-010", "MD-011", "MD-012", "MD-013", "MD-014", "MD-015", "MD-016",
    "MD-017"
  };

  private static final int PRIORITY = 80;

  /** Dependency configuration loaded from YAML. */
  private final DependencyConfig dependencyConfig;

  /** Pattern to match parent section in pom.xml. */
  // Pattern to match parent section including its line but preserving surrounding structure
  // We capture just the parent block on its own lines, not greedy whitespace that would merge lines
  private static final Pattern PARENT_PATTERN =
      Pattern.compile("[ \\t]*<parent>.*?</parent>[ \\t]*\\r?\\n?", Pattern.DOTALL);

  /** Pattern to extract groupId from pom.xml. */
  private static final Pattern GROUP_ID_PATTERN = Pattern.compile("<groupId>([^<]+)</groupId>");

  /** Pattern to extract artifactId from pom.xml. */
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<artifactId>([^<]+)</artifactId>");

  /** Pattern to extract version from pom.xml. */
  private static final Pattern VERSION_PATTERN = Pattern.compile("<version>([^<]+)</version>");

  public ModularDesignRemediator() {
    setPriority(PRIORITY);
    this.dependencyConfig = DependencyConfig.getInstance();
  }

  @Override
  public String getName() {
    return "ModularDesignRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes modular design architecture violations (MD-001 to MD-014) "
        + "including parent removal, mvnw placement, Pure Aggregator creation, "
        + "Maven availability, Pure Aggregator naming, dependencyManagement rules, "
        + "Parent Aggregator creation, and common dependency BOM management";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    return switch (violation.ruleId()) {
      case "MD-001" -> fixRemoveParentFromPureAggregator(violation, context);
      case "MD-002" -> fixRemoveParentFromParentAggregator(violation, context);
      case "MD-003" -> fixRemoveParentFromLibrary(violation, context);
      case "MD-004" -> fixAddParentToSubmodule(violation, context);
      case "MD-005" -> fixMvnwPlacement(violation, context);
      case "MD-006" -> fixCreatePureAggregator(violation, context);
      case "MD-007" -> fixMavenAvailability(violation, context);
      case "MD-008" -> fixPureAggregatorNaming(violation, context);
      case "MD-009" -> fixRemoveDependencyManagementFromPureAggregator(violation, context);
      case "MD-010" -> fixAddDependencyManagementToParentAggregator(violation, context);
      case "MD-011" -> fixAddMissingDependencyVersions(violation, context);
      case "MD-012" -> fixCreateParentAggregatorWithCommonDeps(violation, context);
      case "MD-013" -> fixRemoveVersionsFromCommonDeps(violation, context);
      case "MD-014" -> fixAddCommonDepsToParentDependencyManagement(violation, context);
      case "MD-015" -> fixCreateMissingConfigFiles(violation, context);
      case "MD-016" -> fixReportCrossAggregatorDependencies(violation, context);
      case "MD-017" -> fixSubmoduleReferencingAggregator(violation, context);
      default -> FixResult.notFixable(violation, "Unknown rule: " + violation.ruleId());
    };
  }

  /**
   * MD-001: Remove parent element from Pure Aggregator pom.xml.
   *
   * <p>Pure Aggregators are modules with pom packaging that group other modules but do NOT provide
   * dependency management configuration. If the module has a &lt;dependencyManagement&gt; section,
   * it's a Parent Aggregator, not a Pure Aggregator, and this fix should be skipped.
   */
  private FixResult fixRemoveParentFromPureAggregator(
      StructureViolation violation, FixerContext context) {
    // Verify this is actually a Pure Aggregator (no dependencyManagement)
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String content = readFile(pomFile);

      // Check if this module has dependencyManagement - if so, it's a Parent Aggregator, not Pure
      if (DEPENDENCY_MANAGEMENT_PATTERN.matcher(content).find()) {
        return FixResult.skipped(
            violation,
            "Module has <dependencyManagement> section - this is a Parent Aggregator, not a Pure Aggregator. "
                + "Use MD-002 to remove parent from Parent Aggregators.");
      }
    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to read pom.xml: " + e.getMessage());
    }

    return removeParentFromPom(violation, context, "Pure Aggregator");
  }

  /** MD-002: Remove parent element from Parent Aggregator pom.xml. */
  private FixResult fixRemoveParentFromParentAggregator(
      StructureViolation violation, FixerContext context) {
    return removeParentFromPom(violation, context, "Parent Aggregator");
  }

  /** MD-003: Remove parent element from Library pom.xml. */
  private FixResult fixRemoveParentFromLibrary(StructureViolation violation, FixerContext context) {
    return removeParentFromPom(violation, context, "Library");
  }

  /** Common implementation for removing parent element from pom.xml. */
  private FixResult removeParentFromPom(
      StructureViolation violation, FixerContext context, String moduleType) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Check if parent section exists
      Matcher parentMatcher = PARENT_PATTERN.matcher(originalContent);
      if (!parentMatcher.find()) {
        return FixResult.skipped(violation, "No <parent> section found in pom.xml");
      }

      // Extract parent info before removing
      String parentSection = parentMatcher.group();
      String parentGroupId = extractValue(parentSection, GROUP_ID_PATTERN);
      String parentVersion = extractValue(parentSection, VERSION_PATTERN);

      // Check if groupId and version are already defined outside parent
      boolean hasGroupId =
          originalContent.contains("<groupId>")
              && !parentSection.contains(extractMatch(originalContent, GROUP_ID_PATTERN));
      boolean hasVersion =
          originalContent.contains("<version>")
              && !parentSection.contains(extractMatch(originalContent, VERSION_PATTERN));

      // Remove parent section
      String modifiedContent = parentMatcher.replaceFirst("");

      // Add groupId and version if they were only in parent
      if (!hasGroupId && parentGroupId != null) {
        modifiedContent =
            addAfterModelVersion(modifiedContent, "  <groupId>" + parentGroupId + "</groupId>");
      }
      if (!hasVersion && parentVersion != null) {
        // Find artifactId line and add version after it
        modifiedContent =
            addAfterArtifactId(modifiedContent, "  <version>" + parentVersion + "</version>");
      }

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would remove <parent> section from " + moduleType + " pom.xml",
            List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after removing parent. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Removed <parent> section from " + moduleType + " pom.xml",
          List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /** MD-004: Add parent element to Submodule pom.xml. */
  private FixResult fixAddParentToSubmodule(StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Check if parent section already exists
      if (PARENT_PATTERN.matcher(originalContent).find()) {
        return FixResult.skipped(violation, "<parent> section already exists");
      }

      // Extract current artifactId to determine parent
      String artifactId = extractValue(originalContent, ARTIFACT_ID_PATTERN);
      if (artifactId == null) {
        return FixResult.failed(violation, "Cannot determine artifactId from pom.xml");
      }

      // Derive parent artifactId from submodule name
      String parentArtifactId = deriveParentArtifactId(artifactId);
      if (parentArtifactId == null) {
        return FixResult.notFixable(
            violation, "Cannot derive parent artifactId from: " + artifactId);
      }

      // Try to get groupId and version from current pom or parent directory
      String groupId = extractValue(originalContent, GROUP_ID_PATTERN);
      String version = extractValue(originalContent, VERSION_PATTERN);

      if (groupId == null) {
        groupId = context.getNamespace();
      }
      if (version == null) {
        version = "0.2.0-SNAPSHOT"; // Default version
      }

      // Create parent section
      String parentSection =
          String.format(
              """

            <parent>
              <groupId>%s</groupId>
              <artifactId>%s</artifactId>
              <version>%s</version>
              <relativePath>../pom.xml</relativePath>
            </parent>
          """,
              groupId, parentArtifactId, version);

      // Insert after modelVersion
      String modifiedContent = addAfterModelVersion(originalContent, parentSection);

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would add <parent> section pointing to " + parentArtifactId,
            List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after adding parent. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Added <parent> section pointing to " + parentArtifactId,
          List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /** MD-005: Add or remove mvnw based on hierarchy level. */
  private FixResult fixMvnwPlacement(StructureViolation violation, FixerContext context) {
    Path modulePath = violation.location();
    if (modulePath == null || !Files.isDirectory(modulePath)) {
      return FixResult.skipped(violation, "Module directory not found");
    }

    boolean shouldHaveMvnw =
        violation.expected() != null && violation.expected().contains("present");

    Path mvnwPath = modulePath.resolve("mvnw");
    Path mvnDirPath = modulePath.resolve(".mvn");

    try {
      if (shouldHaveMvnw) {
        // Need to add mvnw - this typically requires running maven wrapper plugin
        if (context.dryRun()) {
          return FixResult.dryRun(
              violation,
              List.of(mvnwPath, mvnDirPath),
              "Would generate mvnw and .mvn/ directory using 'mvn wrapper:wrapper'",
              List.of());
        }

        // We can't actually run mvn wrapper:wrapper, but we can copy from project root
        Path projectMvnw = context.projectRoot().resolve("mvnw");
        Path projectMvnDir = context.projectRoot().resolve(".mvn");

        List<Path> modifiedFiles = new ArrayList<>();

        if (Files.exists(projectMvnw)) {
          Files.copy(projectMvnw, mvnwPath);
          modifiedFiles.add(mvnwPath);
        }

        if (Files.isDirectory(projectMvnDir)) {
          copyDirectory(projectMvnDir, mvnDirPath);
          modifiedFiles.add(mvnDirPath);
        }

        if (modifiedFiles.isEmpty()) {
          // No mvnw at project root - generate it directly
          MavenWrapperGenerator generator = new MavenWrapperGenerator();
          List<Path> generatedFiles = generator.generate(modulePath);
          return FixResult.success(
              violation,
              generatedFiles,
              "Generated Maven wrapper files (mvnw, mvnw.cmd, .mvn/wrapper/*)");
        }

        return FixResult.success(
            violation, modifiedFiles, "Copied mvnw and .mvn/ from project root");

      } else {
        // Need to remove mvnw
        List<Path> toRemove = new ArrayList<>();
        if (Files.exists(mvnwPath)) {
          toRemove.add(mvnwPath);
        }
        if (Files.isDirectory(mvnDirPath)) {
          toRemove.add(mvnDirPath);
        }

        if (toRemove.isEmpty()) {
          return FixResult.skipped(violation, "No mvnw or .mvn/ to remove");
        }

        if (context.dryRun()) {
          return FixResult.dryRun(
              violation, toRemove, "Would remove mvnw and .mvn/ directory", List.of());
        }

        // Backup and remove
        for (Path path : toRemove) {
          if (Files.isDirectory(path)) {
            deleteDirectory(path);
          } else {
            backup(path, context.projectRoot());
            Files.deleteIfExists(path);
          }
        }

        return FixResult.success(violation, toRemove, "Removed mvnw and .mvn/ directory");
      }

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to fix mvnw placement: " + e.getMessage());
    }
  }

  /** MD-006: Create Pure Aggregator pom.xml when multiple sibling modules exist. */
  private FixResult fixCreatePureAggregator(StructureViolation violation, FixerContext context) {
    Path parentPath = violation.location();
    if (parentPath == null || !Files.isDirectory(parentPath)) {
      return FixResult.skipped(violation, "Parent directory not found");
    }

    Path pomFile = parentPath.resolve("pom.xml");
    if (Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml already exists at " + parentPath);
    }

    try {
      // Find all child modules (directories with pom.xml)
      List<String> modules = new ArrayList<>();
      try (var stream = Files.newDirectoryStream(parentPath, Files::isDirectory)) {
        for (Path child : stream) {
          if (Files.exists(child.resolve("pom.xml"))) {
            modules.add(child.getFileName().toString());
          }
        }
      }

      if (modules.isEmpty()) {
        return FixResult.skipped(violation, "No child modules found");
      }

      // Sort modules for consistent ordering
      modules.sort(String::compareTo);

      // Generate pom.xml content
      String artifactId = parentPath.getFileName().toString() + "-parent";
      String groupId = context.getNamespace();
      String version = "0.2.0-SNAPSHOT";

      StringBuilder modulesXml = new StringBuilder();
      for (String module : modules) {
        modulesXml.append("    <module>").append(module).append("</module>\n");
      }

      String pomContent =
          String.format(
              """
          <?xml version="1.0" encoding="UTF-8"?>
          <project xmlns="http://maven.apache.org/POM/4.0.0"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                   http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>%s</groupId>
            <artifactId>%s</artifactId>
            <version>%s</version>
            <packaging>pom</packaging>

            <name>%s (Pure Aggregator)</name>
            <description>Pure aggregator - NO CODE, just grouping independent modules</description>

            <modules>
          %s  </modules>

          </project>
          """,
              groupId,
              artifactId,
              version,
              parentPath.getFileName().toString(),
              modulesXml.toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would create Pure Aggregator pom.xml with " + modules.size() + " modules",
            List.of("+ " + pomContent.replace("\n", "\n+ ")));
      }

      // Write pom.xml
      writeFile(pomFile, pomContent);

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Created Pure Aggregator pom.xml with modules: " + String.join(", ", modules));

    } catch (IOException e) {
      return FixResult.failed(
          violation, "Failed to create Pure Aggregator pom.xml: " + e.getMessage());
    }
  }

  /**
   * MD-007: Generate Maven wrapper when Maven is not available.
   *
   * <p>This fix attempts to generate the Maven wrapper (mvnw) by:
   *
   * <ol>
   *   <li>Copying mvnw from project root if available
   *   <li>Running 'mvn wrapper:wrapper' if mvn is in PATH
   * </ol>
   */
  private FixResult fixMavenAvailability(StructureViolation violation, FixerContext context) {
    Path modulePath = violation.location();
    if (modulePath == null || !Files.isDirectory(modulePath)) {
      return FixResult.skipped(violation, "Module directory not found");
    }

    Path mvnwPath = modulePath.resolve("mvnw");
    Path mvnDirPath = modulePath.resolve(".mvn");

    // Check if mvnw already exists
    if (Files.exists(mvnwPath)) {
      return FixResult.skipped(violation, "mvnw already exists");
    }

    try {
      // Try to copy from project root first
      Path projectMvnw = context.projectRoot().resolve("mvnw");
      Path projectMvnDir = context.projectRoot().resolve(".mvn");

      if (Files.exists(projectMvnw) && Files.isDirectory(projectMvnDir)) {
        if (context.dryRun()) {
          return FixResult.dryRun(
              violation,
              List.of(mvnwPath, mvnDirPath),
              "Would copy mvnw and .mvn/ from project root",
              List.of());
        }

        List<Path> modifiedFiles = new ArrayList<>();

        // Copy mvnw
        Files.copy(projectMvnw, mvnwPath);
        modifiedFiles.add(mvnwPath);

        // Copy mvnw.cmd if exists (for Windows)
        Path projectMvnwCmd = context.projectRoot().resolve("mvnw.cmd");
        if (Files.exists(projectMvnwCmd)) {
          Files.copy(projectMvnwCmd, modulePath.resolve("mvnw.cmd"));
          modifiedFiles.add(modulePath.resolve("mvnw.cmd"));
        }

        // Copy .mvn directory
        copyDirectory(projectMvnDir, mvnDirPath);
        modifiedFiles.add(mvnDirPath);

        return FixResult.success(
            violation, modifiedFiles, "Copied mvnw and .mvn/ from project root");
      }

      // Try to run mvn wrapper:wrapper if mvn is available
      if (isMvnInPath()) {
        if (context.dryRun()) {
          return FixResult.dryRun(
              violation,
              List.of(mvnwPath, mvnDirPath),
              "Would run 'mvn wrapper:wrapper' to generate Maven wrapper",
              List.of());
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(modulePath.toFile());
        processBuilder.command("mvn", "wrapper:wrapper", "-q");
        Process process = processBuilder.start();
        int exitCode = process.waitFor();

        if (exitCode == 0 && Files.exists(mvnwPath)) {
          List<Path> modifiedFiles = new ArrayList<>();
          modifiedFiles.add(mvnwPath);
          if (Files.isDirectory(mvnDirPath)) {
            modifiedFiles.add(mvnDirPath);
          }
          return FixResult.success(
              violation, modifiedFiles, "Generated Maven wrapper using 'mvn wrapper:wrapper'");
        } else {
          return FixResult.failed(
              violation, "Failed to generate Maven wrapper (exit code: " + exitCode + ")");
        }
      }

      // Neither copy nor mvn available - generate directly
      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(mvnwPath, mvnDirPath),
            "Would generate Maven wrapper files directly",
            List.of());
      }

      MavenWrapperGenerator generator = new MavenWrapperGenerator();
      List<Path> generatedFiles = generator.generate(modulePath);
      return FixResult.success(
          violation,
          generatedFiles,
          "Generated Maven wrapper files (mvnw, mvnw.cmd, .mvn/wrapper/*)");

    } catch (IOException | InterruptedException e) {
      return FixResult.failed(violation, "Failed to fix Maven availability: " + e.getMessage());
    }
  }

  /**
   * MD-008: Rename Pure Aggregator to use '-aggregator' suffix.
   *
   * <p>This fix renames the artifactId in the pom.xml to '{name}-aggregator'. It handles both:
   *
   * <ul>
   *   <li>{@code foo-parent} → {@code foo-aggregator}
   *   <li>{@code foo} → {@code foo-aggregator}
   * </ul>
   *
   * <p>It also updates the {@code <name>} element if present.
   *
   * <p>Note: This fix does NOT rename the directory. That must be done manually or by a separate
   * refactoring tool, as it may require updating references in other modules.
   */
  private FixResult fixPureAggregatorNaming(StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Extract current artifactId (the project's own, not the parent's)
      String currentArtifactId = extractProjectArtifactId(originalContent);
      if (currentArtifactId == null) {
        return FixResult.skipped(violation, "Could not extract artifactId from pom.xml");
      }

      // Skip if already has correct suffix
      if (currentArtifactId.endsWith("-aggregator")) {
        return FixResult.skipped(
            violation, "artifactId already ends with '-aggregator': " + currentArtifactId);
      }

      // Calculate new artifactId (add '-aggregator' suffix)
      String baseName;
      if (currentArtifactId.endsWith("-parent")) {
        baseName = currentArtifactId.substring(0, currentArtifactId.length() - "-parent".length());
      } else {
        baseName = currentArtifactId;
      }
      String newArtifactId = baseName + "-aggregator";

      context
          .logger()
          .accept("  📦 Renaming artifactId: " + currentArtifactId + " -> " + newArtifactId);

      // Replace artifactId in pom.xml
      String modifiedContent = originalContent;

      // Find and replace the artifactId that's NOT inside <parent> section
      int parentEndIdx = modifiedContent.indexOf("</parent>");
      int searchStart = parentEndIdx > 0 ? parentEndIdx : 0;

      String searchPattern = "<artifactId>" + currentArtifactId + "</artifactId>";
      int artifactIdIdx = modifiedContent.indexOf(searchPattern, searchStart);

      if (artifactIdIdx < 0) {
        artifactIdIdx = modifiedContent.indexOf(searchPattern);
      }

      if (artifactIdIdx >= 0) {
        String replacement = "<artifactId>" + newArtifactId + "</artifactId>";
        modifiedContent =
            modifiedContent.substring(0, artifactIdIdx)
                + replacement
                + modifiedContent.substring(artifactIdIdx + searchPattern.length());
      } else {
        return FixResult.failed(
            violation, "Could not find artifactId '" + currentArtifactId + "' in pom.xml");
      }

      // Update <name> element
      modifiedContent =
          modifiedContent.replace(">" + currentArtifactId + "<", ">" + newArtifactId + "<");
      modifiedContent =
          modifiedContent.replaceAll(
              "(<name>[^<]*)" + Pattern.quote(baseName) + "\\s*\\(?[Pp]arent\\)?([^<]*</name>)",
              "$1" + baseName + " (Pure Aggregator)$2");

      // Note: We do NOT update child modules' parent references.
      // -aggregator modules should never be referenced as parent by submodules.
      // Only other -aggregator modules can reference -aggregator.
      // If child modules currently reference this module as parent, they need
      // architectural changes (e.g., referencing a proper -parent module instead).

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would rename artifactId from '" + currentArtifactId + "' to '" + newArtifactId + "'",
            List.of(diff));
      }

      // VERIFIED WORKFLOW: BACKUP → WRITE → COMPILE → CLEANUP/ROLLBACK
      context.logger().accept("  📝 Backing up pom.xml...");
      backup(pomFile, context.projectRoot());

      context.logger().accept("  ✍️ Writing modified pom.xml...");
      writeFile(pomFile, modifiedContent);

      context.logger().accept("  🔍 Running compile verification...");
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        context.logger().accept("  ❌ Compile verification failed - rolling back pom.xml");
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after renaming "
                + currentArtifactId
                + " to "
                + newArtifactId
                + ". File rolled back. Child modules may need architectural "
                + "changes to reference a proper -parent module. Error: "
                + compileResult.output);
      }

      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      String description =
          "Renamed artifactId from '" + currentArtifactId + "' to '" + newArtifactId + "'";

      return FixResult.builder()
          .status(FixStatus.FIXED)
          .violation(violation)
          .modifiedFiles(List.of(pomFile))
          .description(description)
          .diffs(List.of(diff))
          .build();

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  private List<Path> findChildModulePomFiles(Path parentPomFile, String parentPomContent) {
    List<Path> childPoms = new ArrayList<>();
    Path parentDir = parentPomFile.getParent();

    Pattern modulesPattern = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL);
    Matcher modulesMatcher = modulesPattern.matcher(parentPomContent);

    if (modulesMatcher.find()) {
      String modulesSection = modulesMatcher.group(1);
      Pattern modulePattern = Pattern.compile("<module>([^<]+)</module>");
      Matcher moduleMatcher = modulePattern.matcher(modulesSection);

      while (moduleMatcher.find()) {
        String moduleName = moduleMatcher.group(1).trim();
        Path childPom = parentDir.resolve(moduleName).resolve("pom.xml");
        childPoms.add(childPom);
      }
    }

    return childPoms;
  }

  private String updateParentArtifactIdInChild(
      String childContent, String oldArtifactId, String newArtifactId) {
    Pattern parentPattern =
        Pattern.compile(
            "(<parent>.*?<artifactId>)"
                + Pattern.quote(oldArtifactId)
                + "(</artifactId>.*?</parent>)",
            Pattern.DOTALL);
    Matcher matcher = parentPattern.matcher(childContent);

    if (matcher.find()) {
      return matcher.replaceFirst("$1" + newArtifactId + "$2");
    }

    return childContent;
  }

  /**
   * Extracts the project's own artifactId from pom.xml content, excluding the parent's artifactId.
   *
   * <p>In a pom.xml with a parent section, there are two artifactId elements:
   *
   * <ul>
   *   <li>The parent's artifactId (inside {@code <parent>...</parent>})
   *   <li>The project's own artifactId (outside the parent section)
   * </ul>
   *
   * <p>This method finds and returns the project's own artifactId.
   *
   * @param pomContent the pom.xml content
   * @return the project's own artifactId, or null if not found
   */
  private String extractProjectArtifactId(String pomContent) {
    // Find the end of the </parent> section
    int parentEndIdx = pomContent.indexOf("</parent>");

    // Search for artifactId after the parent section (if parent exists)
    String searchContent = parentEndIdx > 0 ? pomContent.substring(parentEndIdx) : pomContent;

    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(searchContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    return null;
  }

  /** Pattern to match dependencyManagement section in pom.xml. */
  private static final Pattern DEPENDENCY_MANAGEMENT_PATTERN =
      Pattern.compile("\\s*<dependencyManagement>.*?</dependencyManagement>\\s*", Pattern.DOTALL);

  /**
   * MD-009: Remove dependencyManagement from Pure Aggregator pom.xml.
   *
   * <p>Pure Aggregators should only group modules, not provide shared configuration. The
   * dependencyManagement section should be removed.
   */
  private FixResult fixRemoveDependencyManagementFromPureAggregator(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Check if dependencyManagement section exists
      Matcher depMgmtMatcher = DEPENDENCY_MANAGEMENT_PATTERN.matcher(originalContent);
      if (!depMgmtMatcher.find()) {
        return FixResult.skipped(violation, "No <dependencyManagement> section found in pom.xml");
      }

      // Remove dependencyManagement section
      String modifiedContent = depMgmtMatcher.replaceFirst("\n");

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would remove <dependencyManagement> section from Pure Aggregator pom.xml",
            List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after removing dependencyManagement. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Removed <dependencyManagement> section from Pure Aggregator. "
              + "If shared dependencies are needed, consider converting to Parent Aggregator "
              + "with '-parent' suffix.",
          List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /**
   * MD-010: Add dependencyManagement to Parent Aggregator pom.xml.
   *
   * <p>Parent Aggregators must have dependencyManagement to provide version management for
   * submodules. This fix collects dependencies from all submodules and creates a
   * dependencyManagement section with common dependencies.
   */
  private FixResult fixAddDependencyManagementToParentAggregator(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Check if dependencyManagement already exists
      if (DEPENDENCY_MANAGEMENT_PATTERN.matcher(originalContent).find()) {
        return FixResult.skipped(
            violation, "<dependencyManagement> section already exists in pom.xml");
      }

      // Scan submodules to collect dependencies
      Path parentDir = pomFile.getParent();
      List<DependencyInfo> collectedDeps = collectDependenciesFromSubmodules(parentDir);

      // Generate dependencyManagement section
      StringBuilder depMgmtXml = new StringBuilder();
      depMgmtXml.append("\n  <dependencyManagement>\n");
      depMgmtXml.append("    <dependencies>\n");

      if (collectedDeps.isEmpty()) {
        // Add placeholder with common dependencies
        depMgmtXml.append("      <!-- TODO: Add managed dependencies here -->\n");
        depMgmtXml.append("      <!-- Example:\n");
        depMgmtXml.append("      <dependency>\n");
        depMgmtXml.append("        <groupId>org.slf4j</groupId>\n");
        depMgmtXml.append("        <artifactId>slf4j-api</artifactId>\n");
        depMgmtXml.append("        <version>2.0.9</version>\n");
        depMgmtXml.append("      </dependency>\n");
        depMgmtXml.append("      -->\n");
      } else {
        for (DependencyInfo dep : collectedDeps) {
          depMgmtXml.append("      <dependency>\n");
          depMgmtXml.append("        <groupId>").append(dep.groupId).append("</groupId>\n");
          depMgmtXml
              .append("        <artifactId>")
              .append(dep.artifactId)
              .append("</artifactId>\n");
          depMgmtXml.append("        <version>").append(dep.version).append("</version>\n");
          if (dep.scope != null && !dep.scope.equals("compile")) {
            depMgmtXml.append("        <scope>").append(dep.scope).append("</scope>\n");
          }
          depMgmtXml.append("      </dependency>\n");
        }
      }

      depMgmtXml.append("    </dependencies>\n");
      depMgmtXml.append("  </dependencyManagement>\n");

      // Insert before </project>
      String modifiedContent =
          originalContent.replace("</project>", depMgmtXml.toString() + "\n</project>");

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        String message =
            collectedDeps.isEmpty()
                ? "Would add <dependencyManagement> placeholder section"
                : "Would add <dependencyManagement> with "
                    + collectedDeps.size()
                    + " dependencies "
                    + "collected from submodules";
        return FixResult.dryRun(violation, List.of(pomFile), message, List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after adding dependencyManagement. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      String message =
          collectedDeps.isEmpty()
              ? "Added <dependencyManagement> placeholder section. "
                  + "Please populate with common dependency versions."
              : "Added <dependencyManagement> with "
                  + collectedDeps.size()
                  + " dependencies collected from submodules. "
                  + "Remove versions from submodule pom.xml files to use managed versions.";

      return FixResult.success(violation, List.of(pomFile), message, List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /** Collects unique dependencies with versions from all submodule pom.xml files. */
  private List<DependencyInfo> collectDependenciesFromSubmodules(Path parentDir) {
    List<DependencyInfo> deps = new ArrayList<>();
    java.util.Set<String> seen = new java.util.HashSet<>();

    try (var stream = Files.newDirectoryStream(parentDir, Files::isDirectory)) {
      for (Path submodule : stream) {
        Path submodulePom = submodule.resolve("pom.xml");
        if (Files.exists(submodulePom)) {
          collectDependenciesFromPom(submodulePom, deps, seen);
        }
      }
    } catch (IOException e) {
      // Ignore errors and return whatever we collected
    }

    return deps;
  }

  /** Parses a pom.xml and collects dependencies with versions. */
  private void collectDependenciesFromPom(
      Path pomFile, List<DependencyInfo> deps, java.util.Set<String> seen) {
    try {
      String content = readFile(pomFile);

      // Simple regex-based parsing for dependencies (not inside dependencyManagement)
      // Skip anything inside dependencyManagement
      String withoutDepMgmt = DEPENDENCY_MANAGEMENT_PATTERN.matcher(content).replaceAll("");

      // Find dependencies section
      Pattern depsPattern = Pattern.compile("<dependencies>(.*?)</dependencies>", Pattern.DOTALL);
      Matcher depsMatcher = depsPattern.matcher(withoutDepMgmt);

      while (depsMatcher.find()) {
        String depsSection = depsMatcher.group(1);

        // Parse individual dependencies
        Pattern depPattern =
            Pattern.compile(
                "<dependency>\\s*"
                    + "<groupId>([^<]+)</groupId>\\s*"
                    + "<artifactId>([^<]+)</artifactId>\\s*"
                    + "(?:<version>([^<]+)</version>)?\\s*"
                    + "(?:<scope>([^<]+)</scope>)?",
                Pattern.DOTALL);
        Matcher depMatcher = depPattern.matcher(depsSection);

        while (depMatcher.find()) {
          String groupId = depMatcher.group(1).trim();
          String artifactId = depMatcher.group(2).trim();
          String version = depMatcher.group(3);
          String scope = depMatcher.group(4);

          // Only include dependencies with explicit versions
          if (version != null && !version.contains("${")) {
            String key = groupId + ":" + artifactId;
            if (!seen.contains(key)) {
              seen.add(key);
              deps.add(
                  new DependencyInfo(
                      groupId, artifactId, version.trim(), scope != null ? scope.trim() : null));
            }
          }
        }
      }
    } catch (IOException e) {
      // Ignore errors
    }
  }

  /** Simple record to hold dependency information. */
  private record DependencyInfo(String groupId, String artifactId, String version, String scope) {}

  /**
   * MD-011: Add dependency versions to standalone module.
   *
   * <p>Modules under a Pure Aggregator don't inherit from a parent, so they need a way to get
   * dependency versions. This fix uses two strategies:
   *
   * <p>Strategy 1 (preferred): Add BOM import
   *
   * <ol>
   *   <li>Add dependencyManagement with engineeringlab-bom import
   *   <li>Dependencies without versions will get versions from the imported BOM
   * </ol>
   *
   * <p>Strategy 2 (fallback): Add versions from common-dependencies.yaml
   *
   * <ol>
   *   <li>Read common-dependencies.yaml from config/ directory
   *   <li>Add dependencyManagement with versions for all dependencies
   * </ol>
   */
  private FixResult fixAddMissingDependencyVersions(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Check if module already has dependencyManagement with BOM import
      if (originalContent.contains("<dependencyManagement>")
          && originalContent.contains("<type>pom</type>")
          && originalContent.contains("<scope>import</scope>")) {
        return FixResult.skipped(
            violation, "Module already has BOM import in dependencyManagement");
      }

      // Get groupId and version from pom.xml
      String groupId = extractValue(originalContent, GROUP_ID_PATTERN);
      String version = extractValue(originalContent, VERSION_PATTERN);

      if (groupId == null) {
        groupId = context.getNamespace();
      }
      if (groupId == null) {
        groupId = "dev.engineeringlab";
      }
      if (version == null) {
        version = "0.2.0-SNAPSHOT";
      }

      // Use engineeringlab-bom as the standard BOM
      String bomArtifactId = "engineeringlab-bom";

      // Build dependencyManagement content
      String depMgmtContent;

      // Check if we have common dependencies from config
      List<CommonDependency> allCommonDeps = dependencyConfig.getCommonDependencyList();

      // Filter to only deps actually used in this POM
      List<CommonDependency> usedDeps = filterUsedDependencies(originalContent, allCommonDeps);

      if (!usedDeps.isEmpty()) {
        // Add only the dependencies that are actually used in this module
        StringBuilder depsXml = new StringBuilder();
        depsXml.append("\n  <dependencyManagement>\n");
        depsXml.append("    <dependencies>\n");

        for (CommonDependency dep : usedDeps) {
          depsXml.append("      <dependency>\n");
          depsXml.append("        <groupId>").append(dep.getGroupId()).append("</groupId>\n");
          depsXml
              .append("        <artifactId>")
              .append(dep.getArtifactId())
              .append("</artifactId>\n");
          depsXml.append("        <version>").append(dep.getVersion()).append("</version>\n");
          if (dep.getScope() != null && !dep.getScope().equals("compile")) {
            depsXml.append("        <scope>").append(dep.getScope()).append("</scope>\n");
          }
          depsXml.append("      </dependency>\n");
        }

        depsXml.append("    </dependencies>\n");
        depsXml.append("  </dependencyManagement>\n");
        depMgmtContent = depsXml.toString();
      } else if (!allCommonDeps.isEmpty()) {
        // Fallback: Add BOM import if no specific deps matched but config exists
        depMgmtContent =
            String.format(
                """

          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        """,
                groupId, bomArtifactId, version);
      } else {
        // No config at all: Add BOM import
        depMgmtContent =
            String.format(
                """

          <dependencyManagement>
            <dependencies>
              <dependency>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
            </dependencies>
          </dependencyManagement>
        """,
                groupId, bomArtifactId, version);
      }

      String modifiedContent;
      if (originalContent.contains("<dependencyManagement>")) {
        // Merge with existing dependencyManagement
        modifiedContent =
            mergeIntoDependencyManagement(
                originalContent, usedDeps, groupId, bomArtifactId, version);
      } else {
        // Add new dependencyManagement section before <dependencies> or </project>
        if (originalContent.contains("<dependencies>")) {
          modifiedContent =
              originalContent.replace("<dependencies>", depMgmtContent + "\n  <dependencies>");
        } else {
          modifiedContent = addBeforeClosingProject(originalContent, depMgmtContent);
        }
      }

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      String description =
          usedDeps.isEmpty()
              ? "Would add BOM import for " + bomArtifactId + " to get dependency versions"
              : "Would add dependencyManagement with " + usedDeps.size() + " used dependencies";

      if (context.dryRun()) {
        return FixResult.dryRun(violation, List.of(pomFile), description, List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after adding dependency versions. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      String successMsg =
          usedDeps.isEmpty()
              ? "Added BOM import for "
                  + bomArtifactId
                  + ". Dependencies will now get versions from the BOM."
              : "Added dependencyManagement with "
                  + usedDeps.size()
                  + " managed dependencies (only those used in this module).";

      return FixResult.success(violation, List.of(pomFile), successMsg, List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /** Merges common dependencies into an existing dependencyManagement section. */
  private String mergeIntoDependencyManagement(
      String content,
      List<CommonDependency> commonDeps,
      String groupId,
      String bomArtifactId,
      String version) {
    if (commonDeps.isEmpty()) {
      // Add BOM import to existing dependencyManagement
      String bomImport =
          String.format(
              """
              <dependency>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>%s</version>
                <type>pom</type>
                <scope>import</scope>
              </dependency>
          """,
              groupId, bomArtifactId, version);

      // Find insertion point after <dependencies> in dependencyManagement
      Pattern pattern = Pattern.compile("(<dependencyManagement>\\s*<dependencies>)");
      Matcher matcher = pattern.matcher(content);
      if (matcher.find()) {
        return content.substring(0, matcher.end())
            + "\n      "
            + bomImport.trim()
            + "\n"
            + content.substring(matcher.end());
      }
      return content;
    }

    // Extract dependencyManagement section to check for existing deps
    String depMgmtSection = "";
    Matcher depMgmtMatcher = DEPENDENCY_MANAGEMENT_PATTERN.matcher(content);
    if (depMgmtMatcher.find()) {
      depMgmtSection = depMgmtMatcher.group(0);
    }

    // Add missing dependencies to existing dependencyManagement
    StringBuilder newDeps = new StringBuilder();
    for (CommonDependency dep : commonDeps) {
      // Check if dependency already exists IN dependencyManagement (not in regular dependencies)
      String depCheck = "<artifactId>" + dep.getArtifactId() + "</artifactId>";
      if (!depMgmtSection.contains(depCheck)) {
        newDeps.append("      <dependency>\n");
        newDeps.append("        <groupId>").append(dep.getGroupId()).append("</groupId>\n");
        newDeps
            .append("        <artifactId>")
            .append(dep.getArtifactId())
            .append("</artifactId>\n");
        newDeps.append("        <version>").append(dep.getVersion()).append("</version>\n");
        if (dep.getScope() != null && !dep.getScope().equals("compile")) {
          newDeps.append("        <scope>").append(dep.getScope()).append("</scope>\n");
        }
        newDeps.append("      </dependency>\n");
      }
    }

    if (newDeps.length() == 0) {
      return content; // Nothing to add
    }

    // Find insertion point after <dependencies> in dependencyManagement
    Pattern pattern = Pattern.compile("(<dependencyManagement>\\s*<dependencies>)");
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return content.substring(0, matcher.end())
          + "\n"
          + newDeps
          + content.substring(matcher.end());
    }
    return content;
  }

  /**
   * MD-012: Create Parent Aggregator with common dependencies.
   *
   * <p>When multiple modules under a Pure Aggregator share common dependencies, this fix creates a
   * Parent Aggregator with dependencyManagement to centralize version management. It then updates
   * the child modules to inherit from the new parent.
   */
  private FixResult fixCreateParentAggregatorWithCommonDeps(
      StructureViolation violation, FixerContext context) {
    Path aggregatorPomFile = resolvePomFile(violation.location());
    if (aggregatorPomFile == null || !Files.exists(aggregatorPomFile)) {
      return FixResult.skipped(violation, "Aggregator pom.xml not found");
    }

    Path parentDir = aggregatorPomFile.getParent();
    if (parentDir == null) {
      return FixResult.skipped(violation, "Cannot determine parent directory");
    }

    try {
      String aggregatorContent = readFile(aggregatorPomFile);

      // Extract info from aggregator
      String groupId = extractValue(aggregatorContent, GROUP_ID_PATTERN);
      String version = extractValue(aggregatorContent, VERSION_PATTERN);
      String aggregatorArtifactId = extractValue(aggregatorContent, ARTIFACT_ID_PATTERN);

      if (groupId == null) {
        groupId = context.getNamespace();
      }
      if (version == null) {
        version = "0.2.0-SNAPSHOT";
      }

      // Derive parent name from aggregator (e.g., observability-aggregator -> observability-parent)
      // Also handle case where artifactId already ends with -parent (e.g., cache-parent ->
      // cache-parent)
      String baseName =
          aggregatorArtifactId != null
              ? aggregatorArtifactId.replace("-aggregator", "").replace("-parent", "")
              : parentDir.getFileName().toString().replace("-parent", "");
      String parentArtifactId = baseName + "-parent";

      // Collect common dependencies from child modules
      List<DependencyInfo> commonDeps = collectDependenciesFromSubmodules(parentDir);

      // Check if parent already exists
      Path parentPomPath = parentDir.resolve(parentArtifactId).resolve("pom.xml");
      if (Files.exists(parentPomPath)) {
        return FixResult.skipped(
            violation, "Parent Aggregator already exists: " + parentArtifactId);
      }

      // Create parent directory
      Path parentModuleDir = parentDir.resolve(parentArtifactId);
      List<Path> modifiedFiles = new ArrayList<>();

      // Generate Parent Aggregator pom.xml
      StringBuilder depMgmtXml = new StringBuilder();
      depMgmtXml.append("\n  <dependencyManagement>\n");
      depMgmtXml.append("    <dependencies>\n");

      if (commonDeps.isEmpty()) {
        // Add common defaults from DependencyConfig
        for (CommonDependency dep : dependencyConfig.getCommonDependencyList()) {
          depMgmtXml.append("      <dependency>\n");
          depMgmtXml.append("        <groupId>").append(dep.getGroupId()).append("</groupId>\n");
          depMgmtXml
              .append("        <artifactId>")
              .append(dep.getArtifactId())
              .append("</artifactId>\n");
          depMgmtXml.append("        <version>").append(dep.getVersion()).append("</version>\n");
          if (dep.getScope() != null) {
            depMgmtXml.append("        <scope>").append(dep.getScope()).append("</scope>\n");
          }
          depMgmtXml.append("      </dependency>\n");
        }
      } else {
        for (DependencyInfo dep : commonDeps) {
          depMgmtXml.append("      <dependency>\n");
          depMgmtXml.append("        <groupId>").append(dep.groupId()).append("</groupId>\n");
          depMgmtXml
              .append("        <artifactId>")
              .append(dep.artifactId())
              .append("</artifactId>\n");
          depMgmtXml.append("        <version>").append(dep.version()).append("</version>\n");
          if (dep.scope() != null && !dep.scope().equals("compile")) {
            depMgmtXml.append("        <scope>").append(dep.scope()).append("</scope>\n");
          }
          depMgmtXml.append("      </dependency>\n");
        }
      }

      depMgmtXml.append("    </dependencies>\n");
      depMgmtXml.append("  </dependencyManagement>\n");

      // Find child modules from aggregator
      Pattern modulesPattern = Pattern.compile("<modules>.*?</modules>", Pattern.DOTALL);
      Matcher modulesMatcher = modulesPattern.matcher(aggregatorContent);
      String modulesSection = "";
      List<String> childModules = new ArrayList<>();
      if (modulesMatcher.find()) {
        modulesSection = modulesMatcher.group();
        Pattern modulePattern = Pattern.compile("<module>([^<]+)</module>");
        Matcher moduleMatcher = modulePattern.matcher(modulesSection);
        while (moduleMatcher.find()) {
          childModules.add(moduleMatcher.group(1));
        }
      }

      // Create parent pom content
      String parentPomContent =
          String.format(
              """
          <?xml version="1.0" encoding="UTF-8"?>
          <project xmlns="http://maven.apache.org/POM/4.0.0"
                   xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                   xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                   http://maven.apache.org/xsd/maven-4.0.0.xsd">
            <modelVersion>4.0.0</modelVersion>

            <groupId>%s</groupId>
            <artifactId>%s</artifactId>
            <version>%s</version>
            <packaging>pom</packaging>

            <name>%s (Parent Aggregator)</name>
            <description>Parent Aggregator providing shared dependency management for child modules</description>
          %s
          </project>
          """,
              groupId, parentArtifactId, version, baseName, depMgmtXml.toString());

      if (context.dryRun()) {
        List<String> diffs = new ArrayList<>();
        diffs.add("+ Create " + parentModuleDir.resolve("pom.xml"));
        diffs.add("+ " + parentPomContent.replace("\n", "\n+ "));
        diffs.add("+ Update aggregator to include " + parentArtifactId + " module");
        diffs.add(
            "+ Update "
                + childModules.size()
                + " child modules to inherit from "
                + parentArtifactId);
        return FixResult.dryRun(
            violation,
            List.of(parentModuleDir.resolve("pom.xml")),
            "Would create Parent Aggregator '"
                + parentArtifactId
                + "' with "
                + (commonDeps.isEmpty() ? "default" : commonDeps.size())
                + " managed dependencies",
            diffs);
      }

      // Create parent module directory and pom
      Files.createDirectories(parentModuleDir);
      Path parentPom = parentModuleDir.resolve("pom.xml");
      writeFile(parentPom, parentPomContent);
      modifiedFiles.add(parentPom);

      // Update aggregator pom to include the parent module
      String updatedAggregatorContent =
          aggregatorContent.replace(
              "<modules>", "<modules>\n    <module>" + parentArtifactId + "</module>");
      backup(aggregatorPomFile, context.projectRoot());
      writeFile(aggregatorPomFile, updatedAggregatorContent);
      modifiedFiles.add(aggregatorPomFile);

      // Update child modules to inherit from new parent
      int updatedModules = 0;
      for (String childModule : childModules) {
        Path childPom = parentDir.resolve(childModule).resolve("pom.xml");
        if (Files.exists(childPom)) {
          String childContent = readFile(childPom);

          // Check if already has parent
          if (!PARENT_PATTERN.matcher(childContent).find()) {
            // Add parent section
            String parentSection =
                String.format(
                    """

                  <parent>
                    <groupId>%s</groupId>
                    <artifactId>%s</artifactId>
                    <version>%s</version>
                    <relativePath>../%s/pom.xml</relativePath>
                  </parent>
                """,
                    groupId, parentArtifactId, version, parentArtifactId);

            String updatedChildContent = addAfterModelVersion(childContent, parentSection);

            // Remove version elements from dependencies that are now managed
            for (DependencyInfo dep : commonDeps) {
              String versionTag = "<version>" + dep.version() + "</version>";
              updatedChildContent = updatedChildContent.replace(versionTag + "\n      ", "");
              updatedChildContent = updatedChildContent.replace(versionTag, "");
            }

            backup(childPom, context.projectRoot());
            writeFile(childPom, updatedChildContent);
            modifiedFiles.add(childPom);
            updatedModules++;
          }
        }
      }

      String message =
          String.format(
              "Created Parent Aggregator '%s' with %d managed dependencies. "
                  + "Updated aggregator and %d child modules to use the new parent.",
              parentArtifactId,
              commonDeps.isEmpty()
                  ? dependencyConfig.getCommonDependencyList().size()
                  : commonDeps.size(),
              updatedModules);

      return FixResult.success(violation, modifiedFiles, message);

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to create Parent Aggregator: " + e.getMessage());
    }
  }

  /**
   * MD-013: Remove versions from common dependencies in module pom.
   *
   * <p>Common dependencies should get their versions from a Parent Aggregator's
   * dependencyManagement. This fix removes the version elements from common deps in the module and
   * ensures the parent has them in dependencyManagement.
   */
  private FixResult fixRemoveVersionsFromCommonDeps(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);
      String modifiedContent = originalContent;
      List<String> removedVersions = new ArrayList<>();

      // Pattern to match common dependencies with versions
      for (CommonDependency commonDep : dependencyConfig.getCommonDependencyList()) {
        String groupId = commonDep.getGroupId();
        String artifactId = commonDep.getArtifactId();

        // Pattern to find dependency with version
        Pattern depPattern =
            Pattern.compile(
                "(<dependency>\\s*"
                    + "<groupId>"
                    + Pattern.quote(groupId)
                    + "</groupId>\\s*"
                    + "<artifactId>"
                    + Pattern.quote(artifactId)
                    + "</artifactId>\\s*)"
                    + "<version>[^<]+</version>(\\s*)",
                Pattern.DOTALL);

        Matcher matcher = depPattern.matcher(modifiedContent);
        if (matcher.find()) {
          modifiedContent = matcher.replaceAll("$1$2");
          removedVersions.add(commonDep.getKey());
        }
      }

      if (removedVersions.isEmpty()) {
        return FixResult.skipped(
            violation, "No common dependencies with versions found in module pom");
      }

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would remove versions from "
                + removedVersions.size()
                + " common dependencies: "
                + String.join(", ", removedVersions),
            List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after removing versions. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Removed versions from "
              + removedVersions.size()
              + " common dependencies. "
              + "Ensure these are declared in the Parent Aggregator's dependencyManagement.",
          List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /**
   * MD-014: Add missing common dependencies to Parent Aggregator's dependencyManagement.
   *
   * <p>Parent Aggregators must have all common dependencies used by child modules declared in their
   * dependencyManagement section with appropriate versions.
   */
  private FixResult fixAddCommonDepsToParentDependencyManagement(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String originalContent = readFile(pomFile);

      // Parse violation to find which deps are missing
      String found = violation.found();
      List<String> missingDeps = new ArrayList<>();
      for (CommonDependency dep : dependencyConfig.getCommonDependencyList()) {
        if (found != null && found.contains(dep.getKey())) {
          missingDeps.add(dep.getKey());
        }
      }

      if (missingDeps.isEmpty()) {
        // Fallback: scan child modules to find used common deps
        Path parentDir = pomFile.getParent();
        missingDeps = findMissingCommonDepsFromChildren(parentDir, originalContent);
      }

      if (missingDeps.isEmpty()) {
        return FixResult.skipped(violation, "No missing common dependencies identified");
      }

      // Generate dependencyManagement entries for missing deps
      StringBuilder depsXml = new StringBuilder();
      for (String depKey : missingDeps) {
        String[] parts = depKey.split(":");
        CommonDependency commonDep = dependencyConfig.getCommonDependency(depKey);
        String version =
            commonDep != null && commonDep.getVersion() != null ? commonDep.getVersion() : "FIXME";
        depsXml.append("      <dependency>\n");
        depsXml.append("        <groupId>").append(parts[0]).append("</groupId>\n");
        depsXml.append("        <artifactId>").append(parts[1]).append("</artifactId>\n");
        depsXml.append("        <version>").append(version).append("</version>\n");
        if (commonDep != null && commonDep.getScope() != null) {
          depsXml.append("        <scope>").append(commonDep.getScope()).append("</scope>\n");
        }
        depsXml.append("      </dependency>\n");
      }

      String modifiedContent;
      // Check if dependencyManagement exists
      if (originalContent.contains("<dependencyManagement>")) {
        // Add to existing dependencyManagement
        modifiedContent = originalContent.replace("<dependencies>", "<dependencies>\n" + depsXml);
        // Only replace the first occurrence inside dependencyManagement
        int depMgmtStart = originalContent.indexOf("<dependencyManagement>");
        int depsStart = originalContent.indexOf("<dependencies>", depMgmtStart);
        if (depsStart > depMgmtStart) {
          modifiedContent =
              originalContent.substring(0, depsStart + "<dependencies>".length())
                  + "\n"
                  + depsXml
                  + originalContent.substring(depsStart + "<dependencies>".length());
        }
      } else {
        // Create new dependencyManagement section
        String depMgmtSection =
            "\n  <dependencyManagement>\n    <dependencies>\n"
                + depsXml
                + "    </dependencies>\n  </dependencyManagement>\n";
        modifiedContent = addBeforeClosingProject(originalContent, depMgmtSection);
      }

      // Generate diff
      String diff =
          generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomFile),
            "Would add "
                + missingDeps.size()
                + " common dependencies to dependencyManagement: "
                + String.join(", ", missingDeps),
            List.of(diff));
      }

      // VERIFIED WORKFLOW per fixer-workflow.md:
      // 1. BACKUP → 2. WRITE → 3. COMPILE → 4. CLEANUP/ROLLBACK

      // Step 1: Backup
      backup(pomFile, context.projectRoot());

      // Step 2: Write
      writeFile(pomFile, modifiedContent);

      // Step 3: Compile verification
      Path moduleRoot = pomFile.getParent();
      CompileResult compileResult = runMavenCompile(context.projectRoot(), moduleRoot);

      if (!compileResult.success) {
        // Step 4a: Rollback on compile failure
        rollbackOnFailure(List.of(pomFile), context.projectRoot());
        return FixResult.failed(
            violation,
            "Compile verification failed after adding common dependencies. Changes rolled back. "
                + "Error: "
                + compileResult.output);
      }

      // Step 4b: Cleanup on success
      cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

      return FixResult.success(
          violation,
          List.of(pomFile),
          "Added "
              + missingDeps.size()
              + " common dependencies to dependencyManagement. "
              + "Child modules can now inherit these versions without specifying them.",
          List.of(diff));

    } catch (IOException e) {
      rollbackOnFailure(List.of(pomFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to modify pom.xml: " + e.getMessage());
    }
  }

  /**
   * Finds common dependencies used by child modules but missing from parent's dependencyManagement.
   */
  private List<String> findMissingCommonDepsFromChildren(Path parentDir, String parentContent) {
    java.util.Set<String> usedDeps = new java.util.HashSet<>();
    java.util.Set<String> managedDeps = new java.util.HashSet<>();

    // Parse managed dependencies from parent
    Pattern depMgmtPattern =
        Pattern.compile("<dependencyManagement>.*?</dependencyManagement>", Pattern.DOTALL);
    Matcher depMgmtMatcher = depMgmtPattern.matcher(parentContent);
    if (depMgmtMatcher.find()) {
      String depMgmt = depMgmtMatcher.group();
      for (CommonDependency dep : dependencyConfig.getCommonDependencyList()) {
        if (depMgmt.contains("<groupId>" + dep.getGroupId() + "</groupId>")
            && depMgmt.contains("<artifactId>" + dep.getArtifactId() + "</artifactId>")) {
          managedDeps.add(dep.getKey());
        }
      }
    }

    // Scan child modules
    Pattern modulesPattern = Pattern.compile("<module>([^<]+)</module>");
    Matcher modulesMatcher = modulesPattern.matcher(parentContent);
    while (modulesMatcher.find()) {
      String moduleName = modulesMatcher.group(1);
      Path childPom = parentDir.resolve(moduleName).resolve("pom.xml");
      if (Files.exists(childPom)) {
        try {
          String childContent = readFile(childPom);
          for (CommonDependency dep : dependencyConfig.getCommonDependencyList()) {
            if (childContent.contains("<groupId>" + dep.getGroupId() + "</groupId>")
                && childContent.contains("<artifactId>" + dep.getArtifactId() + "</artifactId>")) {
              usedDeps.add(dep.getKey());
            }
          }
        } catch (IOException e) {
          // Ignore errors reading child poms
        }
      }
    }

    // Return used but not managed
    usedDeps.removeAll(managedDeps);
    return new ArrayList<>(usedDeps);
  }

  /**
   * Filters common dependencies to only include those actually used in the POM content.
   *
   * <p>This scans the POM's dependencies section to find which common dependencies are actually
   * being used, then returns only those dependencies. This ensures that modules only get the
   * versions they actually need in their dependencyManagement.
   *
   * @param pomContent the POM file content to scan
   * @param allCommonDeps all available common dependencies from config
   * @return list of common dependencies that are actually used in the POM
   */
  private List<CommonDependency> filterUsedDependencies(
      String pomContent, List<CommonDependency> allCommonDeps) {
    List<CommonDependency> usedDeps = new ArrayList<>();

    // Skip dependencyManagement section to only look at actual dependencies
    String contentWithoutDepMgmt = DEPENDENCY_MANAGEMENT_PATTERN.matcher(pomContent).replaceAll("");

    for (CommonDependency dep : allCommonDeps) {
      // Check if this dependency's groupId and artifactId appear in the POM
      boolean hasGroupId =
          contentWithoutDepMgmt.contains("<groupId>" + dep.getGroupId() + "</groupId>");
      boolean hasArtifactId =
          contentWithoutDepMgmt.contains("<artifactId>" + dep.getArtifactId() + "</artifactId>");

      if (hasGroupId && hasArtifactId) {
        usedDeps.add(dep);
      }
    }

    return usedDeps;
  }

  /** Adds content before the closing </project> tag. */
  private String addBeforeClosingProject(String content, String toAdd) {
    int idx = content.lastIndexOf("</project>");
    if (idx >= 0) {
      return content.substring(0, idx) + toAdd + content.substring(idx);
    }
    return content + toAdd;
  }

  /**
   * Checks if the mvn command is available in the system PATH.
   *
   * @return true if mvn is available
   */
  private boolean isMvnInPath() {
    try {
      ProcessBuilder processBuilder = new ProcessBuilder();
      String os = System.getProperty("os.name").toLowerCase();
      if (os.contains("win")) {
        processBuilder.command("where", "mvn");
      } else {
        processBuilder.command("which", "mvn");
      }
      Process process = processBuilder.start();
      int exitCode = process.waitFor();
      return exitCode == 0;
    } catch (Exception e) {
      return false;
    }
  }

  // ==================== Helper Methods ====================

  private Path resolvePomFile(Path location) {
    if (location == null) {
      return null;
    }
    if (location.toString().endsWith("pom.xml")) {
      return location;
    }
    return location.resolve("pom.xml");
  }

  private String extractValue(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  private String extractMatch(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return matcher.group(0);
    }
    return "";
  }

  private String addAfterModelVersion(String content, String toAdd) {
    // Find </modelVersion> and add after it
    int idx = content.indexOf("</modelVersion>");
    if (idx >= 0) {
      int endIdx = idx + "</modelVersion>".length();
      // Find end of line
      int lineEnd = content.indexOf('\n', endIdx);
      if (lineEnd < 0) {
        lineEnd = endIdx;
      }
      // Add newline after toAdd to preserve XML formatting
      return content.substring(0, lineEnd + 1) + toAdd + "\n" + content.substring(lineEnd + 1);
    }
    return content;
  }

  private String addAfterArtifactId(String content, String toAdd) {
    // Find </artifactId> (first occurrence outside parent) and add after it
    int parentEnd = content.indexOf("</parent>");
    int startSearch = parentEnd > 0 ? parentEnd : 0;

    int idx = content.indexOf("</artifactId>", startSearch);
    if (idx >= 0) {
      int endIdx = idx + "</artifactId>".length();
      int lineEnd = content.indexOf('\n', endIdx);
      if (lineEnd < 0) {
        lineEnd = endIdx;
      }
      return content.substring(0, lineEnd + 1) + toAdd + "\n" + content.substring(lineEnd + 1);
    }
    return content;
  }

  private String deriveParentArtifactId(String submoduleArtifactId) {
    // Remove layer suffix and add -parent
    for (String suffix : new String[] {"-api", "-spi", "-core", "-common", "-facade"}) {
      if (submoduleArtifactId.endsWith(suffix)) {
        String baseName =
            submoduleArtifactId.substring(0, submoduleArtifactId.length() - suffix.length());
        return baseName + "-parent";
      }
    }
    return null;
  }

  private void copyDirectory(Path source, Path target) throws IOException {
    Files.createDirectories(target);
    try (var stream = Files.walk(source)) {
      stream.forEach(
          src -> {
            try {
              Path dest = target.resolve(source.relativize(src));
              if (Files.isDirectory(src)) {
                Files.createDirectories(dest);
              } else {
                Files.copy(src, dest);
              }
            } catch (IOException e) {
              throw new RuntimeException(e);
            }
          });
    }
  }

  private void deleteDirectory(Path dir) throws IOException {
    if (Files.exists(dir)) {
      try (var stream = Files.walk(dir)) {
        stream
            .sorted((a, b) -> b.compareTo(a))
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (IOException e) {
                    // Ignore
                  }
                });
      }
    }
  }

  // ==================== Compile Verification (fixer-workflow.md) ====================

  /**
   * Runs Maven compile to verify changes don't break the build. Per fixer-workflow.md: BACKUP →
   * WRITE → COMPILE → CLEANUP/ROLLBACK
   *
   * <p>If Maven is not available or the project structure doesn't support compilation (e.g., no src
   * directory, test environment), this method returns success=true with a note that compilation was
   * skipped.
   *
   * @param projectRoot the project root directory
   * @param moduleRoot the module being verified
   * @return CompileResult with success status and output
   */
  private CompileResult runMavenCompile(Path projectRoot, Path moduleRoot) {
    // Skip compile verification if this doesn't look like a real Maven project
    // (helps with unit tests that create temp directories without full Maven structure)
    if (!isMavenProjectStructure(moduleRoot)) {
      CompileResult result = new CompileResult();
      result.success = true;
      result.output = "Compile verification skipped: no Maven project structure detected";
      result.skipped = true;
      return result;
    }
    return runMavenCommand(projectRoot, moduleRoot, "compile");
  }

  /**
   * Checks if the directory has a Maven project structure worth compiling.
   *
   * <p>For aggregator modules (pom packaging), we need to compile child modules to verify that
   * pom.xml changes don't break the build.
   */
  private boolean isMavenProjectStructure(Path moduleRoot) {
    Path pomFile = moduleRoot.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return false;
    }
    try {
      String pomContent = readFile(pomFile);
      // For aggregators (packaging=pom), check if there are child modules to compile
      if (pomContent.contains("<packaging>pom</packaging>")) {
        // Check if this aggregator has child modules with source code
        if (pomContent.contains("<modules>")) {
          // Has child modules - should compile them to verify pom changes
          return true;
        }
        // No child modules - check if this aggregator has its own source
        Path srcDir = moduleRoot.resolve("src/main/java");
        return Files.isDirectory(srcDir);
      }
      // Non-pom modules should have src directory
      Path srcDir = moduleRoot.resolve("src/main/java");
      return Files.isDirectory(srcDir);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Runs a Maven command on the specified module.
   *
   * @param projectRoot the project root directory
   * @param moduleRoot the module to run the command on
   * @param goal the Maven goal (compile, test, etc.)
   * @return CompileResult with success status and output
   */
  private CompileResult runMavenCommand(Path projectRoot, Path moduleRoot, String goal) {
    CompileResult result = new CompileResult();
    StringBuilder output = new StringBuilder();

    try {
      Path workDir = projectRoot;
      List<String> command = new ArrayList<>();

      // Try mvnw first, fall back to mvn
      Path mvnw = findMavenWrapper(workDir);
      if (mvnw != null) {
        command.add(mvnw.toString());
      } else if (isMvnInPath()) {
        command.add("mvn");
      } else {
        // No Maven available - skip verification
        result.success = true;
        result.output = "Compile verification skipped: Maven not available";
        result.skipped = true;
        return result;
      }

      // If moduleRoot is different from workDir, use -pl option
      if (!moduleRoot.equals(workDir)) {
        Path relativePath = workDir.relativize(moduleRoot);
        command.add("-pl");
        command.add(relativePath.toString());
        command.add("-am"); // Also make dependencies
      }

      command.add(goal);
      command.add("-q"); // Quiet mode for less output

      ProcessBuilder pb = new ProcessBuilder(command);
      pb.directory(workDir.toFile());
      pb.redirectErrorStream(true);

      Process process = pb.start();

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          output.append(line).append("\n");
        }
      }

      int exitCode = process.waitFor();
      result.success = (exitCode == 0);
      result.output = output.toString();

    } catch (IOException | InterruptedException e) {
      result.success = false;
      result.output = "Failed to run Maven: " + e.getMessage();
    }

    return result;
  }

  /** Finds the Maven wrapper (mvnw) starting from the given directory. */
  private Path findMavenWrapper(Path startDir) {
    Path current = startDir;
    while (current != null) {
      Path mvnw = current.resolve("mvnw");
      if (Files.isExecutable(mvnw)) {
        return mvnw;
      }
      current = current.getParent();
    }
    return null;
  }

  /** Result of a Maven compile/test command. */
  static class CompileResult {
    boolean success;
    String output;
    boolean skipped; // True if verification was skipped (no Maven/no project structure)
  }

  // ==================== MD-016: Cross-Aggregator Dependencies ====================

  /**
   * MD-016: Report cross-aggregator dependencies (not auto-fixable).
   *
   * <p>Cross-aggregator dependencies violate bounded context principles and require manual
   * restructuring. This cannot be auto-fixed because:
   *
   * <ul>
   *   <li>Shared code may need to be moved to a common module within the same domain
   *   <li>The dependency hierarchy may need to be redesigned
   *   <li>API boundaries may need to be redefined
   * </ul>
   *
   * <p>The fix provides guidance on how to resolve the violation manually.
   */
  private FixResult fixReportCrossAggregatorDependencies(
      StructureViolation violation, FixerContext context) {
    // Extract information from the violation to provide helpful guidance
    String found = violation.found();
    String location = violation.location() != null ? violation.location().toString() : "unknown";

    StringBuilder guidance = new StringBuilder();
    guidance.append("Cross-aggregator dependencies require manual restructuring. Options:\n");
    guidance.append("1. Move the shared code to a common module within your domain's aggregator\n");
    guidance.append(
        "2. Extract the required functionality to a separate interface (SPI pattern)\n");
    guidance.append(
        "3. Redesign the module boundaries if the dependency indicates misplaced code\n");
    guidance.append("\n");
    guidance.append("Affected module: ").append(location).append("\n");
    if (found != null && !found.isEmpty()) {
      guidance.append("Cross-domain dependencies: ").append(found).append("\n");
    }
    guidance.append("\nEach aggregator domain (-aggregator) represents a bounded context. ");
    guidance.append(
        "Dependencies should only flow within the same domain or to third-party libraries.");

    return FixResult.notFixable(violation, guidance.toString());
  }

  // ==================== MD-017: Submodule Referencing Aggregator ====================

  /**
   * MD-017: Fix submodule that incorrectly references an -aggregator as parent.
   *
   * <p>Submodules should never reference an -aggregator module as their parent. Aggregator modules
   * are only for grouping modules, not for parent inheritance. Only other -aggregator modules can
   * reference -aggregator.
   *
   * <p>This fix provides guidance on how to resolve the violation:
   *
   * <ul>
   *   <li>Create a proper -parent module for shared configuration
   *   <li>Update the submodule to reference the -parent module instead
   * </ul>
   */
  private FixResult fixSubmoduleReferencingAggregator(
      StructureViolation violation, FixerContext context) {
    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String pomContent = readFile(pomFile);

      // Extract parent artifactId from the pom
      String parentArtifactId = extractParentArtifactId(pomContent);
      String moduleArtifactId = extractProjectArtifactId(pomContent);

      if (parentArtifactId == null) {
        return FixResult.skipped(violation, "No parent reference found in pom.xml");
      }

      // Verify this is actually referencing an -aggregator
      if (!parentArtifactId.endsWith("-aggregator")) {
        return FixResult.skipped(
            violation, "Parent '" + parentArtifactId + "' is not an -aggregator module");
      }

      // Check if this module is itself an aggregator (aggregators CAN reference other aggregators)
      if (moduleArtifactId != null && moduleArtifactId.endsWith("-aggregator")) {
        return FixResult.skipped(
            violation,
            "Module '"
                + moduleArtifactId
                + "' is an aggregator and can reference other aggregators");
      }

      // Provide guidance for fixing
      String baseName =
          parentArtifactId.substring(0, parentArtifactId.length() - "-aggregator".length());
      String suggestedParent = baseName + "-parent";

      StringBuilder guidance = new StringBuilder();
      guidance.append("Submodule incorrectly references an -aggregator as parent.\n\n");
      guidance.append(
          "RULE: -aggregator modules are only for grouping, not for parent inheritance.\n");
      guidance.append("      Submodules should never reference -aggregator as parent.\n\n");
      guidance.append("Affected module: ").append(pomFile).append("\n");
      guidance.append("Current parent: ").append(parentArtifactId).append("\n\n");
      guidance.append("To fix this violation:\n");
      guidance
          .append("1. Create a '")
          .append(suggestedParent)
          .append("' module with shared configuration (dependencyManagement, properties, etc.)\n");
      guidance
          .append("2. Update this module's <parent> to reference '")
          .append(suggestedParent)
          .append("' instead of '")
          .append(parentArtifactId)
          .append("'\n");
      guidance.append(
          "3. The -aggregator should only list modules in <modules>, not provide inheritance");

      return FixResult.notFixable(violation, guidance.toString());

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to read pom.xml: " + e.getMessage());
    }
  }

  /**
   * Extracts the parent's artifactId from pom.xml content.
   *
   * @param pomContent the pom.xml content
   * @return the parent's artifactId, or null if no parent section exists
   */
  private String extractParentArtifactId(String pomContent) {
    Pattern parentPattern =
        Pattern.compile("<parent>.*?<artifactId>([^<]+)</artifactId>.*?</parent>", Pattern.DOTALL);
    Matcher matcher = parentPattern.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  // ==================== MD-015: Create Missing Config Files ====================

  private static final String CONFIG_DIR = "config";
  private static final String BOOTSTRAP_FILE = "bootstrap.yaml";
  private static final String COMMON_DEPS_FILE = "common-dependencies.yaml";

  /** MD-015: Create missing config files (bootstrap.yaml, common-dependencies.yaml). */
  private FixResult fixCreateMissingConfigFiles(
      StructureViolation violation, FixerContext context) {
    try {
      Path location = violation.location();
      if (location == null) {
        return FixResult.skipped(violation, "No location specified");
      }

      // Resolve config directory, avoiding config/config/ nesting
      Path configDir = resolveConfigDir(location);
      List<String> createdFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Create config directory if needed
      if (!Files.exists(configDir)) {
        if (context.dryRun()) {
          diffs.add("+ mkdir " + configDir);
        } else {
          Files.createDirectories(configDir);
        }
      }

      // Create bootstrap.yaml if missing
      Path bootstrapFile = configDir.resolve(BOOTSTRAP_FILE);
      if (!Files.exists(bootstrapFile)) {
        String bootstrapContent = generateBootstrapYaml(location, context);
        if (context.dryRun()) {
          diffs.add("+ " + bootstrapFile);
          diffs.add(bootstrapContent);
        } else {
          Files.writeString(bootstrapFile, bootstrapContent);
        }
        createdFiles.add(BOOTSTRAP_FILE);
      }

      // Create common-dependencies.yaml if missing
      Path commonDepsFile = configDir.resolve(COMMON_DEPS_FILE);
      if (!Files.exists(commonDepsFile)) {
        String commonDepsContent = generateCommonDependenciesYaml();
        if (context.dryRun()) {
          diffs.add("+ " + commonDepsFile);
          diffs.add(commonDepsContent);
        } else {
          Files.writeString(commonDepsFile, commonDepsContent);
        }
        createdFiles.add(COMMON_DEPS_FILE);
      }

      if (createdFiles.isEmpty()) {
        return FixResult.skipped(violation, "All config files already exist");
      }

      String message =
          String.format(
              "Created config files: %s in %s", String.join(", ", createdFiles), configDir);

      List<Path> modifiedFiles = new ArrayList<>();
      modifiedFiles.add(configDir.resolve(BOOTSTRAP_FILE));
      modifiedFiles.add(configDir.resolve(COMMON_DEPS_FILE));

      if (context.dryRun()) {
        return FixResult.dryRun(violation, modifiedFiles, message, diffs);
      }

      return FixResult.success(violation, modifiedFiles, message, diffs);

    } catch (IOException e) {
      return FixResult.failed(violation, "Failed to create config files: " + e.getMessage());
    }
  }

  /**
   * Resolves the config directory path, avoiding nested config/config/ paths.
   *
   * <p>If the location already ends with "config", it is used as-is. Otherwise, "config" is
   * appended to the location.
   *
   * @param location the base location path
   * @return the resolved config directory path
   */
  private Path resolveConfigDir(Path location) {
    String locationName = location.getFileName().toString();
    if (CONFIG_DIR.equals(locationName)) {
      // Location already is the config directory, use it directly
      return location;
    }
    // Append config to the location
    return location.resolve(CONFIG_DIR);
  }

  /** Generates default bootstrap.yaml content. */
  private String generateBootstrapYaml(Path moduleRoot, FixerContext context) {
    // Try to extract namespace and project from pom.xml
    String namespace = context.getNamespace();
    String project = context.getProject();

    if (namespace == null) {
      namespace = "dev.engineeringlab";
    }

    if (project == null || project.isEmpty()) {
      // Try to extract from module directory name
      project =
          moduleRoot.getFileName().toString().replace("-aggregator", "").replace("-parent", "");
    }

    return String.format(
        """
        # SEA Platform Configuration
        # Bootstrap configuration for project initialization
        #
        # Profile activation: -Dsea.profile=dev|int|prod

        # Naming conventions
        naming:
          namespace: %s
          project: %s

        # Code coverage thresholds (0.0 - 1.0)
        jacoco:
          line:
            minimum: 0.80
          branch:
            minimum: 0.60

        # Report configuration for remediation support
        report:
          json:
            enabled: true
            output:
              path: target/compliance-reports/compliance-report.json
          consolidated: true

        # Module structure requirements
        module:
          require:
            api: true
            core: true
            facade: true
            spi: false
        """,
        namespace, project);
  }

  /** Generates default common-dependencies.yaml content. */
  private String generateCommonDependenciesYaml() {
    StringBuilder yaml = new StringBuilder();
    yaml.append(
        """
        # Common Dependencies Configuration (BOM Pattern)
        # ================================================
        #
        # Defines common dependencies that should be managed via dependencyManagement
        # in Parent Aggregator modules (BOM pattern).
        #
        # Rules enforced:
        #   - MD-013: Common deps must NOT have <version> in module pom
        #   - MD-014: Common deps must BE in parent's <dependencyManagement>

        # Common dependencies that must be managed centrally
        commonDependencies:
        """);

    // Add all common dependencies from config
    for (CommonDependency dep : dependencyConfig.getCommonDependencyList()) {
      yaml.append("  - groupId: ").append(dep.getGroupId()).append("\n");
      yaml.append("    artifactId: ").append(dep.getArtifactId()).append("\n");
      if (dep.getVersion() != null) {
        yaml.append("    version: ").append(dep.getVersion()).append("\n");
      }
      if (dep.getScope() != null) {
        yaml.append("    scope: ").append(dep.getScope()).append("\n");
      }
      if (dep.getDescription() != null) {
        yaml.append("    description: \"").append(dep.getDescription()).append("\"\n");
      }
      yaml.append("\n");
    }

    yaml.append(
        """
        # Configuration for auto-BOM generation
        bomGeneration:
          enabled: true
          parentSuffix: "-parent"
          nameTemplate: "{baseName} (Parent Aggregator)"
          descriptionTemplate: "Parent Aggregator providing shared dependency management"
        """);

    return yaml.toString();
  }
}
