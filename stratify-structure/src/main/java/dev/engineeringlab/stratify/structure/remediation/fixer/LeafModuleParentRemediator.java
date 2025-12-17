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
 * Remediator for MS-012: Leaf Module Parent Must Be Parent Module.
 *
 * <p>Enforces that leaf modules (-api, -core, -facade, -spi, -common) must have a parent whose
 * artifactId ends with -parent. This ensures proper module hierarchy:
 *
 * <pre>
 * feature-parent (parent aggregator)
 *   └── feature-api (leaf module)
 *   └── feature-core (leaf module)
 *   └── feature-facade (leaf module)
 * </pre>
 *
 * <p>This fixer automatically renames the parent module's artifactId to end with -parent and
 * updates all references in child modules.
 *
 * <h2>Fix Strategy</h2>
 *
 * <ol>
 *   <li>Identify the parent module that doesn't end with -parent
 *   <li>Rename the parent's artifactId to add -parent suffix
 *   <li>Update all child module pom.xml files to reference the new parent name
 * </ol>
 *
 * <h2>Example Transformation</h2>
 *
 * <pre>
 * // Before (parent pom.xml)
 * &lt;artifactId&gt;my-feature&lt;/artifactId&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;my-feature-api&lt;/module&gt;
 *     &lt;module&gt;my-feature-core&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After (parent pom.xml)
 * &lt;artifactId&gt;my-feature-parent&lt;/artifactId&gt;
 * &lt;packaging&gt;pom&lt;/packaging&gt;
 * &lt;modules&gt;
 *     &lt;module&gt;my-feature-api&lt;/module&gt;
 *     &lt;module&gt;my-feature-core&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // Before (child pom.xml)
 * &lt;parent&gt;
 *     &lt;artifactId&gt;my-feature&lt;/artifactId&gt;
 * &lt;/parent&gt;
 *
 * // After (child pom.xml)
 * &lt;parent&gt;
 *     &lt;artifactId&gt;my-feature-parent&lt;/artifactId&gt;
 * &lt;/parent&gt;
 * </pre>
 *
 * @see
 *     dev.engineeringlab.scanner.plugin.scanner.plugin.ms.scanner.core.MS012LeafModuleParentMustBeParent
 * @since 0.2.0
 */
