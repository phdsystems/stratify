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
 * Remediator for PH-001: Modules must not use custom relativePath values.
 *
 * <p>Each module should only reference its immediate parent (one level up). Custom relativePath
 * values like '../../pom.xml' indicate modules are skipping hierarchy levels.
 *
 * <p>This fixer removes the relativePath element, allowing Maven to use the default behavior
 * (../pom.xml).
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before
 * &lt;parent&gt;
 *     &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 *     &lt;artifactId&gt;parent&lt;/artifactId&gt;
 *     &lt;version&gt;1.0.0&lt;/version&gt;
 *     &lt;relativePath&gt;../../pom.xml&lt;/relativePath&gt;
 * &lt;/parent&gt;
 *
 * // After
 * &lt;parent&gt;
 *     &lt;groupId&gt;dev.engineeringlab&lt;/groupId&gt;
 *     &lt;artifactId&gt;parent&lt;/artifactId&gt;
 *     &lt;version&gt;1.0.0&lt;/version&gt;
 * &lt;/parent&gt;
 * </pre>
 */
public class RelativePathRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"PH-001"};
  private static final int PRIORITY = 70;

  // Pattern to match <relativePath>...</relativePath> including whitespace
  private static final Pattern RELATIVE_PATH_PATTERN =
      Pattern.compile("\\s*<relativePath>[^<]*</relativePath>\\s*", Pattern.MULTILINE);

  // Pattern to match <relativePath/> (empty element)
  private static final Pattern RELATIVE_PATH_EMPTY_PATTERN =
      Pattern.compile("\\s*<relativePath\\s*/>\\s*", Pattern.MULTILINE);

  public RelativePathRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "RelativePathRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes PH-001 violations: removes custom <relativePath> elements from pom.xml, "
        + "allowing Maven to use the default parent resolution (../pom.xml)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not a PH-001 violation");
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
      return FixResult.failed(violation, "Error fixing relativePath: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Check if there's a custom relativePath
    Matcher matcher = RELATIVE_PATH_PATTERN.matcher(originalContent);
    Matcher emptyMatcher = RELATIVE_PATH_EMPTY_PATTERN.matcher(originalContent);

    boolean hasCustomRelativePath = matcher.find();
    boolean hasEmptyRelativePath = emptyMatcher.find();

    if (!hasCustomRelativePath && !hasEmptyRelativePath) {
      return FixResult.skipped(violation, "No <relativePath> element found in pom.xml");
    }

    // Reset matchers for replacement
    matcher.reset();
    emptyMatcher.reset();

    // Remove relativePath element
    String modifiedContent = originalContent;

    // Remove <relativePath>...</relativePath>
    modifiedContent = RELATIVE_PATH_PATTERN.matcher(modifiedContent).replaceAll("\n");

    // Remove <relativePath/>
    modifiedContent = RELATIVE_PATH_EMPTY_PATTERN.matcher(modifiedContent).replaceAll("\n");

    // Clean up extra blank lines that may result
    modifiedContent = modifiedContent.replaceAll("\n\\s*\n\\s*\n", "\n\n");

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would remove <relativePath> from %s", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Would remove <relativePath> element (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Removed <relativePath> from %s", pomFile);

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description("Removed <relativePath> element - Maven will use default parent resolution")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }
}
