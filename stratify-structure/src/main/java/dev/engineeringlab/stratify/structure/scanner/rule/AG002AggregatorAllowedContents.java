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
 * AG-002: Aggregator Allowed Contents.
 *
 * <p>Pure aggregator modules can ONLY contain:
 *
 * <ul>
 *   <li>config/ - Configuration files
 *   <li>doc/ or docs/ - Documentation
 *   <li>pom.xml - Maven configuration
 *   <li>README.md - Module overview
 *   <li>Declared modules from module.aggregator.yml
 * </ul>
 */
public class AG002AggregatorAllowedContents extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.aggregator.yml";

  /** Files/directories always allowed in pure aggregators. */
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

  public AG002AggregatorAllowedContents() {
    super("AG-002");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to pure aggregator modules
    if (!module.isParent()) {
      return false;
    }
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    return !module.hasAnyLayerModules();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path configPath = modulePath.resolve(CONFIG_FILE);

    // Get declared modules from config
    Set<String> declaredModules = getDeclaredModules(configPath);

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

            // Check if it's a declared module
            if (!declaredModules.contains(name)) {
              String type = Files.isDirectory(entry) ? "Directory" : "File";
              violations.add(
                  createViolation(
                      module,
                      String.format(
                          "%s '%s' is not allowed in pure aggregator. "
                              + "Either declare it in %s or remove it.",
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

  private Set<String> getDeclaredModules(Path configPath) {
    Set<String> modules = new HashSet<>();

    if (!Files.exists(configPath)) {
      return modules;
    }

    try {
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
    } catch (Exception e) {
      // Return empty set on error - AG-001 will catch config issues
    }

    return modules;
  }
}
