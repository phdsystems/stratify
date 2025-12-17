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
 * Remediator for MS-015: Aggregator No Dependencies.
 *
 * <p>Aggregator modules should not have direct dependencies, only dependencyManagement. This fixer
 * moves dependencies from the dependencies section to the dependencyManagement section.
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before
 * &lt;project&gt;
 *     &lt;dependencies&gt;
 *         &lt;dependency&gt;
 *             &lt;groupId&gt;org.example&lt;/groupId&gt;
 *             &lt;artifactId&gt;some-lib&lt;/artifactId&gt;
 *             &lt;version&gt;1.0.0&lt;/version&gt;
 *         &lt;/dependency&gt;
 *     &lt;/dependencies&gt;
 * &lt;/project&gt;
 *
 * // After
 * &lt;project&gt;
 *     &lt;dependencyManagement&gt;
 *         &lt;dependencies&gt;
 *             &lt;dependency&gt;
 *                 &lt;groupId&gt;org.example&lt;/groupId&gt;
 *                 &lt;artifactId&gt;some-lib&lt;/artifactId&gt;
 *                 &lt;version&gt;1.0.0&lt;/version&gt;
 *             &lt;/dependency&gt;
 *         &lt;/dependencies&gt;
 *     &lt;/dependencyManagement&gt;
 * &lt;/project&gt;
 * </pre>
 *
 * @see
 *     dev.engineeringlab.scanner.plugin.scanner.plugin.ms.scanner.core.MS015AggregatorNoDependencies
 * @since 0.2.0
 */
public class MS015AggregatorDepsToManagementRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-015"};
  private static final int PRIORITY = 50;

  // Pattern to match the dependencyManagement section
  private static final Pattern DEP_MGMT_PATTERN =
      Pattern.compile(
          "(<dependencyManagement>\\s*<dependencies>)(.*?)(</dependencies>\\s*</dependencyManagement>)",
          Pattern.DOTALL);

  // Pattern to match standalone dependencies section (not inside dependencyManagement)
  private static final Pattern DEPENDENCIES_SECTION_PATTERN =
      Pattern.compile("(\\s*)<dependencies>(.*?)</dependencies>(\\s*)", Pattern.DOTALL);

  // Pattern to extract individual dependency elements
  private static final Pattern DEPENDENCY_PATTERN =
      Pattern.compile("<dependency>.*?</dependency>", Pattern.DOTALL);

  // Pattern to extract groupId and artifactId from a dependency
  private static final Pattern GROUP_ARTIFACT_PATTERN =
      Pattern.compile(
          "<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);

  public MS015AggregatorDepsToManagementRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS015AggregatorDepsToManagementRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-015 violations: moves dependencies from aggregator pom.xml to dependencyManagement";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-015 violation");
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
      return FixResult.failed(
          violation, "Error moving dependencies to management: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Find dependencyManagement section if it exists
    Matcher depMgmtMatcher = DEP_MGMT_PATTERN.matcher(originalContent);
    boolean hasDepMgmt = depMgmtMatcher.find();

    // Protect dependencyManagement by replacing it with a placeholder
    String placeholder = "___DEPMGMT_PLACEHOLDER___";
    String workingContent = originalContent;
    String depMgmtPrefix = "";
    String depMgmtContent = "";
    String depMgmtSuffix = "";

    if (hasDepMgmt) {
      depMgmtMatcher.reset();
      if (depMgmtMatcher.find()) {
        depMgmtPrefix = depMgmtMatcher.group(1);
        depMgmtContent = depMgmtMatcher.group(2);
        depMgmtSuffix = depMgmtMatcher.group(3);
        workingContent = depMgmtMatcher.replaceFirst(placeholder);
      }
    }

    // Find standalone dependencies section
    Matcher dependenciesMatcher = DEPENDENCIES_SECTION_PATTERN.matcher(workingContent);
    if (!dependenciesMatcher.find()) {
      return FixResult.skipped(violation, "No standalone dependencies section found");
    }

    String leadingWhitespace = dependenciesMatcher.group(1);
    String dependenciesContent = dependenciesMatcher.group(2);

    // Extract dependencies to move
    List<String> depsToMove = new ArrayList<>();
    Matcher depMatcher = DEPENDENCY_PATTERN.matcher(dependenciesContent);
    while (depMatcher.find()) {
      depsToMove.add(depMatcher.group());
    }

    if (depsToMove.isEmpty()) {
      return FixResult.skipped(violation, "No dependencies to move");
    }

    // Log what we're moving
    context.log(
        "Moving %d dependencies to dependencyManagement in %s:",
        depsToMove.size(), pomFile.getFileName());
    for (String dep : depsToMove) {
      String depInfo = extractDependencyInfo(dep);
      if (depInfo != null) {
        context.log("  - %s", depInfo);
      }
    }

    // Build new dependencyManagement content
    StringBuilder newDepMgmtContent = new StringBuilder();
    if (hasDepMgmt && !depMgmtContent.isBlank()) {
      newDepMgmtContent.append(depMgmtContent);
    }
    for (String dep : depsToMove) {
      newDepMgmtContent.append("\n            ").append(dep);
    }

    // Remove the standalone dependencies section
    String modifiedContent = dependenciesMatcher.replaceFirst(leadingWhitespace);

    // Restore or create dependencyManagement section
    if (hasDepMgmt) {
      String newDepMgmt = depMgmtPrefix + newDepMgmtContent + "\n        " + depMgmtSuffix;
      modifiedContent = modifiedContent.replace(placeholder, newDepMgmt);
    } else {
      // Insert new dependencyManagement section before </project>
      String depMgmtSection =
          String.format(
              "\n    <dependencyManagement>\n        <dependencies>%s\n        </dependencies>\n    </dependencyManagement>\n",
              newDepMgmtContent);
      modifiedContent = modifiedContent.replace("</project>", depMgmtSection + "</project>");
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
      context.log(
          "[DRY-RUN] Would move %d dependencies to dependencyManagement", depsToMove.size());
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would move %d dependencies to dependencyManagement (dry-run)",
                  depsToMove.size()))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Moved %d dependencies to dependencyManagement in %s", depsToMove.size(), pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format("Moved %d dependencies to dependencyManagement", depsToMove.size()))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /** Extracts groupId:artifactId from a dependency element. */
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
