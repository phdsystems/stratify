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
 * Remediator for MS-025: Pure Aggregator No Dependencies.
 *
 * <p>Pure aggregator modules (ending with -aggregator suffix) should NOT have a dependencies
 * section in their pom.xml. This fixer removes the entire dependencies section from pure aggregator
 * POMs.
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before
 * &lt;project&gt;
 *     &lt;artifactId&gt;my-module-aggregator&lt;/artifactId&gt;
 *     &lt;packaging&gt;pom&lt;/packaging&gt;
 *     &lt;dependencies&gt;
 *         &lt;dependency&gt;
 *             &lt;groupId&gt;org.example&lt;/groupId&gt;
 *             &lt;artifactId&gt;some-lib&lt;/artifactId&gt;
 *         &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 *     &lt;modules&gt;
 *         &lt;module&gt;sub-module&lt;/module&gt;
 *     &lt;/modules&gt;
 * &lt;/project&gt;
 *
 * // After
 * &lt;project&gt;
 *     &lt;artifactId&gt;my-module-aggregator&lt;/artifactId&gt;
 *     &lt;packaging&gt;pom&lt;/packaging&gt;
 *     &lt;modules&gt;
 *         &lt;module&gt;sub-module&lt;/module&gt;
 *     &lt;/modules&gt;
 * &lt;/project&gt;
 * </pre>
 *
 * <p>The remediator logs which dependencies were removed for transparency. If dependencies should
 * be preserved, they should be moved to:
 *
 * <ul>
 *   <li>dependencyManagement section for version control
 *   <li>Specific submodules that actually need them
 * </ul>
 */
public class MS025PureAggregatorDepsRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-025"};
  // Low priority (15) - removal operations run LAST to avoid breaking
  // modules that depend on inherited dependencies
  private static final int PRIORITY = 15;

  // Pattern to match the dependencyManagement section (to preserve it)
  private static final Pattern DEP_MGMT_PATTERN =
      Pattern.compile("<dependencyManagement>.*?</dependencyManagement>", Pattern.DOTALL);

  // Pattern to match the standalone dependencies section (after dependencyManagement is protected)
  private static final Pattern DEPENDENCIES_PATTERN =
      Pattern.compile("\\s*<dependencies>.*?</dependencies>\\s*", Pattern.DOTALL);

  // Pattern to extract individual dependency elements for logging
  private static final Pattern DEPENDENCY_PATTERN =
      Pattern.compile("<dependency>.*?</dependency>", Pattern.DOTALL);

  // Pattern to extract groupId and artifactId from a dependency
  private static final Pattern GROUP_ARTIFACT_PATTERN =
      Pattern.compile(
          "<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);

  public MS025PureAggregatorDepsRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS025PureAggregatorDepsRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-025 violations: removes dependencies section from pure aggregator pom.xml";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-025 violation");
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
      return FixResult.failed(violation, "Error removing dependencies section: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Protect dependencyManagement by replacing it with a placeholder
    String placeholder = "___DEPMGMT_PLACEHOLDER___";
    Matcher depMgmtMatcher = DEP_MGMT_PATTERN.matcher(originalContent);
    String depMgmtSection = null;
    String workingContent = originalContent;

    if (depMgmtMatcher.find()) {
      depMgmtSection = depMgmtMatcher.group();
      workingContent = depMgmtMatcher.replaceFirst(placeholder);
    }

    // Check if dependencies section exists (outside of dependencyManagement)
    Matcher dependenciesMatcher = DEPENDENCIES_PATTERN.matcher(workingContent);
    if (!dependenciesMatcher.find()) {
      return FixResult.skipped(violation, "No standalone dependencies section found");
    }

    // Extract and log dependencies being removed
    String dependenciesSection = dependenciesMatcher.group();
    List<String> removedDeps = extractDependencies(dependenciesSection);

    // Log what we're removing
    if (!removedDeps.isEmpty()) {
      context.log("Removing %d dependencies from %s:", removedDeps.size(), pomFile.getFileName());
      for (String dep : removedDeps) {
        context.log("  - %s", dep);
      }
    }

    // Remove the dependencies section
    String modifiedContent = dependenciesMatcher.replaceAll("\n");

    // Restore dependencyManagement section if it was present
    if (depMgmtSection != null) {
      modifiedContent = modifiedContent.replace(placeholder, depMgmtSection);
    }

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    List<Path> modifiedFiles = new ArrayList<>();
    modifiedFiles.add(pomFile);

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());
    List<String> diffs = new ArrayList<>();
    diffs.add(diff);

    if (context.dryRun()) {
      context.log("Would remove dependencies section from %s (dry-run)", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would remove %d dependencies from pure aggregator (dry-run)",
                  removedDeps.size()))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Removed dependencies section from %s", pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format("Removed %d dependencies from pure aggregator", removedDeps.size()))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /**
   * Extracts individual dependencies from the dependencies section for logging.
   *
   * @param dependenciesSection the full dependencies section XML
   * @return list of dependency descriptions (groupId:artifactId)
   */
  private List<String> extractDependencies(String dependenciesSection) {
    List<String> deps = new ArrayList<>();
    Matcher depMatcher = DEPENDENCY_PATTERN.matcher(dependenciesSection);

    while (depMatcher.find()) {
      String dependency = depMatcher.group();
      String depInfo = extractDependencyInfo(dependency);
      if (depInfo != null) {
        deps.add(depInfo);
      }
    }

    return deps;
  }

  /**
   * Extracts groupId:artifactId from a dependency element.
   *
   * @param dependency the dependency element XML
   * @return groupId:artifactId string, or null if not found
   */
  private String extractDependencyInfo(String dependency) {
    Matcher matcher = GROUP_ARTIFACT_PATTERN.matcher(dependency);
    if (matcher.find()) {
      String groupId = matcher.group(1).trim();
      String artifactId = matcher.group(2).trim();
      return groupId + ":" + artifactId;
    }
    return null;
  }
}
