package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import java.nio.file.Path;

/**
 * Remediator for MD-018: No parent module can have more than one child module of the same layer
 * type.
 *
 * <p>This violation occurs when a parent module contains multiple child modules that belong to the
 * same architectural layer (e.g., two -api modules, two -core modules, etc.). This is a structural
 * design issue that requires manual decision-making and cannot be automatically fixed.
 *
 * <p>Example violation scenario:
 *
 * <pre>
 * parent-module/
 *   ├── pom.xml
 *   ├── module-api/        ← First API module
 *   └── another-api/       ← Second API module (duplicate layer)
 * </pre>
 *
 * <p>This fixer provides guidance on how to resolve the violation with the following options:
 *
 * <ul>
 *   <li><b>Merge modules:</b> Combine the duplicate layer modules into a single module if they
 *       serve similar purposes
 *   <li><b>Split to separate parents:</b> Restructure the hierarchy by creating separate parent
 *       modules for each layer module if they represent different bounded contexts
 *   <li><b>Rename one module:</b> Change the layer suffix of one module if it was misclassified
 *       (e.g., rename to -impl, -service, or -util)
 * </ul>
 *
 * <p><b>Priority:</b> 75 (similar to other modular design fixers that require manual intervention)
 *
 * @see <a href="doc/3-design/modular-design-architecture.md">Modular Design Architecture</a>
 * @since 0.2.0
 */
public class DuplicateLayerRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MD-018"};
  private static final int PRIORITY = 75;

  public DuplicateLayerRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "DuplicateLayerRemediator";
  }

  @Override
  public String getDescription() {
    return "Provides guidance for MD-018 violations: duplicate layer modules under the same parent. "
        + "This is a structural issue requiring manual decision-making.";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MD-018 violation");
    }

    Path location = violation.location();
    if (location == null) {
      return FixResult.skipped(violation, "No location specified");
    }

    // Extract relevant information from the violation message
    String message = violation.message();
    String parentModule = extractParentModule(location);
    String layerType = extractLayerType(message);

    // Build detailed guidance message
    StringBuilder guidance = new StringBuilder();
    guidance.append(
        "DUPLICATE LAYER VIOLATION: Multiple child modules of the same layer type detected.\n\n");
    guidance.append(
        "RULE: A parent module should not have more than one child module of the same layer type.\n");
    guidance.append(
        "      Each layer (e.g., -api, -core, -impl) should appear at most once per parent.\n\n");
    guidance
        .append("Affected parent: ")
        .append(parentModule != null ? parentModule : location)
        .append("\n");

    if (layerType != null && !layerType.isEmpty()) {
      guidance.append("Duplicate layer: ").append(layerType).append("\n");
    }

    guidance.append("\n");
    guidance.append("This is a structural design issue that requires manual intervention.\n");
    guidance.append("To fix this violation, choose one of the following options:\n\n");

    guidance.append("OPTION 1: Merge modules\n");
    guidance.append("  - Combine the duplicate layer modules into a single module\n");
    guidance.append("  - Best when modules serve similar purposes and can be consolidated\n");
    guidance.append("  - Steps:\n");
    guidance.append("    1. Review the functionality of each duplicate module\n");
    guidance.append("    2. Merge code from one module into the other\n");
    guidance.append("    3. Update dependencies in other modules to reference the merged module\n");
    guidance.append("    4. Remove the obsolete module from the parent's <modules> section\n");
    guidance.append("    5. Delete the empty module directory\n\n");

    guidance.append("OPTION 2: Split to separate parents\n");
    guidance.append("  - Restructure the hierarchy by creating separate parent modules\n");
    guidance.append("  - Best when modules represent different bounded contexts or domains\n");
    guidance.append("  - Steps:\n");
    guidance.append("    1. Create a new parent module for one of the duplicate layers\n");
    guidance.append("    2. Move the appropriate child module under the new parent\n");
    guidance.append("    3. Update the moved module's <parent> reference\n");
    guidance.append("    4. Update the new parent's <modules> section\n");
    guidance.append(
        "    5. Remove the moved module from the original parent's <modules> section\n\n");

    guidance.append("OPTION 3: Rename one module\n");
    guidance.append("  - Change the layer suffix if a module was misclassified\n");
    guidance.append(
        "  - Best when a module doesn't truly belong to the layer indicated by its name\n");
    guidance.append("  - Steps:\n");
    guidance.append("    1. Determine the correct layer for the misclassified module\n");
    guidance.append("    2. Rename the module directory and update its artifactId in pom.xml\n");
    guidance.append("    3. Update references in the parent's <modules> section\n");
    guidance.append("    4. Update dependencies in other modules that reference this module\n");
    guidance.append(
        "    5. Consider suffixes: -api, -core, -impl, -service, -util, -common, etc.\n\n");

    guidance.append("Choose the option that best aligns with your architectural goals and the\n");
    guidance.append("intended boundaries of your modules.");

    return FixResult.notFixable(violation, guidance.toString());
  }

  /**
   * Extracts the parent module name from the violation location.
   *
   * @param location the location path from the violation
   * @return the parent module name, or null if it cannot be determined
   */
  private String extractParentModule(Path location) {
    if (location == null) {
      return null;
    }

    // Get the directory name (parent module)
    Path parent = location.getParent();
    if (parent != null) {
      return parent.getFileName().toString();
    }

    return location.getFileName().toString();
  }

  /**
   * Extracts the layer type from the violation message.
   *
   * <p>Attempts to identify the layer type mentioned in the message (e.g., "api", "core", "impl",
   * etc.).
   *
   * @param message the violation message
   * @return the layer type, or empty string if not found
   */
  private String extractLayerType(String message) {
    if (message == null || message.isEmpty()) {
      return "";
    }

    // Common layer types to look for
    String[] layerTypes = {
      "-api",
      "-core",
      "-impl",
      "-service",
      "-util",
      "-common",
      "-commons",
      "-domain",
      "-application",
      "-infrastructure"
    };

    for (String layer : layerTypes) {
      if (message.toLowerCase().contains(layer)) {
        return layer;
      }
    }

    return "";
  }
}