public class LeafModuleParentRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-012"};
  private static final int PRIORITY = 60;

  private static final String SUFFIX_PARENT = "-parent";
  private static final List<String> LEAF_SUFFIXES =
      List.of("-api", "-core", "-facade", "-spi", "-common");

  // Pattern to match artifactId element (outside parent block)
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("(<artifactId>)([^<]+)(</artifactId>)", Pattern.DOTALL);

  // Pattern to match parent artifactId element
  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile(
          "(<parent>.*?<artifactId>)([^<]+)(</artifactId>.*?</parent>)", Pattern.DOTALL);

  public LeafModuleParentRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "LeafModuleParentRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-012 violations: ensures leaf modules have a parent ending with -parent by renaming the parent module";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-012 violation");
    }

    Path location = violation.location();
    if (location == null) {
      return FixResult.skipped(violation, "No location specified");
    }

    // If location is a directory, look for pom.xml in it
    Path leafPomFile = location;
    if (Files.isDirectory(location)) {
      leafPomFile = location.resolve("pom.xml");
    }

    if (!leafPomFile.toString().endsWith("pom.xml")) {
      return FixResult.skipped(violation, "Not a pom.xml file: " + leafPomFile);
    }

    if (!Files.exists(leafPomFile)) {
      return FixResult.skipped(violation, "File not found: " + leafPomFile);
    }

    // Skip verification - MS-012 fixes are coordinated across all children,
    // so individual fixes may temporarily break compilation until all are applied
    try {
      return fixLeafModuleParent(leafPomFile, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error fixing leaf module parent: " + e.getMessage());
    }
  }

  /** Fixes the leaf module parent by renaming the parent module to end with -parent. */
  private FixResult fixLeafModuleParent(
      Path leafPomFile, StructureViolation violation, FixerContext context) throws Exception {
    String leafContent = readFile(leafPomFile);

    // Extract current leaf module's artifactId
    String leafArtifactId = extractArtifactId(leafContent);
    if (leafArtifactId == null || !isLeafModule(leafArtifactId)) {
      return FixResult.skipped(
          violation,
          "Module is not a leaf module (doesn't end with -api, -core, -facade, -spi, or -common)");
    }

    // Extract parent artifactId from the leaf module's pom.xml
    String currentParentArtifactId = extractParentArtifactId(leafContent);
    if (currentParentArtifactId == null) {
      return FixResult.skipped(violation, "No parent element found in leaf module pom.xml");
    }

    // Check if parent already ends with -parent
    if (currentParentArtifactId.endsWith(SUFFIX_PARENT)) {
      return FixResult.skipped(
          violation,
          String.format("Parent '%s' already ends with -parent", currentParentArtifactId));
    }

    // Determine the new parent artifactId
    // Strip -aggregator suffix if present (can't be both -aggregator and -parent)
    String baseName = currentParentArtifactId;
    if (baseName.endsWith("-aggregator")) {
      baseName = baseName.substring(0, baseName.length() - "-aggregator".length());
    }
    String newParentArtifactId = baseName + SUFFIX_PARENT;

    // Find the parent pom.xml file
    Path parentPomFile = findParentPomFile(leafPomFile, leafContent);
    if (parentPomFile == null || !Files.exists(parentPomFile)) {
      return FixResult.skipped(
          violation, "Could not locate parent pom.xml file for parent: " + currentParentArtifactId);
    }

    // Read parent pom.xml
    String parentContent = readFile(parentPomFile);
    String parentArtifactId = extractArtifactId(parentContent);
    if (parentArtifactId == null) {
      return FixResult.skipped(violation, "Could not extract artifactId from parent pom.xml");
    }

    // Check if parent was already renamed (ends with -parent) but child still references old name
    if (!parentArtifactId.equals(currentParentArtifactId)) {
      if (parentArtifactId.endsWith(SUFFIX_PARENT)) {
        // Parent was already renamed, just update the child to reference the new name
        context.log(
            "Parent already renamed to '%s', updating child reference from '%s'",
            parentArtifactId, currentParentArtifactId);
        return updateChildParentReference(
            leafPomFile, currentParentArtifactId, parentArtifactId, violation, context);
      }
      return FixResult.skipped(
          violation,
          String.format(
              "Parent artifactId mismatch: expected '%s', found '%s'",
              currentParentArtifactId, parentArtifactId));
    }

    context.log(
        "Renaming parent module '%s' to '%s'", currentParentArtifactId, newParentArtifactId);

    List<Path> modifiedFiles = new ArrayList<>();
    List<String> diffs = new ArrayList<>();

    // Modify the parent pom.xml
    String modifiedParentContent =
        renameArtifactId(parentContent, currentParentArtifactId, newParentArtifactId);
    if (!modifiedParentContent.equals(parentContent)) {
      modifiedFiles.add(parentPomFile);
      diffs.add(
          generateDiff(
              parentContent, modifiedParentContent, parentPomFile.getFileName().toString()));
    }

    // Find all child modules that reference this parent
    List<Path> childPomFiles = findChildModulePoms(parentPomFile.getParent(), parentContent);
    for (Path childPom : childPomFiles) {
      if (Files.exists(childPom)) {
        try {
          String childContent = readFile(childPom);
          String childParentArtifactId = extractParentArtifactId(childContent);

          if (currentParentArtifactId.equals(childParentArtifactId)) {
            String modifiedChildContent =
                updateParentArtifactId(childContent, currentParentArtifactId, newParentArtifactId);
            if (!childContent.equals(modifiedChildContent)) {
              modifiedFiles.add(childPom);
              diffs.add(
                  generateDiff(
                      childContent, modifiedChildContent, childPom.getFileName().toString()));
            }
          }
        } catch (Exception e) {
          context.log("Warning: Failed to process child pom %s: %s", childPom, e.getMessage());
        }
      }
    }

    if (modifiedFiles.isEmpty()) {
      return FixResult.skipped(violation, "No changes needed");
    }

    if (context.dryRun()) {
      context.log(
          "[DRY-RUN] Would rename parent '%s' to '%s' and update %d file(s)",
          currentParentArtifactId, newParentArtifactId, modifiedFiles.size());

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would rename parent '%s' to '%s' and update %d child reference(s)",
                  currentParentArtifactId, newParentArtifactId, modifiedFiles.size()))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Apply changes
    String parentModifiedContent =
        renameArtifactId(parentContent, currentParentArtifactId, newParentArtifactId);
    backup(parentPomFile, context.projectRoot());
    writeFile(parentPomFile, parentModifiedContent);
    context.log("Updated parent pom.xml: %s", parentPomFile);

    for (Path childPom : childPomFiles) {
      if (Files.exists(childPom)) {
        try {
          String childContent = readFile(childPom);
          String childParentArtifactId = extractParentArtifactId(childContent);

          if (currentParentArtifactId.equals(childParentArtifactId)) {
            String modifiedChildContent =
                updateParentArtifactId(childContent, currentParentArtifactId, newParentArtifactId);
            if (!childContent.equals(modifiedChildContent)) {
              backup(childPom, context.projectRoot());
              writeFile(childPom, modifiedChildContent);
              context.log("Updated child pom.xml: %s", childPom);
            }
          }
        } catch (Exception e) {
          context.log("Warning: Failed to update child pom %s: %s", childPom, e.getMessage());
        }
      }
    }

    context.log(
        "Successfully renamed parent '%s' to '%s'", currentParentArtifactId, newParentArtifactId);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Renamed parent '%s' to '%s' and updated %d child reference(s)",
                currentParentArtifactId, newParentArtifactId, modifiedFiles.size()))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /** Checks if a module is a leaf module based on its artifactId. */
  private boolean isLeafModule(String artifactId) {
    return LEAF_SUFFIXES.stream().anyMatch(artifactId::endsWith);
  }

  /** Extracts the artifactId from pom.xml content (outside parent block). */
  private String extractArtifactId(String content) {
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content.substring(searchStart));
    if (matcher.find()) {
      return matcher.group(2).trim();
    }
    return null;
  }

  /** Extracts the parent's artifactId from pom.xml content. */
  private String extractParentArtifactId(String content) {
    Matcher matcher = PARENT_ARTIFACT_ID_PATTERN.matcher(content);
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
      // Escape $ and \ in replacement to avoid regex interpretation
      replacement = Matcher.quoteReplacement(replacement);
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
      // Escape $ and \ in replacement to avoid regex interpretation
      replacement = Matcher.quoteReplacement(replacement);
      return matcher.replaceFirst(replacement);
    }
    return content;
  }

  /** Finds the parent pom.xml file by following the relativePath or looking in parent directory. */
  private Path findParentPomFile(Path leafPomFile, String leafContent) {
    // Extract relativePath from parent element
    Pattern relativePathPattern =
        Pattern.compile(
            "<parent>.*?<relativePath>([^<]+)</relativePath>.*?</parent>", Pattern.DOTALL);
    Matcher matcher = relativePathPattern.matcher(leafContent);

    if (matcher.find()) {
      String relativePath = matcher.group(1).trim();
      Path parentPath = leafPomFile.getParent().resolve(relativePath).normalize();
      if (Files.isDirectory(parentPath)) {
        return parentPath.resolve("pom.xml");
      }
      return parentPath;
    }

    // Default: look in parent directory
    Path parentDir = leafPomFile.getParent().getParent();
    if (parentDir != null) {
      Path parentPom = parentDir.resolve("pom.xml");
      if (Files.exists(parentPom)) {
        return parentPom;
      }
    }

    return null;
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

  /**
   * Updates a single child module's parent reference when the parent has already been renamed.
   *
   * <p>This handles the case where the parent module was renamed in a previous run but the child
   * still references the old name.
   *
   * @param childPomFile the child pom.xml file to update
   * @param oldParentArtifactId the old parent artifactId (what child currently references)
   * @param newParentArtifactId the new parent artifactId (what parent is actually named)
   * @param violation the violation being fixed
   * @param context the fixer context
   * @return the fix result
   */
  private FixResult updateChildParentReference(
      Path childPomFile,
      String oldParentArtifactId,
      String newParentArtifactId,
      StructureViolation violation,
      FixerContext context) {
    try {
      String childContent = readFile(childPomFile);
      String modifiedContent =
          updateParentArtifactId(childContent, oldParentArtifactId, newParentArtifactId);

      if (childContent.equals(modifiedContent)) {
        return FixResult.skipped(violation, "No changes needed to child pom.xml");
      }

      String diff =
          generateDiff(childContent, modifiedContent, childPomFile.getFileName().toString());

      if (context.dryRun()) {
        context.log(
            "[DRY-RUN] Would update parent reference in %s from '%s' to '%s'",
            childPomFile, oldParentArtifactId, newParentArtifactId);

        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.DRY_RUN)
            .description(
                String.format(
                    "Would update parent reference from '%s' to '%s'",
                    oldParentArtifactId, newParentArtifactId))
            .modifiedFiles(List.of(childPomFile))
            .diffs(List.of(diff))
            .build();
      }

      // Apply changes
      backup(childPomFile, context.projectRoot());
      writeFile(childPomFile, modifiedContent);
      context.log(
          "Updated parent reference in %s from '%s' to '%s'",
          childPomFile, oldParentArtifactId, newParentArtifactId);

      cleanupBackupsOnSuccess(List.of(childPomFile), context.projectRoot());

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description(
              String.format(
                  "Updated parent reference from '%s' to '%s'",
                  oldParentArtifactId, newParentArtifactId))
          .modifiedFiles(List.of(childPomFile))
          .diffs(List.of(diff))
          .build();

    } catch (Exception e) {
      return FixResult.failed(
          violation, "Failed to update child parent reference: " + e.getMessage());
    }
  }
}
