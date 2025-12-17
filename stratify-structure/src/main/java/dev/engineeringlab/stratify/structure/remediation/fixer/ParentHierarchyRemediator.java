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
 * Remediator for MD-001: Parent aggregator has non-pure-aggregator parent.
 *
 * <p>Enforces that parent aggregators (-parent suffix) can only have pure aggregators (-aggregator
 * suffix) as their parent. This ensures proper modular hierarchy:
 *
 * <pre>
 * root-aggregator (pure aggregator)
 *   └── feature-parent (parent aggregator)
 *       └── feature-api (leaf module)
 *       └── feature-core (leaf module)
 * </pre>
 *
 * <p>This fixer provides suggestions for fixing the violation by either:
 *
 * <ol>
 *   <li>Renaming the grandparent to add '-aggregator' suffix
 *   <li>Restructuring the module hierarchy
 * </ol>
 *
 * <p><b>Note:</b> This is a suggestion-only fixer because renaming parent modules affects multiple
 * files across the project and requires careful coordination. Manual intervention is recommended to
 * ensure all references are updated correctly.
 *
 * @see dev.engineeringlab.ms.scanner.core.MD001ParentAggregatorHierarchy
 * @since 0.2.0
 */
public class ParentHierarchyRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MD-001"};
  private static final int PRIORITY = 75; // Medium-high priority

  // Pattern to extract parent artifactId from pom.xml
  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile("<parent>.*?<artifactId>([^<]+)</artifactId>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract current module's artifactId
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("</parent>.*?<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);

  // Pattern to check if module is a parent aggregator
  private static final Pattern PARENT_SUFFIX_PATTERN = Pattern.compile(".*-parent$");

  // Pattern to check if module is a pure aggregator
  private static final Pattern AGGREGATOR_SUFFIX_PATTERN = Pattern.compile(".*-aggregator$");

  // Pattern to extract parent groupId
  private static final Pattern PARENT_GROUP_ID_PATTERN =
      Pattern.compile("<parent>.*?<groupId>([^<]+)</groupId>.*?</parent>", Pattern.DOTALL);

  // Pattern to extract parent relativePath
  private static final Pattern PARENT_RELATIVE_PATH_PATTERN =
      Pattern.compile(
          "<parent>.*?<relativePath>([^<]+)</relativePath>.*?</parent>", Pattern.DOTALL);

  public ParentHierarchyRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ParentHierarchyRemediator";
  }

  @Override
  public String getDescription() {
    return "Provides suggestions for MD-001 violations where a parent aggregator's parent "
        + "is not a pure aggregator. Suggests renaming the grandparent to add '-aggregator' suffix.";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!"MD-001".equals(violation.ruleId())) {
      return FixResult.notFixable(violation, "Not an MD-001 violation");
    }

    // Check if this is specifically about parent hierarchy (not pure aggregator parent removal)
    if (!violation.message().contains("not a pure aggregator")
        && !violation.message().contains("non-pure-aggregator parent")) {
      return FixResult.skipped(
          violation, "This fixer only handles parent aggregator hierarchy violations");
    }

    Path pomFile = resolvePomFile(violation.location());
    if (pomFile == null || !Files.exists(pomFile)) {
      return FixResult.skipped(violation, "pom.xml not found at " + violation.location());
    }

    try {
      String pomContent = readFile(pomFile);

      // Extract information from pom.xml
      String currentArtifactId = extractCurrentArtifactId(pomContent);
      String parentArtifactId = extractParentArtifactId(pomContent);
      String parentGroupId = extractParentGroupId(pomContent);
      String relativePath = extractRelativePath(pomContent);

      if (currentArtifactId == null) {
        return FixResult.failed(violation, "Could not extract current module's artifactId");
      }

      if (parentArtifactId == null) {
        return FixResult.skipped(violation, "No parent element found in pom.xml");
      }

      // Verify this is a parent aggregator
      if (!PARENT_SUFFIX_PATTERN.matcher(currentArtifactId).matches()) {
        return FixResult.skipped(
            violation,
            "Module '"
                + currentArtifactId
                + "' is not a parent aggregator (doesn't end with -parent)");
      }

      // Verify parent is not a pure aggregator
      if (AGGREGATOR_SUFFIX_PATTERN.matcher(parentArtifactId).matches()) {
        return FixResult.skipped(
            violation, "Parent '" + parentArtifactId + "' is already a pure aggregator");
      }

      // Build suggestions
      String newParentArtifactId = suggestNewParentName(parentArtifactId);
      List<String> suggestions =
          buildSuggestions(
              currentArtifactId,
              parentArtifactId,
              newParentArtifactId,
              parentGroupId,
              relativePath,
              pomFile);

      String suggestionText = String.join("\n", suggestions);

      context.log(
          "MD-001 Violation: Parent aggregator '%s' has non-pure-aggregator parent '%s'",
          currentArtifactId, parentArtifactId);
      context.log("Suggestions:\n%s", suggestionText);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.SKIPPED)
          .description(suggestionText)
          .modifiedFiles(List.of())
          .diffs(List.of())
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Error analyzing parent hierarchy: " + e.getMessage());
    }
  }

  /** Suggests a new name for the parent module by adding '-aggregator' suffix. */
  private String suggestNewParentName(String currentName) {
    // If it ends with -parent, replace with -aggregator
    if (currentName.endsWith("-parent")) {
      return currentName.substring(0, currentName.length() - 7) + "-aggregator";
    }
    // Otherwise, just add -aggregator
    return currentName + "-aggregator";
  }

  /** Builds a list of actionable suggestions for fixing the violation. */
  private List<String> buildSuggestions(
      String currentArtifactId,
      String parentArtifactId,
      String newParentArtifactId,
      String parentGroupId,
      String relativePath,
      Path pomFile) {

    List<String> suggestions = new ArrayList<>();

    suggestions.add("MANUAL FIX REQUIRED: Parent aggregator hierarchy violation");
    suggestions.add("");
    suggestions.add("Current situation:");
    suggestions.add("  - Module: " + currentArtifactId + " (parent aggregator)");
    suggestions.add("  - Parent: " + parentArtifactId + " (NOT a pure aggregator)");
    suggestions.add("");
    suggestions.add("Problem: Parent aggregators can only have pure aggregators as parents.");
    suggestions.add("");
    suggestions.add("Suggested solution:");
    suggestions.add("");
    suggestions.add("Option 1: Rename the parent module to make it a pure aggregator");
    suggestions.add("  1. Rename '" + parentArtifactId + "' to '" + newParentArtifactId + "'");
    suggestions.add("  2. Update the following:");
    suggestions.add("     - Directory name: " + parentArtifactId + " → " + newParentArtifactId);
    suggestions.add(
        "     - Parent's pom.xml: <artifactId>" + newParentArtifactId + "</artifactId>");
    suggestions.add("     - All child pom.xml files that reference this parent");
    suggestions.add("     - Grandparent's <modules> section (if exists)");
    if (relativePath != null) {
      suggestions.add("     - relativePath references: " + relativePath);
    }
    suggestions.add("");
    suggestions.add("Option 2: Restructure the module hierarchy");
    suggestions.add("  1. Create a new pure aggregator (e.g., '" + newParentArtifactId + "')");
    suggestions.add("  2. Move '" + currentArtifactId + "' under the new pure aggregator");
    suggestions.add("  3. Update parent references accordingly");
    suggestions.add("");
    suggestions.add("Files that need to be updated:");
    suggestions.add("  - " + pomFile);
    if (relativePath != null) {
      Path parentPomPath = pomFile.getParent().resolve(relativePath).normalize();
      suggestions.add("  - " + parentPomPath);
    }
    suggestions.add("  - Any sibling modules referencing the same parent");
    suggestions.add("");
    suggestions.add("After making changes, run the scanner again to verify the fix.");

    return suggestions;
  }

  /** Extracts the current module's artifactId from pom.xml. */
  private String extractCurrentArtifactId(String pomContent) {
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    // Fallback: try to find any artifactId after modelVersion
    Pattern fallbackPattern =
        Pattern.compile("<modelVersion>.*?<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);
    matcher = fallbackPattern.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }

    return null;
  }

  /** Extracts the parent's artifactId from pom.xml. */
  private String extractParentArtifactId(String pomContent) {
    Matcher matcher = PARENT_ARTIFACT_ID_PATTERN.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the parent's groupId from pom.xml. */
  private String extractParentGroupId(String pomContent) {
    Matcher matcher = PARENT_GROUP_ID_PATTERN.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the relativePath from parent element, if present. */
  private String extractRelativePath(String pomContent) {
    Matcher matcher = PARENT_RELATIVE_PATH_PATTERN.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Resolves the pom.xml file path from the violation location. */
  private Path resolvePomFile(Path location) {
    if (location == null) {
      return null;
    }

    if (Files.isDirectory(location)) {
      return location.resolve("pom.xml");
    }

    if (location.toString().endsWith("pom.xml")) {
      return location;
    }

    return null;
  }
}
