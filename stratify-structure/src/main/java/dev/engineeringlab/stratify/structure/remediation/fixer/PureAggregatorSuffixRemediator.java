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
 * Remediator for MS-017: Pure Aggregator Must Use -aggregator Suffix.
 *
 * <p>Pure aggregator modules (modules that only have submodules, no code/layers) must have
 * artifactId ending with -aggregator suffix.
 *
 * <p>This fixer renames the artifactId to add the -aggregator suffix and updates child module
 * parent references to maintain consistency.
 *
 * <p><b>Important:</b> This fixer validates the module hierarchy before renaming. If the module has
 * leaf children (-api, -core, -spi, -facade, -common), it is a <b>parent aggregator</b> and should
 * NOT be renamed to -aggregator. Only true pure aggregators (with non-leaf children) will be
 * renamed.
 *
 * <p>Example transformation (for pure aggregators only):
 *
 * <pre>
 * // Before
 * &lt;artifactId&gt;my-module&lt;/artifactId&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;sub-module&lt;/module&gt;  &lt;!-- NOT a leaf module --&gt;
 * &lt;/modules&gt;
 *
 * // After
 * &lt;artifactId&gt;my-module-aggregator&lt;/artifactId&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;sub-module&lt;/module&gt;
 * &lt;/modules&gt;
 * </pre>
 *
 * <p>Modules with leaf children will be skipped:
 *
 * <pre>
 * // Will NOT be renamed (has leaf children)
 * &lt;artifactId&gt;my-module-parent&lt;/artifactId&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;my-module-api&lt;/module&gt;   &lt;!-- leaf --&gt;
 *     &lt;module&gt;my-module-core&lt;/module&gt;  &lt;!-- leaf --&gt;
 * &lt;/modules&gt;
 * </pre>
 */
