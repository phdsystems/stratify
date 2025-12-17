package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;
import org.yaml.snakeyaml.Yaml;

/**
 * MS-021: YAML Config Matches Folder Structure.
 *
 * <p>Validates that bootstrap.yaml configuration accurately reflects the actual folder and module
 * structure. Detects:
 *
 * <ul>
 *   <li>Configured submodules that don't exist as directories
 *   <li>Directories with pom.xml that aren't configured
 *   <li>Module type mismatches (lib vs feature)
 * </ul>
 */
public class MS021ConfigMatchesFolderStructure extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/bootstrap.yaml";
  private static final Set<String> LIB_LAYERS =
      Set.of("core", "common", "util", "utils", "commons");
  private static final Set<String> FEATURE_LAYERS = Set.of("api", "spi", "core", "facade");

  public MS021ConfigMatchesFolderStructure() {
    super("MS-021");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent/aggregator modules that have a config/bootstrap.yaml
    if (!module.isParent()) {
      return false;
    }
    Path configPath = module.getBasePath().resolve(CONFIG_FILE);
    return Files.exists(configPath);
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    try {
      Map<String, Object> config = loadYamlConfig(configPath);
      if (config == null) {
        return List.of();
      }

      // Get configured submodules
      @SuppressWarnings("unchecked")
      Map<String, Object> submodules = (Map<String, Object>) config.get("submodules");
      if (submodules == null) {
        submodules = new java.util.HashMap<>();
      }

      // Get actual directories with pom.xml
      Set<String> actualDirs = getDirectoriesWithPom(modulePath);

      // Check 1: Configured modules that don't exist
      for (String configuredModule : submodules.keySet()) {
        if (!actualDirs.contains(configuredModule)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Configured submodule '%s' does not exist as a directory with pom.xml",
                      configuredModule),
                  configPath.toString()));
        }
      }

      // Check 2: Directories with pom.xml that aren't configured
      for (String actualDir : actualDirs) {
        if (!submodules.containsKey(actualDir) && !isIgnoredDirectory(actualDir)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Directory '%s' has pom.xml but is not configured in bootstrap.yaml",
                      actualDir),
                  modulePath.resolve(actualDir).toString()));
        }
      }

      // Check 3: Module type validation
      violations.addAll(validateModuleTypes(module, submodules, modulePath));

    } catch (Exception e) {
      violations.add(
          createViolation(
              module, "Failed to parse bootstrap.yaml: " + e.getMessage(), configPath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  private Map<String, Object> loadYamlConfig(Path configPath) throws IOException {
    Yaml yaml = new Yaml();
    String content = Files.readString(configPath);
    return yaml.load(content);
  }

  private Set<String> getDirectoriesWithPom(Path modulePath) {
    Set<String> dirs = new HashSet<>();
    try (Stream<Path> stream = Files.list(modulePath)) {
      stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .forEach(dir -> dirs.add(dir.getFileName().toString()));
    } catch (IOException e) {
      // Return empty set on error
    }
    return dirs;
  }

  private boolean isIgnoredDirectory(String dirName) {
    // Ignore common non-module directories
    return dirName.equals("target")
        || dirName.equals("config")
        || dirName.equals("doc")
        || dirName.equals("docs")
        || dirName.startsWith(".");
  }

  private List<Violation> validateModuleTypes(
      ModuleInfo module, Map<String, Object> submodules, Path modulePath) {

    List<Violation> violations = new ArrayList<>();

    for (Map.Entry<String, Object> entry : submodules.entrySet()) {
      String submoduleName = entry.getKey();
      @SuppressWarnings("unchecked")
      Map<String, Object> submoduleConfig = (Map<String, Object>) entry.getValue();

      if (submoduleConfig == null) {
        continue;
      }

      String type = (String) submoduleConfig.get("type");
      if (type == null) {
        continue;
      }

      Path submodulePath = modulePath.resolve(submoduleName);
      if (!Files.exists(submodulePath)) {
        continue; // Already reported above
      }

      Set<String> actualLayers = getSubmoduleLayers(submodulePath);

      if ("lib".equals(type)) {
        // Lib modules should not have feature layers (api, spi, facade)
        // actualLayers contains just suffixes (e.g., "api", "core")
        Set<String> featureLayersFound = new HashSet<>(actualLayers);
        featureLayersFound.retainAll(Set.of("api", "spi", "facade"));
        if (!featureLayersFound.isEmpty()) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Module '%s' is configured as 'lib' but has feature layers: %s. "
                          + "Change type to 'feature' or remove the layers.",
                      submoduleName, featureLayersFound),
                  submodulePath.toString()));
        }
      } else if ("feature".equals(type)) {
        // Feature modules should have the configured layers
        @SuppressWarnings("unchecked")
        List<String> configuredLayers = (List<String>) submoduleConfig.get("layers");
        if (configuredLayers != null) {
          for (String layer : configuredLayers) {
            String layerDir = extractLayerDir(layer, submoduleName);
            // Check if the directory exists
            if (!Files.exists(submodulePath.resolve(layerDir))) {
              violations.add(
                  createViolation(
                      module,
                      String.format(
                          "Module '%s' is configured with layer '%s' but directory does not exist",
                          submoduleName, layer),
                      submodulePath.toString()));
            }
          }
        }
      }
    }

    return violations;
  }

  private Set<String> getSubmoduleLayers(Path submodulePath) {
    Set<String> layers = new HashSet<>();
    try (Stream<Path> stream = Files.list(submodulePath)) {
      stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .forEach(
              dir -> {
                String name = dir.getFileName().toString();
                // Extract layer suffix (e.g., "test-lib-api" -> "api")
                for (String layer : FEATURE_LAYERS) {
                  if (name.endsWith("-" + layer)) {
                    layers.add(layer); // Add just the suffix, not the full name
                    break;
                  }
                }
                for (String layer : LIB_LAYERS) {
                  if (name.endsWith("-" + layer)) {
                    layers.add(layer); // Add just the suffix, not the full name
                    break;
                  }
                }
              });
    } catch (IOException e) {
      // Return empty set on error
    }
    return layers;
  }

  private String extractLayerDir(String layerName, String submoduleName) {
    // Layer name might be full (e.g., "ms-scanner-api") or just suffix ("api")
    if (layerName.contains("-")) {
      return layerName;
    }
    return submoduleName + "-" + layerName;
  }
}
