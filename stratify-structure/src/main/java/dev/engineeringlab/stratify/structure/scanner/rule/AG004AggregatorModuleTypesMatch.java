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
 * AG-004: Aggregator Module Types Match.
 *
 * <p>Module types declared in module.aggregator.yml must match the actual structure:
 *
 * <ul>
 *   <li>aggregator: Pure aggregator with -aggregator suffix, no layer submodules
 *   <li>parent: Parent module with layer submodules (api/spi/core/facade)
 *   <li>leaf: Standalone module with source code, no submodules
 *   <li>library: Utility module (-common, -util, -utils, -commons)
 * </ul>
 */
public class AG004AggregatorModuleTypesMatch extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.aggregator.yml";

  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");
  private static final Set<String> LIBRARY_SUFFIXES =
      Set.of("-common", "-util", "-utils", "-commons");

  public AG004AggregatorModuleTypesMatch() {
    super("AG-004");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to pure aggregator modules with a config file
    if (!module.isParent()) {
      return false;
    }
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    if (module.hasAnyLayerModules()) {
      return false;
    }
    return Files.exists(module.getBasePath().resolve(CONFIG_FILE));
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    try {
      Yaml yaml = new Yaml();
      String content = Files.readString(configPath);
      Map<String, Object> config = yaml.load(content);

      if (config == null) {
        return violations;
      }

      @SuppressWarnings("unchecked")
      List<Map<String, Object>> moduleList = (List<Map<String, Object>>) config.get("modules");
      if (moduleList == null) {
        return violations;
      }

      for (Map<String, Object> moduleEntry : moduleList) {
        String name = (String) moduleEntry.get("name");
        String declaredType = (String) moduleEntry.get("type");

        if (name == null || declaredType == null) {
          continue;
        }

        Path childPath = modulePath.resolve(name);
        if (!Files.exists(childPath)) {
          continue; // AG-003 handles missing modules
        }

        String actualType = detectModuleType(childPath, name);

        if (!declaredType.equals(actualType)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Module '%s' is declared as '%s' but actual structure indicates '%s'. "
                          + "Update the type in %s or restructure the module.",
                      name, declaredType, actualType, CONFIG_FILE),
                  childPath.toString()));
        }
      }

    } catch (Exception e) {
      violations.add(
          createViolation(
              module, "Failed to validate module types: " + e.getMessage(), configPath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  /**
   * Detects the actual type of a module based on its structure.
   *
   * @param modulePath the path to the module
   * @param moduleName the name of the module
   * @return the detected type: "aggregator", "parent", "leaf", or "library"
   */
  private String detectModuleType(Path modulePath, String moduleName) {
    // Check if it has pom.xml
    if (!Files.exists(modulePath.resolve("pom.xml"))) {
      return "leaf"; // No pom.xml, treat as leaf
    }

    // Check for layer submodules (parent type)
    if (hasLayerSubmodules(modulePath)) {
      return "parent";
    }

    // Check for nested modules without layers (aggregator type)
    if (hasSubmodules(modulePath) && !hasSourceCode(modulePath)) {
      // Has submodules but no src - check if it's a pure aggregator
      if (moduleName.endsWith("-aggregator")) {
        return "aggregator";
      }
      // Has submodules without layer pattern - could be nested aggregator
      return "aggregator";
    }

    // Check for library pattern
    for (String suffix : LIBRARY_SUFFIXES) {
      if (moduleName.endsWith(suffix)) {
        return "library";
      }
    }

    // Check for source code (leaf type)
    if (hasSourceCode(modulePath)) {
      return "leaf";
    }

    // Default to leaf for standalone modules
    return "leaf";
  }

  private boolean hasLayerSubmodules(Path modulePath) {
    try (Stream<Path> stream = Files.list(modulePath)) {
      return stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .anyMatch(
              dir -> {
                String name = dir.getFileName().toString();
                for (String suffix : LAYER_SUFFIXES) {
                  if (name.endsWith(suffix)) {
                    return true;
                  }
                }
                return false;
              });
    } catch (IOException e) {
      return false;
    }
  }

  private boolean hasSubmodules(Path modulePath) {
    try (Stream<Path> stream = Files.list(modulePath)) {
      return stream
          .filter(Files::isDirectory)
          .anyMatch(dir -> Files.exists(dir.resolve("pom.xml")));
    } catch (IOException e) {
      return false;
    }
  }

  private boolean hasSourceCode(Path modulePath) {
    return Files.exists(modulePath.resolve("src/main/java"))
        || Files.exists(modulePath.resolve("src/test/java"));
  }
}
