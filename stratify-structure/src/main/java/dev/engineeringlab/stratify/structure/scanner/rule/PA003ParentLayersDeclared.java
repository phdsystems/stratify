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
 * PA-003: Parent Layers Declared.
 *
 * <p>All layer directories within a parent aggregator MUST be declared in the module.parent.yml
 * configuration file. Layers follow the naming pattern {parent-name}-{layer} where layer is one of:
 * api, spi, core, facade, common.
 */
public class PA003ParentLayersDeclared extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.parent.yml";

  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");

  /** Directories to ignore when checking for undeclared layers. */
  private static final Set<String> IGNORED_DIRS =
      Set.of("config", "doc", "docs", "target", ".git", ".mvn", ".remediation");

  public PA003ParentLayersDeclared() {
    super("PA-003");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent aggregator modules with a config file
    if (!module.isParent()) {
      return false;
    }
    // Check if has any standard layer modules
    boolean hasLayers =
        module.hasApiModule()
            || module.hasCoreModule()
            || module.hasFacadeModule()
            || module.hasSpiModule()
            || module.hasCommonModule();
    if (!hasLayers) {
      return false;
    }
    // Only check if config file exists (PA-001 handles missing config)
    return Files.exists(module.getBasePath().resolve(CONFIG_FILE));
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    try {
      // Get declared layers and submodules from config
      ConfigInfo configInfo = parseConfig(configPath);

      // Get actual layer directories
      Set<String> actualLayers = getLayerDirectories(modulePath);

      // Get actual non-layer directories with pom.xml (potential submodules)
      Set<String> actualSubmodules = getNonLayerDirectories(modulePath);

      // Check for undeclared layers
      for (String actualLayer : actualLayers) {
        if (!configInfo.layers.contains(actualLayer)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Layer directory '%s' is not declared in %s. " + "Add it to the layers list.",
                      actualLayer, CONFIG_FILE),
                  modulePath.resolve(actualLayer).toString()));
        }
      }

      // Check for declared layers that don't exist
      for (String declaredLayer : configInfo.layers) {
        if (!actualLayers.contains(declaredLayer)) {
          Path missingPath = modulePath.resolve(declaredLayer);
          if (!Files.exists(missingPath) || !Files.exists(missingPath.resolve("pom.xml"))) {
            violations.add(
                createViolation(
                    module,
                    String.format(
                        "Declared layer '%s' does not exist or has no pom.xml. "
                            + "Create the layer module or remove it from %s.",
                        declaredLayer, CONFIG_FILE),
                    configPath.toString()));
          }
        }
      }

      // Check for undeclared submodules (non-layer directories with pom.xml)
      for (String actualSubmodule : actualSubmodules) {
        if (!configInfo.submodules.contains(actualSubmodule) && !isIgnored(actualSubmodule)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Directory '%s' has pom.xml but is not declared in %s. "
                          + "Add it to the submodules list or layers list.",
                      actualSubmodule, CONFIG_FILE),
                  modulePath.resolve(actualSubmodule).toString()));
        }
      }

    } catch (Exception e) {
      violations.add(
          createViolation(
              module,
              "Failed to validate layer declarations: " + e.getMessage(),
              configPath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  private ConfigInfo parseConfig(Path configPath) throws IOException {
    ConfigInfo info = new ConfigInfo();

    Yaml yaml = new Yaml();
    String content = Files.readString(configPath);
    Map<String, Object> config = yaml.load(content);

    if (config == null) {
      return info;
    }

    // Get declared layers
    @SuppressWarnings("unchecked")
    List<String> layers = (List<String>) config.get("layers");
    if (layers != null) {
      info.layers.addAll(layers);
    }

    // Get declared submodules
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
    if (submodules != null) {
      for (Map<String, Object> submodule : submodules) {
        String name = (String) submodule.get("name");
        if (name != null) {
          info.submodules.add(name);
        }
      }
    }

    return info;
  }

  /** Gets directories that match layer naming pattern (ending with -api, -spi, etc.) */
  private Set<String> getLayerDirectories(Path modulePath) {
    Set<String> layers = new HashSet<>();
    try (Stream<Path> stream = Files.list(modulePath)) {
      stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .forEach(
              dir -> {
                String name = dir.getFileName().toString();
                for (String suffix : LAYER_SUFFIXES) {
                  if (name.endsWith(suffix)) {
                    layers.add(name);
                    break;
                  }
                }
              });
    } catch (IOException e) {
      // Return empty set on error
    }
    return layers;
  }

  /** Gets directories with pom.xml that don't match layer naming pattern. */
  private Set<String> getNonLayerDirectories(Path modulePath) {
    Set<String> dirs = new HashSet<>();
    try (Stream<Path> stream = Files.list(modulePath)) {
      stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .forEach(
              dir -> {
                String name = dir.getFileName().toString();
                boolean isLayer = false;
                for (String suffix : LAYER_SUFFIXES) {
                  if (name.endsWith(suffix)) {
                    isLayer = true;
                    break;
                  }
                }
                if (!isLayer) {
                  dirs.add(name);
                }
              });
    } catch (IOException e) {
      // Return empty set on error
    }
    return dirs;
  }

  private boolean isIgnored(String dirName) {
    return IGNORED_DIRS.contains(dirName) || dirName.startsWith(".");
  }

  private static class ConfigInfo {
    Set<String> layers = new HashSet<>();
    Set<String> submodules = new HashSet<>();
  }
}
