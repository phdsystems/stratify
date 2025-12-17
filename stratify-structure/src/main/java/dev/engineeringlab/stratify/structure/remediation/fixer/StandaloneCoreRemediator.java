package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MD-003: Core modules in common/ should be standalone without parent.
 *
 * <p>Common modules (e.g., -core, -common, -commons, -util, -utils) should be truly reusable and
 * standalone, without inheriting from a project-specific parent. This ensures they remain portable
 * and can be shared across different projects.
 *
 * <p>This fixer performs the following transformations:
 *
 * <ol>
 *   <li>Removes the &lt;parent&gt; element from the pom.xml
 *   <li>Extracts dependency versions currently used in the module
 *   <li>Adds a &lt;dependencyManagement&gt; section with explicit versions
 *   <li>Uses common default versions for standard dependencies
 * </ol>
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
 * &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 * &lt;artifactId&gt;common-core&lt;/artifactId&gt;
 *
 * &lt;dependencies&gt;
 *     &lt;dependency&gt;
 *         &lt;groupId&gt;org.slf4j&lt;/groupId&gt;
 *         &lt;artifactId&gt;slf4j-api&lt;/artifactId&gt;
 *     &lt;/dependency&gt;
 * &lt;/dependencies&gt;
 *
 * // After
 * &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 * &lt;artifactId&gt;common-core&lt;/artifactId&gt;
 * &lt;version&gt;1.0.0&lt;/version&gt;
 *
 * &lt;dependencyManagement&gt;
 *     &lt;dependencies&gt;
 *         &lt;dependency&gt;
 *             &lt;groupId&gt;org.slf4j&lt;/groupId&gt;
 *             &lt;artifactId&gt;slf4j-api&lt;/artifactId&gt;
 *             &lt;version&gt;2.0.9&lt;/version&gt;
 *         &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 * &lt;/dependencyManagement&gt;
 *
 * &lt;dependencies&gt;
 *     &lt;dependency&gt;
 *         &lt;groupId&gt;org.slf4j&lt;/groupId&gt;
 *         &lt;artifactId&gt;slf4j-api&lt;/artifactId&gt;
 *     &lt;/dependency&gt;
 * &lt;/dependencies&gt;
 * </pre>
 */
