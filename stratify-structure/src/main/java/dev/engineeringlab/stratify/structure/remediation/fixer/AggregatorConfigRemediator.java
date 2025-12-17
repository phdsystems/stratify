package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

/**
 * Remediator for AG-001, AG-002, AG-003, and AG-004: Module aggregator configuration.
 *
 * <p>Handles the following fixes for pure aggregator modules:
 *
 * <ul>
 *   <li>AG-001: Creates module.aggregator.yml if missing
 *   <li>AG-002: Adds undeclared directories to module.aggregator.yml
 *   <li>AG-003: Syncs modules list with actual directory structure
 *   <li>AG-004: Corrects module types based on actual structure
 * </ul>
 */
public class AggregatorConfigRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"AG-001", "AG-002", "AG-003", "AG-004"};
  private static final int PRIORITY = 85;
  public static final String CONFIG_FILE = "config/module.aggregator.yml";

  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");
  private static final Set<String> LIBRARY_SUFFIXES =
      Set.of("-common", "-util", "-utils", "-commons");
  private static final Set<String> IGNORED_DIRS =
      Set.of("config", "doc", "docs", "target", ".git", ".mvn", ".remediation");

  public AggregatorConfigRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "AggregatorConfigRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates/syncs module.aggregator.yml for pure aggregator modules (AG-001, AG-002, AG-003, AG-004)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an AG-001, AG-002, AG-003, or AG-004 violation");
    }

    try {
      // Derive moduleRoot from violation location, not context
      Path moduleRoot = deriveModuleRoot(violation, context);

      // For AG-002, the violation location is the undeclared child directory.
      // We need to go up to the parent (the aggregator) to update its config.
      if ("AG-002".equals(ruleId) && violation.location() != null) {
        Path undeclaredDir = violation.location();
        if (!undeclaredDir.isAbsolute()) {
          undeclaredDir = context.projectRoot().resolve(undeclaredDir);
        }
        // If the undeclared directory itself is a module (has pom.xml),
        // the aggregator is its parent
        if (Files.exists(undeclaredDir.resolve("pom.xml"))) {
          Path parent = undeclaredDir.getParent();
          // Check if parent is an aggregator (has pom.xml or config file)
          if (parent != null
              && (Files.exists(parent.resolve("pom.xml"))
                  || Files.exists(parent.resolve(CONFIG_FILE)))) {
            moduleRoot = parent;
          }
        }
      }

      Path configPath = moduleRoot.resolve(CONFIG_FILE);

      // Ensure config directory exists
      if (!Files.exists(configPath.getParent())) {
        Files.createDirectories(configPath.getParent());
      }

      Map<String, Object> config;
      boolean isNew = !Files.exists(configPath);

      if (isNew) {
        config = createDefaultConfig(moduleRoot);
      } else {
        config = loadYamlConfig(configPath);
      }

      List<String> changes = new ArrayList<>();

      switch (ruleId) {
        case "AG-001" -> {
          if (isNew) {
            changes.add("+ Created " + CONFIG_FILE);
            syncModules(config, moduleRoot, context, changes);
          }
        }
        case "AG-002" -> {
          // Add the undeclared directory from the violation location
          String undeclaredDir = extractUndeclaredDirectory(violation);
          if (undeclaredDir != null) {
            changes.addAll(addUndeclaredModule(config, moduleRoot, undeclaredDir, context));
          }
        }
        case "AG-003" -> changes.addAll(syncModules(config, moduleRoot, context, changes));
        case "AG-004" -> changes.addAll(fixModuleTypes(config, moduleRoot, context));
      }

      if (changes.isEmpty()) {
        return FixResult.skipped(violation, "No changes needed");
      }

      if (context.dryRun()) {
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would " + (isNew ? "create" : "update") + " " + CONFIG_FILE)
            .diffs(changes)
            .build();
      }

      // Backup existing file
      if (!isNew) {
        backup(configPath, context.projectRoot());
      }

      writeYamlConfig(configPath, config);
      context.log("%s: %s", isNew ? "Created" : "Updated", configPath);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description((isNew ? "Created" : "Updated") + " " + CONFIG_FILE)
          .modifiedFiles(List.of(configPath))
          .diffs(changes)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to fix: " + e.getMessage());
    }
  }

  private Map<String, Object> createDefaultConfig(Path moduleRoot) {
    Map<String, Object> config = new LinkedHashMap<>();

    // Module definition
    Map<String, Object> module = new LinkedHashMap<>();
    String moduleName = moduleRoot.getFileName().toString();
    module.put("name", moduleName);
    module.put("description", "Pure aggregator for " + moduleName);
    config.put("module", module);

    // Empty modules list
    config.put("modules", new ArrayList<Map<String, Object>>());

    return config;
  }

  private List<String> syncModules(
      Map<String, Object> config, Path moduleRoot, FixerContext context, List<String> changes)
      throws IOException {

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> modules = (List<Map<String, Object>>) config.get("modules");
    if (modules == null) {
      modules = new ArrayList<>();
      config.put("modules", modules);
    }

    // Build set of declared module names
    Set<String> declaredNames = new HashSet<>();
    for (Map<String, Object> module : modules) {
      String name = (String) module.get("name");
      if (name != null) {
        declaredNames.add(name);
      }
    }

    // Get actual directories with pom.xml
    Set<String> actualDirs = getDirectoriesWithPom(moduleRoot);

    // Remove modules that no longer exist
    modules.removeIf(
        module -> {
          String name = (String) module.get("name");
          if (name != null && !actualDirs.contains(name)) {
            changes.add("- Removed non-existent module: " + name);
            context.log("Removed non-existent module: %s", name);
            return true;
          }
          return false;
        });

    // Add directories that aren't declared
    for (String actualDir : actualDirs) {
      if (!declaredNames.contains(actualDir) && !isIgnored(actualDir)) {
        String type = detectModuleType(moduleRoot.resolve(actualDir), actualDir);
        Map<String, Object> moduleEntry = new LinkedHashMap<>();
        moduleEntry.put("name", actualDir);
        moduleEntry.put("type", type);
        modules.add(moduleEntry);
        changes.add("+ Added module: " + actualDir + " (type=" + type + ")");
        context.log("Added module: %s (type=%s)", actualDir, type);
      }
    }

    return changes;
  }

  private List<String> fixModuleTypes(
      Map<String, Object> config, Path moduleRoot, FixerContext context) {

    List<String> changes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> modules = (List<Map<String, Object>>) config.get("modules");
    if (modules == null) {
      return changes;
    }

    for (Map<String, Object> moduleEntry : modules) {
      String name = (String) moduleEntry.get("name");
      String declaredType = (String) moduleEntry.get("type");

      if (name == null) {
        continue;
      }

      Path childPath = moduleRoot.resolve(name);
      if (!Files.exists(childPath)) {
        continue;
      }

      String actualType = detectModuleType(childPath, name);

      if (declaredType == null || !declaredType.equals(actualType)) {
        moduleEntry.put("type", actualType);
        changes.add(
            "~ Changed type of "
                + name
                + ": "
                + (declaredType != null ? declaredType : "null")
                + " -> "
                + actualType);
        context.log("Changed type of %s: %s -> %s", name, declaredType, actualType);
      }
    }

    return changes;
  }

  /** Detects the type of a module based on its structure. */
  private String detectModuleType(Path modulePath, String moduleName) {
    if (!Files.exists(modulePath.resolve("pom.xml"))) {
      return "leaf";
    }

    // Check for layer submodules (parent type)
    if (hasLayerSubmodules(modulePath)) {
      return "parent";
    }

    // Check for nested modules without layers (aggregator type)
    if (hasSubmodules(modulePath) && !hasSourceCode(modulePath)) {
      return "aggregator";
    }

    // Check for library pattern
    for (String suffix : LIBRARY_SUFFIXES) {
      if (moduleName.endsWith(suffix)) {
        return "library";
      }
    }

    // Has source code or is standalone module
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

  private Set<String> getDirectoriesWithPom(Path modulePath) {
    Set<String> dirs = new LinkedHashSet<>();
    try (Stream<Path> stream = Files.list(modulePath)) {
      stream
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .map(dir -> dir.getFileName().toString())
          .sorted()
          .forEach(dirs::add);
    } catch (IOException e) {
      // Return empty set on error
    }
    return dirs;
  }

  private boolean isIgnored(String dirName) {
    return IGNORED_DIRS.contains(dirName) || dirName.startsWith(".");
  }

  // deriveModuleRoot() is inherited from AbstractStructureFixer

  /**
   * Extracts the undeclared directory name from an AG-002 violation. The violation location
   * contains the path to the undeclared directory.
   */
  private String extractUndeclaredDirectory(StructureViolation violation) {
    if (violation.location() == null) {
      return null;
    }
    Path location = violation.location();
    return location.getFileName().toString();
  }

  /** Adds a single undeclared module to the config. */
  private List<String> addUndeclaredModule(
      Map<String, Object> config, Path moduleRoot, String dirName, FixerContext context) {

    List<String> changes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> modules = (List<Map<String, Object>>) config.get("modules");
    if (modules == null) {
      modules = new ArrayList<>();
      config.put("modules", modules);
    }

    // Check if already declared
    boolean alreadyDeclared = modules.stream().anyMatch(m -> dirName.equals(m.get("name")));

    if (alreadyDeclared) {
      return changes;
    }

    // Detect type and add
    Path childPath = moduleRoot.resolve(dirName);
    String type = detectModuleType(childPath, dirName);

    Map<String, Object> moduleEntry = new LinkedHashMap<>();
    moduleEntry.put("name", dirName);
    moduleEntry.put("type", type);
    modules.add(moduleEntry);

    changes.add("+ Added module: " + dirName + " (type=" + type + ")");
    context.log("Added undeclared module: %s (type=%s)", dirName, type);

    return changes;
  }

  // loadYamlConfig() inherited from AbstractStructureFixer

  private void writeYamlConfig(Path configPath, Map<String, Object> config) throws IOException {
    writeYamlConfigWithModuleHeader(configPath, config, "Aggregator");
  }
}
