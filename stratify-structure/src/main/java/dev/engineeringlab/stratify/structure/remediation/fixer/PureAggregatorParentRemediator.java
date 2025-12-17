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
 * Remediator for MD-001: Pure Aggregators must not have parent element.
 *
 * <p>A Pure Aggregator is a pom with:
 *
 * <ul>
 *   <li>{@code <packaging>pom</packaging>}
 *   <li>{@code <modules>} section
 *   <li>NO {@code <dependencyManagement>} section
 * </ul>
 *
 * <p>This fixer removes the {@code <parent>} element from pure aggregator pom.xml files and makes
 * groupId and version explicit if they were inherited from the parent.
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before
 * &lt;parent&gt;
 *     &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 *     &lt;artifactId&gt;parent&lt;/artifactId&gt;
 *     &lt;version&gt;1.0.0&lt;/version&gt;
 * &lt;/parent&gt;
 *
 * &lt;artifactId&gt;pure-aggregator&lt;/artifactId&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 *
 * &lt;modules&gt;
 *     &lt;module&gt;module-a&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After
 * &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 * &lt;artifactId&gt;pure-aggregator&lt;/artifactId&gt;
 * &lt;version&gt;1.0.0&lt;/version&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 *
 * &lt;modules&gt;
 *     &lt;module&gt;module-a&lt;/module&gt;
 * &lt;/modules&gt;
 * </pre>
 */