public class StandaloneCoreRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MD-003"};
  private static final int PRIORITY = 65;

  // Pattern to match the entire <parent>...</parent> block
  private static final Pattern PARENT_PATTERN =
      Pattern.compile("\\s*<parent>.*?</parent>\\s*", Pattern.DOTALL | Pattern.MULTILINE);

  // Pattern to extract version from parent block
  private static final Pattern PARENT_VERSION_PATTERN =
      Pattern.compile("<parent>.*?<version>([^<]+)</version>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract groupId from parent block
  private static final Pattern PARENT_GROUP_ID_PATTERN =
      Pattern.compile("<parent>.*?<groupId>([^<]+)</groupId>.*?</parent>", Pattern.DOTALL);

  // Pattern to match dependencies section
  private static final Pattern DEPENDENCIES_PATTERN =
      Pattern.compile("<dependencies>(.*?)</dependencies>", Pattern.DOTALL);

  // Pattern to extract individual dependency elements
  private static final Pattern DEPENDENCY_PATTERN =
      Pattern.compile(
          "<dependency>\\s*"
              + "<groupId>([^<]+)</groupId>\\s*"
              + "<artifactId>([^<]+)</artifactId>\\s*"
              + "(?:<version>([^<]+)</version>\\s*)?"
              + "(?:<scope>([^<]+)</scope>\\s*)?"
              + ".*?</dependency>",
          Pattern.DOTALL);

  // Common dependency versions (defaults)
  private static final Map<String, String> DEFAULT_VERSIONS = new LinkedHashMap<>();

  static {
    DEFAULT_VERSIONS.put("org.aspectj:aspectjrt", "1.9.22.1");
    DEFAULT_VERSIONS.put("org.slf4j:slf4j-api", "2.0.9");
    DEFAULT_VERSIONS.put("org.junit.jupiter:junit-jupiter", "5.10.2");
    DEFAULT_VERSIONS.put("org.assertj:assertj-core", "3.25.3");
    DEFAULT_VERSIONS.put("org.projectlombok:lombok", "1.18.30");
    DEFAULT_VERSIONS.put("ch.qos.logback:logback-classic", "1.4.14");
    DEFAULT_VERSIONS.put("org.mockito:mockito-core", "5.10.0");
    DEFAULT_VERSIONS.put("org.mockito:mockito-junit-jupiter", "5.10.0");
  }

  public StandaloneCoreRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "StandaloneCoreRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MD-003 violations: removes <parent> element from common/core modules "
        + "and adds <dependencyManagement> with explicit versions to make them standalone";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MD-003 violation");
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
      return FixResult.failed(violation, "Error making module standalone: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws IOException {
    String originalContent = readFile(pomFile);

    // Check if there's a parent block
    Matcher parentMatcher = PARENT_PATTERN.matcher(originalContent);
    if (!parentMatcher.find()) {
      return FixResult.skipped(violation, "No <parent> element found in pom.xml");
    }

    // Extract information from parent before removing it
    String parentVersion = extractParentVersion(originalContent);
    String parentGroupId = extractParentGroupId(originalContent);

    // Extract dependencies and their versions
    Map<String, DependencyInfo> dependencies = extractDependencies(originalContent);

    // Build modified content
    String modifiedContent =
        transformPom(originalContent, parentVersion, parentGroupId, dependencies);

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would remove <parent> and add <dependencyManagement> to %s", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Would convert module to standalone (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Converted module to standalone: %s", pomFile);
    context.log(
        "Removed <parent> and added <dependencyManagement> with %d dependencies",
        dependencies.size());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            "Removed <parent> element and added <dependencyManagement> to make module standalone")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }

  /** Transforms the POM by removing parent and adding dependencyManagement. */
  private String transformPom(
      String content,
      String parentVersion,
      String parentGroupId,
      Map<String, DependencyInfo> dependencies) {
    String result = content;

    // Step 1: Check if version needs to be added (after removing parent)
    boolean needsVersion = !hasExplicitVersion(content);
    boolean needsGroupId = !hasExplicitGroupId(content);

    // Step 2: Remove parent element
    result = PARENT_PATTERN.matcher(result).replaceAll("\n");

    // Step 3: Add version if needed (insert after artifactId)
    if (needsVersion && parentVersion != null) {
      result = addVersionAfterArtifactId(result, parentVersion);
    }

    // Step 4: Add groupId if needed (insert before artifactId)
    if (needsGroupId && parentGroupId != null) {
      result = addGroupIdBeforeArtifactId(result, parentGroupId);
    }

    // Step 5: Add dependencyManagement section (before dependencies section)
    if (!dependencies.isEmpty()) {
      result = addDependencyManagement(result, dependencies);
    }

    // Step 6: Clean up extra blank lines
    result = result.replaceAll("\n{3,}", "\n\n");

    return result;
  }

  /** Checks if the POM already has an explicit version (outside parent block). */
  private boolean hasExplicitVersion(String content) {
    int parentEnd = content.indexOf("</parent>");
    if (parentEnd < 0) {
      return content.contains("<version>");
    }
    String afterParent = content.substring(parentEnd);
    return afterParent.contains("<version>");
  }

  /** Checks if the POM already has an explicit groupId (outside parent block). */
  private boolean hasExplicitGroupId(String content) {
    int parentEnd = content.indexOf("</parent>");
    if (parentEnd < 0) {
      return content.contains("<groupId>");
    }
    String afterParent = content.substring(parentEnd);
    int artifactIdPos = afterParent.indexOf("<artifactId>");
    if (artifactIdPos > 0) {
      String beforeArtifact = afterParent.substring(0, artifactIdPos);
      return beforeArtifact.contains("<groupId>");
    }
    return false;
  }

  /** Adds version element after artifactId. */
  private String addVersionAfterArtifactId(String content, String version) {
    // Find the first artifactId after where parent was (outside dependencies)
    int projectStart = content.indexOf("<project");
    int dependenciesStart = content.indexOf("<dependencies>");

    // Look for artifactId between project and dependencies
    String searchArea =
        dependenciesStart > 0 ? content.substring(projectStart, dependenciesStart) : content;

    Pattern artifactIdPattern = Pattern.compile("(<artifactId>[^<]+</artifactId>)");
    Matcher matcher = artifactIdPattern.matcher(searchArea);

    if (matcher.find()) {
      int insertPos = projectStart + matcher.end();
      String indent = extractIndentation(content, projectStart + matcher.start());
      String versionElement = "\n" + indent + "<version>" + version + "</version>";
      return content.substring(0, insertPos) + versionElement + content.substring(insertPos);
    }

    return content;
  }

  /** Adds groupId element before artifactId. */
  private String addGroupIdBeforeArtifactId(String content, String groupId) {
    int projectStart = content.indexOf("<project");
    int dependenciesStart = content.indexOf("<dependencies>");

    String searchArea =
        dependenciesStart > 0 ? content.substring(projectStart, dependenciesStart) : content;

    Pattern artifactIdPattern = Pattern.compile("(<artifactId>[^<]+</artifactId>)");
    Matcher matcher = artifactIdPattern.matcher(searchArea);

    if (matcher.find()) {
      int insertPos = projectStart + matcher.start();
      String indent = extractIndentation(content, insertPos);
      String groupIdElement = indent + "<groupId>" + groupId + "</groupId>\n";
      return content.substring(0, insertPos) + groupIdElement + content.substring(insertPos);
    }

    return content;
  }

  /** Extracts indentation from a line containing the given position. */
  private String extractIndentation(String content, int position) {
    int lineStart = content.lastIndexOf("\n", position) + 1;
    StringBuilder indent = new StringBuilder();
    for (int i = lineStart; i < position && i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == ' ' || c == '\t') {
        indent.append(c);
      } else {
        break;
      }
    }
    return indent.toString();
  }

  /** Adds dependencyManagement section before dependencies section. */
  private String addDependencyManagement(String content, Map<String, DependencyInfo> dependencies) {
    int dependenciesPos = content.indexOf("<dependencies>");
    if (dependenciesPos < 0) {
      // No dependencies section, add before closing project tag
      int projectEnd = content.lastIndexOf("</project>");
      if (projectEnd > 0) {
        dependenciesPos = projectEnd;
      } else {
        return content; // Can't find where to insert
      }
    }

    // Build dependencyManagement section
    String indent = extractIndentation(content, dependenciesPos);
    StringBuilder depMgmt = new StringBuilder();

    depMgmt.append("\n").append(indent).append("<dependencyManagement>\n");
    depMgmt.append(indent).append("    <dependencies>\n");

    for (DependencyInfo dep : dependencies.values()) {
      depMgmt.append(indent).append("        <dependency>\n");
      depMgmt
          .append(indent)
          .append("            <groupId>")
          .append(dep.groupId)
          .append("</groupId>\n");
      depMgmt
          .append(indent)
          .append("            <artifactId>")
          .append(dep.artifactId)
          .append("</artifactId>\n");
      depMgmt
          .append(indent)
          .append("            <version>")
          .append(dep.version)
          .append("</version>\n");
      if (dep.scope != null && !dep.scope.isEmpty() && !"compile".equals(dep.scope)) {
        depMgmt.append(indent).append("            <scope>").append(dep.scope).append("</scope>\n");
      }
      depMgmt.append(indent).append("        </dependency>\n");
    }

    depMgmt.append(indent).append("    </dependencies>\n");
    depMgmt.append(indent).append("</dependencyManagement>\n");

    return content.substring(0, dependenciesPos) + depMgmt + content.substring(dependenciesPos);
  }

  /** Extracts parent version from POM content. */
  private String extractParentVersion(String content) {
    Matcher matcher = PARENT_VERSION_PATTERN.matcher(content);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /** Extracts parent groupId from POM content. */
  private String extractParentGroupId(String content) {
    Matcher matcher = PARENT_GROUP_ID_PATTERN.matcher(content);
    return matcher.find() ? matcher.group(1).trim() : null;
  }

  /**
   * Extracts all dependencies and their versions. Uses default versions for dependencies without
   * explicit versions.
   */
  private Map<String, DependencyInfo> extractDependencies(String content) {
    Map<String, DependencyInfo> deps = new LinkedHashMap<>();

    Matcher depsSectionMatcher = DEPENDENCIES_PATTERN.matcher(content);
    if (!depsSectionMatcher.find()) {
      return deps;
    }

    String depsSection = depsSectionMatcher.group(1);
    Matcher depMatcher = DEPENDENCY_PATTERN.matcher(depsSection);

    while (depMatcher.find()) {
      String groupId = depMatcher.group(1).trim();
      String artifactId = depMatcher.group(2).trim();
      String version = depMatcher.group(3);
      String scope = depMatcher.group(4);

      // If version is not specified, look it up in defaults
      if (version == null || version.trim().isEmpty()) {
        String key = groupId + ":" + artifactId;
        version = DEFAULT_VERSIONS.get(key);

        // If still no version found, use a placeholder
        if (version == null) {
          version = "LATEST"; // Will need manual fixing
        }
      } else {
        version = version.trim();
      }

      String key = groupId + ":" + artifactId;
      deps.put(
          key,
          new DependencyInfo(groupId, artifactId, version, scope != null ? scope.trim() : null));
    }

    return deps;
  }

  /** Simple structure to hold dependency information. */
  private static class DependencyInfo {
    final String groupId;
    final String artifactId;
    final String version;
    final String scope;

    DependencyInfo(String groupId, String artifactId, String version, String scope) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.scope = scope;
    }
  }
}