public class PureAggregatorSuffixRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-017"};
  private static final int PRIORITY = 60;

  // Pattern to match artifactId element
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("(<artifactId>)([^<]+)(</artifactId>)", Pattern.DOTALL);

  // Pattern to match parent artifactId element
  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile(
          "(<parent>.*?<artifactId>)([^<]+)(</artifactId>.*?</parent>)", Pattern.DOTALL);

  /** Hierarchy validator for checking module types before renaming. */
  private final ModuleHierarchyValidator hierarchyValidator;

  public PureAggregatorSuffixRemediator() {
    this(new ModuleHierarchyValidator());
  }

  /**
   * Constructor with custom hierarchy validator (for testing).
   *
   * @param hierarchyValidator the validator to use
   */
  public PureAggregatorSuffixRemediator(ModuleHierarchyValidator hierarchyValidator) {
    setPriority(PRIORITY);
    this.hierarchyValidator = hierarchyValidator;
  }

  @Override
  public String getName() {
    return "PureAggregatorSuffixRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-017 violations: renames pure aggregator artifactId to add -aggregator suffix";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-017 violation");
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
      return FixResult.failed(violation, "Error fixing pure aggregator suffix: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Extract current artifactId
    String currentArtifactId = extractArtifactId(originalContent);
    if (currentArtifactId == null || currentArtifactId.isEmpty()) {
      return FixResult.failed(violation, "Could not extract artifactId from pom.xml");
    }

    // Check if already ends with -aggregator
    if (currentArtifactId.endsWith("-aggregator")) {
      return FixResult.skipped(violation, "ArtifactId already ends with -aggregator");
    }

    // CRITICAL: Validate module hierarchy before renaming
    // If this module has leaf children, it's a PARENT AGGREGATOR and should NOT be renamed
    ModuleHierarchyValidator.ModuleType moduleType =
        hierarchyValidator.detectModuleType(originalContent, pomFile.getParent());

    if (moduleType == ModuleHierarchyValidator.ModuleType.PARENT_AGGREGATOR) {
      List<String> children = hierarchyValidator.extractModuleNames(originalContent);
      List<String> leafChildren =
          children.stream().filter(hierarchyValidator::hasLeafSuffix).toList();

      String message =
          String.format(
              "Cannot rename to -aggregator: module '%s' has leaf children %s. "
                  + "This is a PARENT AGGREGATOR (not a pure aggregator). "
                  + "Leaf modules (-api, -core, -spi, -facade, -common) require their parent to end with -parent. "
                  + "Renaming would break MS-012 rule.",
              currentArtifactId, leafChildren);

      context.log("Skipping %s: %s", currentArtifactId, message);
      return FixResult.skipped(violation, message);
    }

    // Determine new artifactId
    String newArtifactId = determineNewArtifactId(currentArtifactId);

    // Validate the rename operation
    ModuleHierarchyValidator.ValidationResult validationResult =
        hierarchyValidator.validateRename(pomFile, currentArtifactId, newArtifactId);

    if (!validationResult.isValid()) {
      String message =
          String.format(
              "Cannot rename '%s' to '%s': %s",
              currentArtifactId, newArtifactId, String.join("; ", validationResult.violations()));
      context.log("Validation failed: %s", message);
      return FixResult.skipped(violation, message);
    }

    // Build modified content
    String modifiedContent = renameArtifactId(originalContent, currentArtifactId, newArtifactId);

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    // Find and update child module pom.xml files
    List<Path> childPomFiles = findChildModulePoms(pomFile.getParent(), originalContent);
    List<Path> modifiedFiles = new ArrayList<>();
    modifiedFiles.add(pomFile);

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());
    List<String> diffs = new ArrayList<>();
    diffs.add(diff);

    if (context.dryRun()) {
      context.log(
          "Would rename artifactId from '%s' to '%s' in %s",
          currentArtifactId, newArtifactId, pomFile);

      // Preview child updates in dry run
      for (Path childPom : childPomFiles) {
        if (Files.exists(childPom)) {
          String childContent = readFile(childPom);
          String updatedChildContent =
              updateParentArtifactId(childContent, currentArtifactId, newArtifactId);
          if (!childContent.equals(updatedChildContent)) {
            diffs.add(
                generateDiff(childContent, updatedChildContent, childPom.getFileName().toString()));
            modifiedFiles.add(childPom);
          }
        }
      }

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would rename artifactId from '%s' to '%s' (dry-run)",
                  currentArtifactId, newArtifactId))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    // Update child module parent references
    for (Path childPom : childPomFiles) {
      if (Files.exists(childPom)) {
        try {
          String childContent = readFile(childPom);
          String updatedChildContent =
              updateParentArtifactId(childContent, currentArtifactId, newArtifactId);

          if (!childContent.equals(updatedChildContent)) {
            backup(childPom, context.projectRoot());
            writeFile(childPom, updatedChildContent);
            modifiedFiles.add(childPom);
            diffs.add(
                generateDiff(childContent, updatedChildContent, childPom.getFileName().toString()));
            context.log("Updated parent reference in %s", childPom);
          }
        } catch (Exception e) {
          context.log("Warning: Failed to update child pom %s: %s", childPom, e.getMessage());
        }
      }
    }

    context.log(
        "Renamed artifactId from '%s' to '%s' in %s", currentArtifactId, newArtifactId, pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format("Renamed artifactId from '%s' to '%s'", currentArtifactId, newArtifactId))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /**
   * Extracts the artifactId from the pom.xml content. This finds the first artifactId outside of
   * any parent block.
   */
  private String extractArtifactId(String content) {
    // Find the parent block end if it exists
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    // Find artifactId after parent block
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content.substring(searchStart));
    if (matcher.find()) {
      return matcher.group(2).trim();
    }
    return null;
  }

  /**
   * Determines the new artifactId by adding -aggregator suffix.
   *
   * <p><b>Note:</b> This method is only called AFTER hierarchy validation passes. If the module has
   * leaf children (is a parent aggregator), we skip before reaching here. This means if we see
   * -parent suffix here, it's safe to replace because the validation already confirmed there are no
   * leaf children.
   */
  private String determineNewArtifactId(String currentArtifactId) {
    // If ends with -parent and we got here, it means the module has NO leaf children
    // (otherwise hierarchy validation would have skipped it)
    // So it's safe to replace -parent with -aggregator
    if (currentArtifactId.endsWith("-parent")) {
      return currentArtifactId.substring(0, currentArtifactId.length() - 7) + "-aggregator";
    }
    // Otherwise, append -aggregator
    return currentArtifactId + "-aggregator";
  }

  /** Renames the artifactId in the pom.xml content. */
  private String renameArtifactId(String content, String oldArtifactId, String newArtifactId) {
    // Find the parent block end if it exists
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    // Split content at parent end
    String beforeParent = content.substring(0, searchStart);
    String afterParent = content.substring(searchStart);

    // Replace first artifactId occurrence after parent block
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(afterParent);
    if (matcher.find()) {
      String replacement = matcher.group(1) + newArtifactId + matcher.group(3);
      afterParent = matcher.replaceFirst(replacement);
    }

    return beforeParent + afterParent;
  }

  /** Updates parent artifactId references in child module pom files. */
  private String updateParentArtifactId(
      String content, String oldArtifactId, String newArtifactId) {
    Matcher matcher = PARENT_ARTIFACT_ID_PATTERN.matcher(content);
    if (matcher.find() && matcher.group(2).trim().equals(oldArtifactId)) {
      String replacement = matcher.group(1) + newArtifactId + matcher.group(3);
      return matcher.replaceFirst(replacement);
    }
    return content;
  }

  /** Finds child module pom.xml files by parsing the modules section. */
  private List<Path> findChildModulePoms(Path parentDir, String pomContent) {
    List<Path> childPoms = new ArrayList<>();

    // Extract module names from <modules> section
    Pattern modulePattern = Pattern.compile("<module>([^<]+)</module>");
    Matcher matcher = modulePattern.matcher(pomContent);

    while (matcher.find()) {
      String moduleName = matcher.group(1).trim();
      Path childPom = parentDir.resolve(moduleName).resolve("pom.xml");
      if (Files.exists(childPom)) {
        childPoms.add(childPom);
      }
    }

    return childPoms;
  }
}
