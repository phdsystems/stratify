package dev.engineeringlab.stratify.structure.remediation.fixer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates module hierarchy constraints before applying refactoring operations.
 *
 * <p>This utility enforces the three-tier module hierarchy:
 *
 * <ul>
 *   <li><b>Pure Aggregator (-aggregator)</b>: Only aggregates other modules, no leaf children
 *   <li><b>Parent Aggregator (-parent)</b>: Groups leaf modules (api/core/facade/spi/common)
 *   <li><b>Leaf Module (-api/-core/-facade/-spi/-common)</b>: Contains source code, no children
 * </ul>
 *
 * <p>Key constraints:
 *
 * <ul>
 *   <li>Nothing below leaf modules
 *   <li>Leaf modules must have a -parent parent
 *   <li>Parent aggregators can only have leaf children
 *   <li>Pure aggregators can only have parent aggregators or other pure aggregators as children
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * ModuleHierarchyValidator validator = new ModuleHierarchyValidator();
 * ModuleType type = validator.detectModuleType(pomPath);
 *
 * if (type == ModuleType.PARENT_AGGREGATOR) {
 *     // Cannot rename to -aggregator, has leaf children
 * }
 *
 * ValidationResult result = validator.validateRename(pomPath, "old-name", "new-name-aggregator");
 * if (!result.isValid()) {
 *     // Rename would break hierarchy rules
 * }
 * }</pre>
 */
public class ModuleHierarchyValidator {

  /** Suffixes that identify leaf modules. */
  private static final Set<String> LEAF_SUFFIXES =
      Set.of("-api", "-core", "-facade", "-spi", "-common", "-commons", "-util", "-utils");

  /** Pattern to extract module names from pom.xml. */
  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

  /** Pattern to extract artifactId from pom.xml (outside parent block). */
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<artifactId>([^<]+)</artifactId>");

  /** Module types in the hierarchy. */
  public enum ModuleType {
    /** Pure aggregator: only has other aggregators or parent modules as children. */
    PURE_AGGREGATOR,
    /** Parent aggregator: has leaf modules as children. */
    PARENT_AGGREGATOR,
    /** Leaf module: no children, contains source code. */
    LEAF,
    /** Unknown: cannot determine type. */
    UNKNOWN
  }

  /** Result of a hierarchy validation check. */
  public record ValidationResult(boolean isValid, String message, List<String> violations) {
    public static ValidationResult valid() {
      return new ValidationResult(true, "Validation passed", List.of());
    }

    public static ValidationResult invalid(String message, List<String> violations) {
      return new ValidationResult(false, message, violations);
    }

    public static ValidationResult invalid(String message) {
      return new ValidationResult(false, message, List.of(message));
    }
  }

  /**
   * Detects the module type based on the pom.xml content and children.
   *
   * @param pomFile path to the pom.xml file
   * @return the detected module type
   */
  public ModuleType detectModuleType(Path pomFile) {
    if (pomFile == null || !Files.exists(pomFile)) {
      return ModuleType.UNKNOWN;
    }

    try {
      String content = Files.readString(pomFile);
      return detectModuleType(content, pomFile.getParent());
    } catch (IOException e) {
      return ModuleType.UNKNOWN;
    }
  }

  /**
   * Detects the module type based on pom content and module directory.
   *
   * @param pomContent the pom.xml content
   * @param moduleDir the module directory (parent of pom.xml)
   * @return the detected module type
   */
  public ModuleType detectModuleType(String pomContent, Path moduleDir) {
    if (pomContent == null) {
      return ModuleType.UNKNOWN;
    }

    // Check if this module has children
    List<String> children = extractModuleNames(pomContent);

    if (children.isEmpty()) {
      // No children - check if it's a leaf module by artifactId
      String artifactId = extractArtifactId(pomContent);
      if (artifactId != null && hasLeafSuffix(artifactId)) {
        return ModuleType.LEAF;
      }
      // No children and not a leaf suffix - could be an empty aggregator
      return ModuleType.PURE_AGGREGATOR;
    }

    // Has children - determine if children are leaf modules or other aggregators
    boolean hasLeafChildren = hasLeafModuleChildren(children, moduleDir);

    return hasLeafChildren ? ModuleType.PARENT_AGGREGATOR : ModuleType.PURE_AGGREGATOR;
  }

