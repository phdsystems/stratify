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
 * AG-003: Aggregator Modules Declared.
 *
 * <p>All module directories (folders containing pom.xml) within a pure aggregator MUST be declared
 * in the module.aggregator.yml configuration file.
 */
public class AG003AggregatorModulesDeclared extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.aggregator.yml";

  /** Directories to ignore when checking for undeclared modules. */
  private static final Set<String> IGNORED_DIRS =
      Set.of("config", "doc", "docs", "target", ".git", ".mvn", ".remediation");

  public AG003AggregatorModulesDeclared() {
    super("AG-003");
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
    // Only check if config file exists (AG-001 handles missing config)
    return Files.exists(module.getBasePath().resolve(CONFIG_FILE));
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    try {
      // Get declared modules from config
      Set<String> declaredModules = getDeclaredModules(configPath);

      // Get actual directories with pom.xml
      Set<String> actualModules = getDirectoriesWithPom(modulePath);

      // Check for undeclared modules (actual but not declared)
      for (String actualModule : actualModules) {
        if (!declaredModules.contains(actualModule) && !isIgnored(actualModule)) {
          violations.add(
              createViolation(
                  module,
                  String.format(
                      "Module directory '%s' has pom.xml but is not declared in %s. "
                          + "Add it to the modules list with appropriate type.",
                      actualModule, CONFIG_FILE),
                  modulePath.resolve(actualModule).toString()));
        }
      }

      // Check for declared modules that don't exist
      for (String declaredModule : declaredModules) {
        if (!actualModules.contains(declaredModule)) {
          Path missingPath = modulePath.resolve(declaredModule);
          if (!Files.exists(missingPath) || !Files.exists(missingPath.resolve("pom.xml"))) {
            violations.add(
                createViolation(
                    module,
                    String.format(
                        "Declared module '%s' does not exist or has no pom.xml. "
                            + "Create the module or remove it from %s.",
                        declaredModule, CONFIG_FILE),
                    configPath.toString()));
          }
        }
      }

    } catch (Exception e) {
      violations.add(
          createViolation(
              module,
              "Failed to validate module declarations: " + e.getMessage(),
              configPath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  private Set<String> getDeclaredModules(Path configPath) throws IOException {
    Set<String> modules = new HashSet<>();

    Yaml yaml = new Yaml();
    String content = Files.readString(configPath);
    Map<String, Object> config = yaml.load(content);

    if (config == null) {
      return modules;
    }

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> moduleList = (List<Map<String, Object>>) config.get("modules");
    if (moduleList != null) {
      for (Map<String, Object> moduleEntry : moduleList) {
        String name = (String) moduleEntry.get("name");
        if (name != null) {
          modules.add(name);
        }
      }
    }

    return modules;
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

  private boolean isIgnored(String dirName) {
    return IGNORED_DIRS.contains(dirName) || dirName.startsWith(".");
  }
}
