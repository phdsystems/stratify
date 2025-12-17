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
 * PA-002: Parent Allowed Contents.
 *
 * <p>Parent aggregator modules can ONLY contain:
 *
 * <ul>
 *   <li>config/ - Configuration files
 *   <li>doc/ or docs/ - Documentation
 *   <li>pom.xml - Maven configuration
 *   <li>README.md - Module overview
 *   <li>Declared layers from module.parent.yml
 *   <li>Declared submodules from module.parent.yml
 * </ul>
 */
public class PA002ParentAllowedContents extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.parent.yml";

  /** Files/directories always allowed in parent aggregators. */
  private static final Set<String> ALWAYS_ALLOWED =
      Set.of(
          "config",
          "doc",
          "docs",
          "pom.xml",
          "README.md",
          "LICENSE",
          "CHANGELOG.md",
          ".git",
          ".gitignore",
          ".gitattributes",
          ".mvn",
          ".remediation",
          "target");

  public PA002ParentAllowedContents() {
    super("PA-002");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent aggregator modules (has layer submodules)
    if (!module.isParent()) {
      return false;
    }
    // Check if has any standard layer modules
    return module.hasApiModule()
        || module.hasCoreModule()
        || module.hasFacadeModule()
        || module.hasSpiModule()
        || module.hasCommonModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    // Get declared layers and submodules from config
    Set<String> declaredItems = getDeclaredItems(configPath);

    // Check all entries in the directory
    try (Stream<Path> entries = Files.list(modulePath)) {
      entries.forEach(
          entry -> {
            String name = entry.getFileName().toString();

            // Skip if always allowed
            if (ALWAYS_ALLOWED.contains(name)) {
              return;
            }

            // Skip hidden files/directories
            if (name.startsWith(".")) {
              return;
            }

            // Check if it's a declared layer or submodule
            if (!declaredItems.contains(name)) {
              String type = Files.isDirectory(entry) ? "Directory" : "File";
              violations.add(
                  createViolation(
                      module,
                      String.format(
                          "%s '%s' is not allowed in parent aggregator. "
                              + "Either declare it in %s (as layer or submodule) or remove it.",
                          type, name, CONFIG_FILE),
                      entry.toString()));
            }
          });
    } catch (IOException e) {
      violations.add(
          createViolation(
              module,
              "Failed to list directory contents: " + e.getMessage(),
              modulePath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  /** Gets all declared layers and submodules from the config file. */
  private Set<String> getDeclaredItems(Path configPath) {
    Set<String> items = new HashSet<>();

    if (!Files.exists(configPath)) {
      return items;
    }

    try {
      Yaml yaml = new Yaml();
      String content = Files.readString(configPath);
      Map<String, Object> config = yaml.load(content);

      if (config == null) {
        return items;
      }

      // Get declared layers
      @SuppressWarnings("unchecked")
      List<String> layers = (List<String>) config.get("layers");
      if (layers != null) {
        items.addAll(layers);
      }

      // Get declared submodules
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
      if (submodules != null) {
        for (Map<String, Object> submodule : submodules) {
          String name = (String) submodule.get("name");
          if (name != null) {
            items.add(name);
          }
        }
      }

    } catch (Exception e) {
      // Return empty set on error - PA-001 will catch config issues
    }

    return items;
  }
}
