package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-013: Parent Modules Should Not Have Parent.
 *
 * <p>Parent modules (artifactId ending with -parent) should be standalone without declaring a
 * parent POM to maintain a flat module hierarchy.
 *
 * <p>This fixer:
 *
 * <ol>
 *   <li>Removes the parent element from parent modules
 *   <li>Makes groupId and version explicit
 *   <li>Copies dependencyManagement from the parent POM
 *   <li>Copies properties from the parent POM
 *   <li>Copies build/pluginManagement from the parent POM
 * </ol>
 *
 * <p>This ensures the module remains buildable after becoming standalone.
 */
public class StandaloneParentRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-013"};
  private static final int PRIORITY = 70;

  // Pattern to match the entire parent block
  private static final Pattern PARENT_BLOCK_PATTERN =
      Pattern.compile("\\s*<parent>.*?</parent>\\s*", Pattern.DOTALL | Pattern.MULTILINE);

  // Pattern to extract groupId from parent block
  private static final Pattern PARENT_GROUP_ID_PATTERN =
      Pattern.compile("<parent>.*?<groupId>([^<]+)</groupId>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract artifactId from parent block
  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile("<parent>.*?<artifactId>([^<]+)</artifactId>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract version from parent block
  private static final Pattern PARENT_VERSION_PATTERN =
      Pattern.compile("<parent>.*?<version>([^<]+)</version>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract relativePath from parent block
  private static final Pattern PARENT_RELATIVE_PATH_PATTERN =
      Pattern.compile(
          "<parent>.*?<relativePath>([^<]*)</relativePath>.*?</parent>", Pattern.DOTALL);

  // Pattern to check if groupId exists outside parent block
  private static final Pattern PROJECT_GROUP_ID_PATTERN =
      Pattern.compile("</parent>.*?<groupId>", Pattern.DOTALL);

  // Pattern to check if version exists outside parent block
  private static final Pattern PROJECT_VERSION_PATTERN =
      Pattern.compile("</parent>.*?<version>", Pattern.DOTALL);

  // Pattern to extract properties block
  private static final Pattern PROPERTIES_PATTERN =
      Pattern.compile("(\\s*<properties>.*?</properties>)", Pattern.DOTALL);

  // Pattern to extract dependencyManagement block
  private static final Pattern DEPENDENCY_MANAGEMENT_PATTERN =
      Pattern.compile("(\\s*<dependencyManagement>.*?</dependencyManagement>)", Pattern.DOTALL);

  // Pattern to extract build block
  private static final Pattern BUILD_PATTERN =
      Pattern.compile("(\\s*<build>.*?</build>)", Pattern.DOTALL);

  // Pattern to extract pluginManagement from build block
  private static final Pattern PLUGIN_MANAGEMENT_PATTERN =
      Pattern.compile("(<pluginManagement>.*?</pluginManagement>)", Pattern.DOTALL);

  // Pattern to find </project> tag
  private static final Pattern PROJECT_END_PATTERN =
      Pattern.compile("(\\s*</project>)", Pattern.DOTALL);

  public StandaloneParentRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "StandaloneParentRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-013 violations: removes <parent> element from parent modules, "
        + "copies dependencyManagement, properties, and pluginManagement from parent POM";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-013 violation");
    }

    Path location = violation.location();
    if (location == null) {
      return FixResult.skipped(violation, "No location specified");
    }

    // If location is a directory, look for pom.xml in it
    Path pomFile = location;
    if (Files.isDirectory(location)) {
      pomFile = location.resolve("pom.xml");
    }

    if (!pomFile.toString().endsWith("pom.xml")) {
      return FixResult.skipped(violation, "Not a pom.xml file: " + pomFile);
    }

    if (!Files.exists(pomFile)) {
      return FixResult.skipped(violation, "File not found: " + pomFile);
    }

    try {
      return fixPomFile(pomFile, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error fixing standalone parent: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Check if there's a parent block
    Matcher parentMatcher = PARENT_BLOCK_PATTERN.matcher(originalContent);
    if (!parentMatcher.find()) {
      return FixResult.skipped(violation, "No <parent> element found in pom.xml");
    }

    // Extract parent information
    String parentGroupId = extractParentGroupId(originalContent);
    String parentArtifactId = extractParentArtifactId(originalContent);
    String parentVersion = extractParentVersion(originalContent);
    String relativePath = extractRelativePath(originalContent);

    if (parentGroupId == null || parentGroupId.isEmpty()) {
      return FixResult.failed(violation, "Could not extract groupId from parent block");
    }

    if (parentVersion == null || parentVersion.isEmpty()) {
      return FixResult.failed(violation, "Could not extract version from parent block");
    }

    // Try to read parent POM to extract inherited configurations
    String parentPomContent = null;
    Path parentPomPath = resolveParentPom(pomFile, relativePath, parentArtifactId, context);
    if (parentPomPath != null && Files.exists(parentPomPath)) {
      try {
        parentPomContent = readFile(parentPomPath);
      } catch (Exception e) {
        context.log("Warning: Could not read parent POM at %s: %s", parentPomPath, e.getMessage());
      }
    }

    // Check if groupId and version are already explicit
    boolean hasExplicitGroupId = hasProjectGroupId(originalContent);
    boolean hasExplicitVersion = hasProjectVersion(originalContent);

    // Build modified content
    String modifiedContent = originalContent;

    // Remove parent block
    parentMatcher.reset();
    modifiedContent = parentMatcher.replaceAll("\n");

    // Add explicit groupId if needed
    if (!hasExplicitGroupId) {
      modifiedContent = addGroupIdAfterModelVersion(modifiedContent, parentGroupId);
    }

    // Add explicit version if needed
    if (!hasExplicitVersion) {
      modifiedContent = addVersionAfterArtifactId(modifiedContent, parentVersion);
    }

    // Copy configurations from parent POM if available and not already present
    if (parentPomContent != null) {
      modifiedContent = copyInheritedConfigurations(modifiedContent, parentPomContent, context);
    }

    // Clean up extra blank lines
    modifiedContent = modifiedContent.replaceAll("\n\\s*\n\\s*\n", "\n\n");

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would remove <parent> from %s and copy inherited configurations", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description("Would remove <parent> element and copy inherited configurations (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Removed <parent> from %s and copied inherited configurations", pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description("Removed <parent> element and copied inherited configurations")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }

  /** Resolves the path to the parent POM file. */
  private Path resolveParentPom(
      Path childPom, String relativePath, String parentArtifactId, FixerContext context) {
    Path childDir = childPom.getParent();

    // If relativePath is specified, use it
    if (relativePath != null && !relativePath.isEmpty()) {
      Path resolved = childDir.resolve(relativePath);
      if (Files.isDirectory(resolved)) {
        resolved = resolved.resolve("pom.xml");
      }
      if (Files.exists(resolved)) {
        return resolved;
      }
    }

    // Default: look in parent directory
    Path defaultParentPom = childDir.resolve("../pom.xml").normalize();
    if (Files.exists(defaultParentPom)) {
      return defaultParentPom;
    }

    // Try project root
    if (context.projectRoot() != null) {
      Path rootPom = context.projectRoot().resolve("pom.xml");
      if (Files.exists(rootPom)) {
        return rootPom;
      }
    }

    return null;
  }

  /**
   * Copies inherited configurations (properties, dependencyManagement, pluginManagement) from
   * parent POM to the target POM.
   */
  private String copyInheritedConfigurations(
      String targetContent, String parentContent, FixerContext context) {
    StringBuilder additions = new StringBuilder();

    // Check and copy properties if not already present
    if (!targetContent.contains("<properties>")) {
      String parentProperties = extractBlock(parentContent, PROPERTIES_PATTERN);
      if (parentProperties != null && !parentProperties.trim().isEmpty()) {
        additions.append("\n").append(parentProperties);
        context.log("Copying properties from parent POM");
      }
    }

    // Check and copy dependencyManagement if not already present
    if (!targetContent.contains("<dependencyManagement>")) {
      String parentDepMgmt = extractBlock(parentContent, DEPENDENCY_MANAGEMENT_PATTERN);
      if (parentDepMgmt != null && !parentDepMgmt.trim().isEmpty()) {
        additions.append("\n").append(parentDepMgmt);
        context.log("Copying dependencyManagement from parent POM");
      }
    }

    // Check and copy build/pluginManagement if not already present
    if (!targetContent.contains("<pluginManagement>")) {
      String parentPluginMgmt = extractPluginManagement(parentContent);
      if (parentPluginMgmt != null && !parentPluginMgmt.trim().isEmpty()) {
        if (targetContent.contains("<build>")) {
          // Insert pluginManagement inside existing build block
          targetContent = insertPluginManagementIntoBuild(targetContent, parentPluginMgmt);
          context.log("Adding pluginManagement to existing build block");
        } else {
          // Add entire build block with pluginManagement
          additions
              .append("\n    <build>\n        <pluginManagement>\n            ")
              .append(parentPluginMgmt.trim())
              .append("\n        </pluginManagement>\n    </build>");
          context.log("Copying build/pluginManagement from parent POM");
        }
      }
    }

    // Insert additions before </project>
    if (additions.length() > 0) {
      Matcher endMatcher = PROJECT_END_PATTERN.matcher(targetContent);
      if (endMatcher.find()) {
        targetContent =
            targetContent.substring(0, endMatcher.start())
                + additions.toString()
                + targetContent.substring(endMatcher.start());
      }
    }

    return targetContent;
  }

  /** Extracts a block from content using the given pattern. */
  private String extractBlock(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  /** Extracts pluginManagement from the build block. */
  private String extractPluginManagement(String content) {
    Matcher matcher = PLUGIN_MANAGEMENT_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1);
    }
    return null;
  }

  /** Inserts pluginManagement into an existing build block. */
  private String insertPluginManagementIntoBuild(String content, String pluginManagement) {
    int buildStart = content.indexOf("<build>");
    if (buildStart < 0) {
      return content;
    }
    int insertPoint = content.indexOf(">", buildStart) + 1;

    return content.substring(0, insertPoint)
        + "\n        <pluginManagement>\n            "
        + pluginManagement.trim()
        + "\n        </pluginManagement>"
        + content.substring(insertPoint);
  }

  /** Extracts the groupId from the parent block. */
  private String extractParentGroupId(String content) {
    Matcher matcher = PARENT_GROUP_ID_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the artifactId from the parent block. */
  private String extractParentArtifactId(String content) {
    Matcher matcher = PARENT_ARTIFACT_ID_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the version from the parent block. */
  private String extractParentVersion(String content) {
    Matcher matcher = PARENT_VERSION_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the relativePath from the parent block. */
  private String extractRelativePath(String content) {
    Matcher matcher = PARENT_RELATIVE_PATH_PATTERN.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Checks if groupId exists outside the parent block. */
  private boolean hasProjectGroupId(String content) {
    return PROJECT_GROUP_ID_PATTERN.matcher(content).find();
  }

  /** Checks if version exists outside the parent block. */
  private boolean hasProjectVersion(String content) {
    return PROJECT_VERSION_PATTERN.matcher(content).find();
  }

  /** Adds groupId element after modelVersion. */
  private String addGroupIdAfterModelVersion(String content, String groupId) {
    // Find </modelVersion>
    int modelVersionEnd = content.indexOf("</modelVersion>");
    if (modelVersionEnd < 0) {
      return addGroupIdAfterProject(content, groupId);
    }
    modelVersionEnd = content.indexOf(">", modelVersionEnd) + 1;

    // Determine indentation
    String indent = "    ";
    int lineStart = content.lastIndexOf("\n", modelVersionEnd - 15) + 1;
    for (int i = lineStart; i < content.indexOf("<", lineStart); i++) {
      if (Character.isWhitespace(content.charAt(i))) {
        indent = content.substring(lineStart, i + 1);
        break;
      }
    }

    return content.substring(0, modelVersionEnd)
        + "\n\n"
        + indent
        + "<groupId>"
        + groupId
        + "</groupId>"
        + content.substring(modelVersionEnd);
  }

  /** Adds groupId element after the project opening tag (fallback). */
  private String addGroupIdAfterProject(String content, String groupId) {
    int projectTagEnd = content.indexOf("<project");
    if (projectTagEnd < 0) {
      return content;
    }
    projectTagEnd = content.indexOf(">", projectTagEnd) + 1;

    return content.substring(0, projectTagEnd)
        + "\n    <groupId>"
        + groupId
        + "</groupId>"
        + content.substring(projectTagEnd);
  }

  /** Adds version element after artifactId. */
  private String addVersionAfterArtifactId(String content, String version) {
    // Find <artifactId> outside of parent block
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    int artifactIdStart = content.indexOf("<artifactId>", searchStart);
    if (artifactIdStart < 0) {
      // No parent block found, search from beginning
      artifactIdStart = content.indexOf("<artifactId>");
      if (artifactIdStart < 0) {
        return content;
      }
    }

    int artifactIdEnd = content.indexOf("</artifactId>", artifactIdStart);
    if (artifactIdEnd < 0) {
      return content;
    }
    artifactIdEnd = content.indexOf(">", artifactIdEnd) + 1;

    // Determine indentation
    int lineStart = content.lastIndexOf("\n", artifactIdStart) + 1;
    StringBuilder indent = new StringBuilder();
    for (int i = lineStart; i < artifactIdStart && Character.isWhitespace(content.charAt(i)); i++) {
      indent.append(content.charAt(i));
    }

    return content.substring(0, artifactIdEnd)
        + "\n"
        + indent
        + "<version>"
        + version
        + "</version>"
        + content.substring(artifactIdEnd);
  }
}
