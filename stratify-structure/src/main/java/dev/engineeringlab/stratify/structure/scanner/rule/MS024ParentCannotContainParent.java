package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MS-024: Parent Cannot Contain Another Parent.
 *
 * <p>Parent aggregators (-parent suffix) should only contain leaf modules or pure aggregators. They
 * must NOT contain other parent aggregators as submodules. This ensures a clean two-level hierarchy
 * where parent aggregators group leaf modules (api/core/facade/spi/common) and pure aggregators
 * group parent aggregators.
 *
 * <p>This rule verifies that a parent module does not contain any submodules that are themselves
 * parent aggregators (determined by -parent suffix or presence of layer modules).
 *
 * <p>Following the principle: No assumptions - verify the child module actually exists and check
 * its type directly, don't trust the config alone.
 */
public class MS024ParentCannotContainParent extends AbstractStructureRule {

  // Pattern to match the project's own artifactId (not from parent block)
  // Matches <artifactId> that comes after <modelVersion> to avoid parent's artifactId
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<modelVersion>.*?<artifactId>([^<]+)</artifactId>", Pattern.DOTALL);

  public MS024ParentCannotContainParent() {
    super("MS-024");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Only applies to parent modules (artifactId ends with -parent OR has layer submodules)
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();

    // Get the list of submodules from the module order
    List<String> submodules = module.getModuleOrder();
    if (submodules == null || submodules.isEmpty()) {
      return List.of();
    }

    // Check each submodule to see if it's a parent aggregator
    for (String submoduleName : submodules) {
      Path submodulePath = modulePath.resolve(submoduleName);

      // Skip if submodule directory doesn't exist
      if (!Files.exists(submodulePath) || !Files.isDirectory(submodulePath)) {
        continue;
      }

      Path submodulePom = submodulePath.resolve("pom.xml");
      if (!Files.exists(submodulePom)) {
        continue;
      }

      // Check if the submodule is a parent aggregator
      if (isParentAggregator(submodulePath, submodulePom)) {
        String submoduleArtifactId = extractArtifactId(submodulePom);
        violations.add(
            createViolation(
                module,
                String.format(
                    "Parent module '%s' contains another parent aggregator '%s'. "
                        + "Parent aggregators should only contain leaf modules or pure aggregators. "
                        + "Convert '%s' to a pure aggregator (-aggregator suffix) or restructure the hierarchy.",
                    module.getArtifactId(), submoduleArtifactId, submoduleArtifactId),
                submodulePath.toString()));
      }

      // Limit violations to avoid overwhelming output
      if (violations.size() >= MAX_VIOLATIONS) {
        break;
      }
    }

    return violations;
  }

  /**
   * Checks if a module is a parent aggregator by examining: 1. Whether its artifactId ends with
   * -parent 2. Whether it has layer submodules (api, core, facade, spi, common)
   *
   * @param modulePath path to the module directory
   * @param pomFile path to the module's pom.xml
   * @return true if the module is a parent aggregator
   */
  private boolean isParentAggregator(Path modulePath, Path pomFile) {
    // Check 1: Does artifactId end with -parent?
    String artifactId = extractArtifactId(pomFile);
    if (artifactId != null && artifactId.endsWith("-parent")) {
      return true;
    }

    // Check 2: Does it have layer submodules?
    // This is the key verification - don't trust config alone, verify actual structure
    return hasLayerSubmodules(modulePath);
  }

  /**
   * Extracts the artifactId from a pom.xml file. Finds the project's own artifactId (after
   * modelVersion, not from parent block).
   *
   * @param pomFile path to the pom.xml file
   * @return the artifactId, or null if not found
   */
  private String extractArtifactId(Path pomFile) {
    try {
      String content = Files.readString(pomFile);
      Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content);
      if (matcher.find()) {
        return matcher.group(1).trim();
      }
    } catch (IOException e) {
      // If we can't read the file, return null
    }
    return null;
  }

  /**
   * Checks if a module has layer submodules (api, core, facade, spi, common). This is the
   * definitive check for whether a module is a parent aggregator.
   *
   * @param modulePath path to the module directory
   * @return true if the module has any layer submodules
   */
  private boolean hasLayerSubmodules(Path modulePath) {
    String[] layerSuffixes = {
      "-api", "-core", "-facade", "-spi", "-common", "-commons", "-util", "-utils"
    };

    try {
      return Files.list(modulePath)
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .map(dir -> dir.getFileName().toString())
          .anyMatch(
              dirName -> {
                for (String suffix : layerSuffixes) {
                  if (dirName.endsWith(suffix)) {
                    return true;
                  }
                }
                return false;
              });
    } catch (IOException e) {
      return false;
    }
  }
}
