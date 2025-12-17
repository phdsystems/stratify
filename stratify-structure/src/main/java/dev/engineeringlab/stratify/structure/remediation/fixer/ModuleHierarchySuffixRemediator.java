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
 * Smart Hierarchy-Aware Module Suffix Remediator.
 *
 * <p>This fixer intelligently determines the correct suffix for a module based on its children in
 * the module hierarchy:
 *
 * <ul>
 *   <li><b>Has leaf children</b> (-api, -core, -spi, -facade, -common) → rename to <b>-parent</b>
 *   <li><b>Has non-leaf children</b> (other aggregators/parents) → rename to <b>-aggregator</b>
 *   <li><b>Is a leaf module</b> (no children) → no suffix change needed
 * </ul>
 *
 * <h2>Module Hierarchy Rules</h2>
 *
 * <pre>
 * Pure Aggregator (-aggregator)
 *     └─ Parent Aggregator (-parent)
 *          └─ Leaf Modules (-api, -core, -facade, -spi, -common)
 * </pre>
 *
 * <h2>Supported Rules</h2>
 *
 * <ul>
 *   <li><b>MS-017</b>: Pure aggregator must use -aggregator suffix
 *   <li><b>MS-012</b>: Leaf modules must have parent ending with -parent
 * </ul>
 *
 * <h2>Smart Fix Examples</h2>
 *
 * <h3>Example 1: Module with leaf children → -parent</h3>
 *
 * <pre>
 * // Before (incorrectly named or unnamed)
 * &lt;artifactId&gt;my-module&lt;/artifactId&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;my-module-api&lt;/module&gt;
 *     &lt;module&gt;my-module-core&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After (smart fix detects leaf children)
 * &lt;artifactId&gt;my-module-parent&lt;/artifactId&gt;
 * </pre>
 *
 * <h3>Example 2: Module with non-leaf children → -aggregator</h3>
 *
 * <pre>
 * // Before
 * &lt;artifactId&gt;platform&lt;/artifactId&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;service-a&lt;/module&gt;
 *     &lt;module&gt;service-b&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After (smart fix detects non-leaf children)
 * &lt;artifactId&gt;platform-aggregator&lt;/artifactId&gt;
 * </pre>
 *
 * <h3>Example 3: Wrong suffix correction</h3>
 *
 * <pre>
 * // Before (incorrectly named -aggregator but has leaf children)
 * &lt;artifactId&gt;my-module-aggregator&lt;/artifactId&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;my-module-api&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After (smart fix corrects to -parent)
 * &lt;artifactId&gt;my-module-parent&lt;/artifactId&gt;
 * </pre>
 */
public class ModuleHierarchySuffixRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-017", "MS-012"};
  private static final int PRIORITY = 65; // Higher priority than individual fixers

  private static final String SUFFIX_PARENT = "-parent";
  private static final String SUFFIX_AGGREGATOR = "-aggregator";

  // Pattern to match artifactId element
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("(<artifactId>)([^<]+)(</artifactId>)", Pattern.DOTALL);

  // Pattern to match parent artifactId element
  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile(
          "(<parent>.*?<artifactId>)([^<]+)(</artifactId>.*?</parent>)", Pattern.DOTALL);

  /** Hierarchy validator for detecting module types. */
  private final ModuleHierarchyValidator hierarchyValidator;

  public ModuleHierarchySuffixRemediator() {
    this(new ModuleHierarchyValidator());
  }

  /**
   * Constructor with custom hierarchy validator (for testing).
   *
   * @param hierarchyValidator the validator to use
   */
  public ModuleHierarchySuffixRemediator(ModuleHierarchyValidator hierarchyValidator) {
    setPriority(PRIORITY);
    this.hierarchyValidator = hierarchyValidator;
  }

  @Override
  public String getName() {
    return "ModuleHierarchySuffixRemediator";
  }

  @Override
  public String getDescription() {
    return "Smart fixer that determines correct module suffix (-parent or -aggregator) based on children";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not a supported rule (MS-017 or MS-012)");
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
      return smartFixPomFile(pomFile, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error in smart suffix fix: " + e.getMessage());
    }
  }

  /** Smart fix that determines the correct suffix based on module hierarchy. */
  private FixResult smartFixPomFile(
      Path pomFile, StructureViolation violation, FixerContext context) throws Exception {
    String originalContent = readFile(pomFile);

    // Extract current artifactId
    String currentArtifactId = extractArtifactId(originalContent);
    if (currentArtifactId == null || currentArtifactId.isEmpty()) {
      return FixResult.failed(violation, "Could not extract artifactId from pom.xml");
    }

    // Detect module type based on children
    ModuleHierarchyValidator.ModuleType moduleType =
        hierarchyValidator.detectModuleType(originalContent, pomFile.getParent());

    // Determine the CORRECT suffix based on module type
    String correctSuffix = determineCorrectSuffix(moduleType);
    if (correctSuffix == null) {
      return FixResult.skipped(
          violation,
          String.format(
              "Module '%s' is a leaf module - no suffix change needed", currentArtifactId));
    }

    // Get the base name (without any existing suffix)
    String baseName = stripExistingSuffix(currentArtifactId);

    // Build the correct artifactId
    String newArtifactId = baseName + correctSuffix;

    // Check if already correct
    if (currentArtifactId.equals(newArtifactId)) {
      return FixResult.skipped(
          violation,
          String.format("ArtifactId '%s' already has correct suffix", currentArtifactId));
    }

    // Log what we're doing and why
    context.log("Smart fix: Module '%s' detected as %s", currentArtifactId, moduleType);
    context.log("  Children analysis: %s", describeChildren(originalContent, pomFile.getParent()));
    context.log("  Correct suffix: '%s' → renaming to '%s'", correctSuffix, newArtifactId);

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
      context.log("[DRY-RUN] Would rename '%s' to '%s'", currentArtifactId, newArtifactId);

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
                  "Would rename '%s' to '%s' (detected: %s)",
                  currentArtifactId, newArtifactId, moduleType))
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

    context.log("Smart fix complete: '%s' → '%s'", currentArtifactId, newArtifactId);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Smart fix: renamed '%s' to '%s' (detected: %s)",
                currentArtifactId, newArtifactId, moduleType))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /**
   * Determines the correct suffix based on module type.
   *
   * @param moduleType the detected module type
   * @return "-parent" for parent aggregators, "-aggregator" for pure aggregators, null for leaf
   */
  private String determineCorrectSuffix(ModuleHierarchyValidator.ModuleType moduleType) {
    return switch (moduleType) {
      case PARENT_AGGREGATOR -> SUFFIX_PARENT;
      case PURE_AGGREGATOR -> SUFFIX_AGGREGATOR;
      case LEAF, UNKNOWN -> null;
    };
  }

  /**
   * Strips existing -parent or -aggregator suffix from artifactId.
   *
   * @param artifactId the current artifactId
   * @return the base name without suffix
   */
  private String stripExistingSuffix(String artifactId) {
    if (artifactId.endsWith(SUFFIX_PARENT)) {
      return artifactId.substring(0, artifactId.length() - SUFFIX_PARENT.length());
    }
    if (artifactId.endsWith(SUFFIX_AGGREGATOR)) {
      return artifactId.substring(0, artifactId.length() - SUFFIX_AGGREGATOR.length());
    }
    return artifactId;
  }

  /** Describes the children of a module for logging. */
  private String describeChildren(String pomContent, Path moduleDir) {
    List<String> children = hierarchyValidator.extractModuleNames(pomContent);
    if (children.isEmpty()) {
      return "no children (leaf module)";
    }

    List<String> leafChildren = new ArrayList<>();
    List<String> nonLeafChildren = new ArrayList<>();

    for (String child : children) {
      if (hierarchyValidator.hasLeafSuffix(child)) {
        leafChildren.add(child);
      } else {
        // Also check child pom artifactId
        Path childPom = moduleDir.resolve(child).resolve("pom.xml");
        if (Files.exists(childPom)) {
          try {
            String childContent = Files.readString(childPom);
            String childArtifactId = hierarchyValidator.extractArtifactId(childContent);
            if (childArtifactId != null && hierarchyValidator.hasLeafSuffix(childArtifactId)) {
              leafChildren.add(child + " (artifactId: " + childArtifactId + ")");
              continue;
            }
          } catch (Exception e) {
            // Ignore
          }
        }
        nonLeafChildren.add(child);
      }
    }

    StringBuilder sb = new StringBuilder();
    if (!leafChildren.isEmpty()) {
      sb.append("leaf: ").append(leafChildren);
    }
    if (!nonLeafChildren.isEmpty()) {
      if (sb.length() > 0) sb.append(", ");
      sb.append("non-leaf: ").append(nonLeafChildren);
    }
    return sb.toString();
  }

  /** Extracts the artifactId from the pom.xml content. */
  private String extractArtifactId(String content) {
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content.substring(searchStart));
    if (matcher.find()) {
      return matcher.group(2).trim();
    }
    return null;
  }

  /** Renames the artifactId in the pom.xml content. */
  private String renameArtifactId(String content, String oldArtifactId, String newArtifactId) {
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    String beforeParent = content.substring(0, searchStart);
    String afterParent = content.substring(searchStart);

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