  /**
   * Checks if a module has any leaf module children.
   *
   * @param childNames list of child module directory names
   * @param moduleDir the parent module directory
   * @return true if any child is a leaf module
   */
  public boolean hasLeafModuleChildren(List<String> childNames, Path moduleDir) {
    for (String childName : childNames) {
      // Check by directory name suffix
      if (hasLeafSuffix(childName)) {
        return true;
      }

      // Also check the child's artifactId if pom.xml exists
      if (moduleDir != null) {
        Path childPom = moduleDir.resolve(childName).resolve("pom.xml");
        if (Files.exists(childPom)) {
          try {
            String childContent = Files.readString(childPom);
            String childArtifactId = extractArtifactId(childContent);
            if (childArtifactId != null && hasLeafSuffix(childArtifactId)) {
              return true;
            }
          } catch (IOException e) {
            // Ignore and continue checking
          }
        }
      }
    }
    return false;
  }

  /**
   * Validates whether a rename operation is safe for the module hierarchy.
   *
   * @param pomFile path to the pom.xml file
   * @param oldArtifactId current artifact ID
   * @param newArtifactId proposed new artifact ID
   * @return validation result
   */
  public ValidationResult validateRename(Path pomFile, String oldArtifactId, String newArtifactId) {
    if (pomFile == null || !Files.exists(pomFile)) {
      return ValidationResult.invalid("POM file does not exist");
    }

    List<String> violations = new ArrayList<>();

    try {
      String content = Files.readString(pomFile);
      Path moduleDir = pomFile.getParent();
      ModuleType currentType = detectModuleType(content, moduleDir);

      // Rule 1: Cannot rename to -aggregator if has leaf children
      if (newArtifactId.endsWith("-aggregator") && currentType == ModuleType.PARENT_AGGREGATOR) {
        List<String> children = extractModuleNames(content);
        List<String> leafChildren = children.stream().filter(this::hasLeafSuffix).toList();

        violations.add(
            String.format(
                "Cannot rename to '%s': module has leaf children %s that require a -parent suffix. "
                    + "Leaf modules (-api, -core, -spi, -facade, -common) must have a parent ending with -parent.",
                newArtifactId, leafChildren));
      }

      // Rule 2: Cannot rename from -parent to -aggregator if children are leaf modules
      if (oldArtifactId.endsWith("-parent") && newArtifactId.endsWith("-aggregator")) {
        List<String> children = extractModuleNames(content);
        if (hasLeafModuleChildren(children, moduleDir)) {
          violations.add(
              String.format(
                  "Cannot rename '%s' to '%s': this is a parent aggregator with leaf module children. "
                      + "Parent aggregators must keep the -parent suffix.",
                  oldArtifactId, newArtifactId));
        }
      }

      // Rule 3: Leaf modules cannot be renamed to -aggregator or -parent
      if (currentType == ModuleType.LEAF) {
        if (newArtifactId.endsWith("-aggregator") || newArtifactId.endsWith("-parent")) {
          violations.add(
              String.format(
                  "Cannot rename leaf module '%s' to '%s': leaf modules cannot become aggregators.",
                  oldArtifactId, newArtifactId));
        }
      }

    } catch (IOException e) {
      return ValidationResult.invalid("Error reading POM file: " + e.getMessage());
    }

    if (violations.isEmpty()) {
      return ValidationResult.valid();
    }

    return ValidationResult.invalid("Rename would violate module hierarchy rules", violations);
  }

  /**
   * Validates the complete module hierarchy starting from a root module.
   *
   * @param pomFile path to the root pom.xml file
   * @return validation result with all hierarchy violations
   */
  public ValidationResult validateHierarchy(Path pomFile) {
    if (pomFile == null || !Files.exists(pomFile)) {
      return ValidationResult.invalid("POM file does not exist");
    }

    List<String> violations = new ArrayList<>();
    validateHierarchyRecursive(pomFile, violations, 0);

    if (violations.isEmpty()) {
      return ValidationResult.valid();
    }

    return ValidationResult.invalid("Module hierarchy violations found", violations);
  }

