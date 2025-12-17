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
 * Remediator for MS-026: Pure Aggregator No Dependency Management.
 *
 * <p>Removes the {@code <dependencyManagement>} section from pure aggregator pom.xml files. Pure
 * aggregators should only contain a {@code <modules>} section to organize submodules, not manage
 * dependencies.
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before
 * &lt;project&gt;
 *     &lt;artifactId&gt;my-aggregator&lt;/artifactId&gt;
 *     &lt;packaging&gt;pom&lt;/packaging&gt;
 *
 *     &lt;dependencyManagement&gt;
 *         &lt;dependencies&gt;
 *             &lt;dependency&gt;...&lt;/dependency&gt;
 *         &lt;/dependencies&gt;
 *     &lt;/dependencyManagement&gt;
 *
 *     &lt;modules&gt;
 *         &lt;module&gt;module-a&lt;/module&gt;
 *     &lt;/modules&gt;
 * &lt;/project&gt;
 *
 * // After
 * &lt;project&gt;
 *     &lt;artifactId&gt;my-aggregator&lt;/artifactId&gt;
 *     &lt;packaging&gt;pom&lt;/packaging&gt;
 *
 *     &lt;modules&gt;
 *         &lt;module&gt;module-a&lt;/module&gt;
 *     &lt;/modules&gt;
 * &lt;/project&gt;
 * </pre>
 */
public class MS026PureAggregatorDepMgmtRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-026"};
  // Low priority (10) - removal operations run LAST to avoid breaking
  // modules that depend on inherited dependencyManagement
  private static final int PRIORITY = 10;

  // Pattern to match the entire dependencyManagement block
  private static final Pattern DEPENDENCY_MANAGEMENT_PATTERN =
      Pattern.compile(
          "\\s*<dependencyManagement>.*?</dependencyManagement>\\s*",
          Pattern.DOTALL | Pattern.MULTILINE);

  public MS026PureAggregatorDepMgmtRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS026PureAggregatorDepMgmtRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-026 violations: removes <dependencyManagement> section from pure aggregator pom.xml files";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-026 violation");
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
      return FixResult.failed(violation, "Error removing dependencyManagement: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Check if there's a dependencyManagement block
    Matcher matcher = DEPENDENCY_MANAGEMENT_PATTERN.matcher(originalContent);
    if (!matcher.find()) {
      return FixResult.skipped(violation, "No <dependencyManagement> element found in pom.xml");
    }

    // Remove dependencyManagement block
    String modifiedContent = matcher.replaceAll("\n");

    // Clean up extra blank lines (more than 2 consecutive blank lines)
    modifiedContent = modifiedContent.replaceAll("\n\\s*\n\\s*\n\\s*\n", "\n\n\n");

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would remove <dependencyManagement> from %s", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description("Would remove <dependencyManagement> section (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Removed <dependencyManagement> from %s", pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description("Removed <dependencyManagement> section from pure aggregator")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }
}