public class PureAggregatorParentRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MD-001"};
  private static final int PRIORITY = 70;

  // Pattern to match the entire parent block
  private static final Pattern PARENT_BLOCK_PATTERN =
      Pattern.compile("\\s*<parent>.*?</parent>\\s*", Pattern.DOTALL | Pattern.MULTILINE);

  // Pattern to extract groupId from parent block
  private static final Pattern PARENT_GROUP_ID_PATTERN =
      Pattern.compile("<parent>.*?<groupId>([^<]+)</groupId>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract version from parent block
  private static final Pattern PARENT_VERSION_PATTERN =
      Pattern.compile("<parent>.*?<version>([^<]+)</version>.*?</parent>", Pattern.DOTALL);

  // Pattern to check if groupId exists outside parent block
  private static final Pattern PROJECT_GROUP_ID_PATTERN =
      Pattern.compile("</parent>.*?<groupId>", Pattern.DOTALL);

  // Pattern to check if version exists outside parent block
  private static final Pattern PROJECT_VERSION_PATTERN =
      Pattern.compile("</parent>.*?<version>", Pattern.DOTALL);

  // Pattern to check for dependencyManagement section
  private static final Pattern DEPENDENCY_MANAGEMENT_PATTERN =
      Pattern.compile("<dependencyManagement>", Pattern.DOTALL);

  // Pattern to check for modules section
  private static final Pattern MODULES_PATTERN = Pattern.compile("<modules>", Pattern.DOTALL);

  // Pattern to check for packaging=pom
  private static final Pattern POM_PACKAGING_PATTERN =
      Pattern.compile("<packaging>\\s*pom\\s*</packaging>", Pattern.DOTALL);

  public PureAggregatorParentRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "PureAggregatorParentRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MD-001 violations: removes <parent> element from pure aggregator pom.xml files, "
        + "making groupId and version explicit";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MD-001 violation");
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
      return FixResult.failed(violation, "Error fixing pure aggregator parent: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Verify this is a pure aggregator
    if (!isPureAggregator(originalContent)) {
      return FixResult.skipped(
          violation,
          "Not a pure aggregator (must have packaging=pom, modules, and no dependencyManagement)");
    }

    // Check if there's a parent block
    Matcher parentMatcher = PARENT_BLOCK_PATTERN.matcher(originalContent);
    if (!parentMatcher.find()) {
      return FixResult.skipped(violation, "No <parent> element found in pom.xml");
    }

    // Extract parent groupId and version
    String parentGroupId = extractParentGroupId(originalContent);
    String parentVersion = extractParentVersion(originalContent);

    if (parentGroupId == null || parentGroupId.isEmpty()) {
      return FixResult.failed(violation, "Could not extract groupId from parent block");
    }

    if (parentVersion == null || parentVersion.isEmpty()) {
      return FixResult.failed(violation, "Could not extract version from parent block");
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
      modifiedContent = addGroupIdAfterProject(modifiedContent, parentGroupId);
    }

    // Add explicit version if needed
    if (!hasExplicitVersion) {
      modifiedContent = addVersionAfterArtifactId(modifiedContent, parentVersion);
    }

    // Clean up extra blank lines
    modifiedContent = modifiedContent.replaceAll("\n\\s*\n\\s*\n", "\n\n");

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would remove <parent> from %s and make groupId/version explicit", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description("Would remove <parent> element and make groupId/version explicit (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Removed <parent> from %s and made groupId/version explicit", pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description("Removed <parent> element - groupId and version are now explicit")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }

  /**
   * Checks if the pom.xml is a pure aggregator. A pure aggregator has: - packaging=pom - modules
   * section - NO dependencyManagement section
   */
  private boolean isPureAggregator(String content) {
    boolean hasPomPackaging = POM_PACKAGING_PATTERN.matcher(content).find();
    boolean hasModules = MODULES_PATTERN.matcher(content).find();
    boolean hasDependencyManagement = DEPENDENCY_MANAGEMENT_PATTERN.matcher(content).find();

    return hasPomPackaging && hasModules && !hasDependencyManagement;
  }

  /** Extracts the groupId from the parent block. */
  private String extractParentGroupId(String content) {
    Matcher matcher = PARENT_GROUP_ID_PATTERN.matcher(content);
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

  /** Checks if groupId exists outside the parent block. */
  private boolean hasProjectGroupId(String content) {
    return PROJECT_GROUP_ID_PATTERN.matcher(content).find();
  }

  /** Checks if version exists outside the parent block. */
  private boolean hasProjectVersion(String content) {
    return PROJECT_VERSION_PATTERN.matcher(content).find();
  }

  /**
   * Adds groupId element after the project opening tag. Inserts at the beginning of the project,
   * right after the <?xml> and <project> tags.
   */
  private String addGroupIdAfterProject(String content, String groupId) {
    // Find the <project> tag
    int projectTagEnd = content.indexOf("<project");
    if (projectTagEnd < 0) {
      return content;
    }

    // Find the end of the <project> opening tag
    projectTagEnd = content.indexOf(">", projectTagEnd) + 1;

    // Find the next line with content to determine indentation
    int nextLineStart = projectTagEnd;
    while (nextLineStart < content.length() && content.charAt(nextLineStart) == '\n') {
      nextLineStart++;
    }

    // Determine indentation from the next element
    String indent = "    "; // Default to 4 spaces
    int lineEnd = content.indexOf("\n", nextLineStart);
    if (lineEnd > nextLineStart) {
      String nextLine = content.substring(nextLineStart, lineEnd);
      int indentEnd = 0;
      while (indentEnd < nextLine.length() && Character.isWhitespace(nextLine.charAt(indentEnd))) {
        indentEnd++;
      }
      if (indentEnd > 0) {
        indent = nextLine.substring(0, indentEnd);
      }
    }

    // Insert groupId element
    StringBuilder result = new StringBuilder();
    result.append(content, 0, projectTagEnd);
    result.append("\n");
    result.append(indent).append("<groupId>").append(groupId).append("</groupId>\n");
    result.append(content.substring(projectTagEnd));

    return result.toString();
  }

  /** Adds version element after artifactId. */
  private String addVersionAfterArtifactId(String content, String version) {
    // Find <artifactId> outside of parent block
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    int artifactIdStart = content.indexOf("<artifactId>", searchStart);
    if (artifactIdStart < 0) {
      return content;
    }

    // Find the end of the artifactId closing tag
    int artifactIdEnd = content.indexOf("</artifactId>", artifactIdStart);
    if (artifactIdEnd < 0) {
      return content;
    }
    artifactIdEnd = content.indexOf(">", artifactIdEnd) + 1;

    // Determine indentation from artifactId line
    int lineStart = content.lastIndexOf("\n", artifactIdStart) + 1;
    String indent = "";
    for (int i = lineStart; i < artifactIdStart && Character.isWhitespace(content.charAt(i)); i++) {
      indent += content.charAt(i);
    }

    // Insert version element
    StringBuilder result = new StringBuilder();
    result.append(content, 0, artifactIdEnd);
    result.append("\n");
    result.append(indent).append("<version>").append(version).append("</version>");
    result.append(content.substring(artifactIdEnd));

    return result.toString();
  }
}