  private void validateHierarchyRecursive(Path pomFile, List<String> violations, int depth) {
    if (!Files.exists(pomFile)) {
      return;
    }

    try {
      String content = Files.readString(pomFile);
      Path moduleDir = pomFile.getParent();
      String artifactId = extractArtifactId(content);
      ModuleType type = detectModuleType(content, moduleDir);
      List<String> children = extractModuleNames(content);

      // Validate based on module type
      switch (type) {
        case PURE_AGGREGATOR -> {
          // Pure aggregators should not have leaf children directly
          for (String child : children) {
            if (hasLeafSuffix(child)) {
              violations.add(
                  String.format(
                      "Pure aggregator '%s' has direct leaf child '%s'. "
                          + "Leaf modules should be under a parent aggregator (-parent).",
                      artifactId, child));
            }
          }
        }
        case PARENT_AGGREGATOR -> {
          // Parent aggregators should only have leaf children
          for (String child : children) {
            Path childPom = moduleDir.resolve(child).resolve("pom.xml");
            if (Files.exists(childPom)) {
              String childContent = Files.readString(childPom);
              String childArtifactId = extractArtifactId(childContent);
              if (childArtifactId != null) {
                if (childArtifactId.endsWith("-parent")
                    || childArtifactId.endsWith("-aggregator")) {
                  violations.add(
                      String.format(
                          "Parent aggregator '%s' has non-leaf child '%s'. "
                              + "Parent aggregators should only contain leaf modules.",
                          artifactId, childArtifactId));
                }
              }
            }
          }
        }
        case LEAF -> {
          // Leaf modules should not have children
          if (!children.isEmpty()) {
            violations.add(
                String.format(
                    "Leaf module '%s' has children %s. Leaf modules cannot have submodules.",
                    artifactId, children));
          }
        }
        default -> {
          // Unknown type - skip
        }
      }

      // Recursively validate children
      for (String child : children) {
        Path childPom = moduleDir.resolve(child).resolve("pom.xml");
        validateHierarchyRecursive(childPom, violations, depth + 1);
      }

    } catch (IOException e) {
      violations.add("Error reading " + pomFile + ": " + e.getMessage());
    }
  }

  /**
   * Extracts module names from the pom.xml modules section.
   *
   * @param pomContent the pom.xml content
   * @return list of module names
   */
  public List<String> extractModuleNames(String pomContent) {
    List<String> modules = new ArrayList<>();
    Matcher matcher = MODULE_PATTERN.matcher(pomContent);
    while (matcher.find()) {
      modules.add(matcher.group(1).trim());
    }
    return modules;
  }

  /**
   * Extracts the artifactId from pom.xml content (outside parent block).
   *
   * @param pomContent the pom.xml content
   * @return the artifactId, or null if not found
   */
  public String extractArtifactId(String pomContent) {
    // Find the parent block end if it exists
    int parentEnd = pomContent.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    // Find artifactId after parent block
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(pomContent.substring(searchStart));
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /**
   * Checks if a name has a leaf module suffix.
   *
   * @param name the module name or artifactId
   * @return true if it has a leaf suffix
   */
  public boolean hasLeafSuffix(String name) {
    if (name == null) {
      return false;
    }
    return LEAF_SUFFIXES.stream().anyMatch(name::endsWith);
  }

  /**
   * Determines the recommended suffix for a module based on its children.
   *
   * @param pomFile path to the pom.xml file
   * @return "-parent" if has leaf children, "-aggregator" if has non-leaf children, null if leaf
   */
  public String recommendedSuffix(Path pomFile) {
    ModuleType type = detectModuleType(pomFile);
    return switch (type) {
      case PARENT_AGGREGATOR -> "-parent";
      case PURE_AGGREGATOR -> "-aggregator";
      case LEAF, UNKNOWN -> null;
    };
  }
}
