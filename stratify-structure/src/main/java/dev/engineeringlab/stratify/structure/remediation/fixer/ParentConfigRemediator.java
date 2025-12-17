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
 * Remediator for PA-001, PA-002, PA-003, and PA-004: Module parent configuration.
 *
 * <p>Handles the following fixes for parent aggregator modules:
 *
 * <ul>
 *   <li>PA-001: Creates module.parent.yml if missing
 *   <li>PA-002: Adds undeclared directories to module.parent.yml
 *   <li>PA-003: Syncs layers list with actual directory structure
 *   <li>PA-004: Validates layer naming (no fix needed - naming issues require manual rename)
 * </ul>
 */
public class ParentConfigRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"PA-001", "PA-002", "PA-003"};
  private static final int PRIORITY = 85;
  public static final String CONFIG_FILE = "config/module.parent.yml";

  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");
  private static final Set<String> IGNORED_DIRS =
      Set.of("config", "doc", "docs", "target", ".git", ".mvn", ".remediation");

  public ParentConfigRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ParentConfigRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates/syncs module.parent.yml for parent aggregator modules (PA-001, PA-002, PA-003)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not a PA-001, PA-002, or PA-003 violation");
    }

    try {
      // Derive moduleRoot from violation location, not context
      Path moduleRoot = deriveModuleRoot(violation, context);
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
        case "PA-001" -> {
          // Always sync layers and submodules for PA-001, even if file exists
          // The config file might be incomplete or out of sync
          if (isNew) {
            changes.add("+ Created " + CONFIG_FILE);
          }
          syncLayers(config, moduleRoot, context, changes);
          syncSubmodules(config, moduleRoot, context, changes);
        }
        case "PA-002" -> {
          // Add the undeclared directory from the violation location
          // Note: For PA-002, the violation location is the undeclared layer/submodule itself,
          // so we need to derive the parent module root from the location's parent
          String undeclaredDir = extractUndeclaredDirectory(violation);
          if (undeclaredDir != null) {
            // Re-derive moduleRoot as the parent of the violation location
            // because PA-002 violation location points to the undeclared dir, not the parent
            Path parentRoot = deriveParentModuleRoot(violation.location(), context);
            if (parentRoot != null && !parentRoot.equals(moduleRoot)) {
              moduleRoot = parentRoot;
              configPath = moduleRoot.resolve(CONFIG_FILE);
              if (!Files.exists(configPath.getParent())) {
                Files.createDirectories(configPath.getParent());
              }
              if (Files.exists(configPath)) {
                config = loadYamlConfig(configPath);
                isNew = false;
              } else {
                config = createDefaultConfig(moduleRoot);
                isNew = true;
              }
            }
            changes.addAll(addUndeclaredEntry(config, moduleRoot, undeclaredDir, context));
          }
        }
        case "PA-003" -> {
          syncLayers(config, moduleRoot, context, changes);
          syncSubmodules(config, moduleRoot, context, changes);
        }
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
    module.put("description", "Parent aggregator for " + moduleName);
    config.put("module", module);

    // Empty layers list
    config.put("layers", new ArrayList<String>());

    // Empty submodules list (optional)
    config.put("submodules", new ArrayList<Map<String, Object>>());

    return config;
  }

  private void syncLayers(
      Map<String, Object> config, Path moduleRoot, FixerContext context, List<String> changes)
      throws IOException {

    @SuppressWarnings("unchecked")
    List<String> layers = (List<String>) config.get("layers");
    if (layers == null) {
      layers = new ArrayList<>();
      config.put("layers", layers);
    }

    // Get actual layer directories
    Set<String> actualLayers = getLayerDirectories(moduleRoot);
    Set<String> declaredLayers = new HashSet<>(layers);

    // Remove layers that no longer exist
    layers.removeIf(
        layer -> {
          if (!actualLayers.contains(layer)) {
            changes.add("- Removed non-existent layer: " + layer);
            context.log("Removed non-existent layer: %s", layer);
            return true;
          }
          return false;
        });

    // Add layers that aren't declared (maintaining order: api, spi, common, core, facade)
    List<String> orderedLayers = orderLayers(actualLayers);
    for (String layer : orderedLayers) {
      if (!declaredLayers.contains(layer)) {
        layers.add(layer);
        changes.add("+ Added layer: " + layer);
        context.log("Added layer: %s", layer);
      }
    }

    // Re-order the layers list to maintain standard order
    List<String> finalOrdered = orderLayers(new HashSet<>(layers));
    layers.clear();
    layers.addAll(finalOrdered);
  }

  private void syncSubmodules(
      Map<String, Object> config, Path moduleRoot, FixerContext context, List<String> changes)
      throws IOException {

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
    if (submodules == null) {
      submodules = new ArrayList<>();
      config.put("submodules", submodules);
    }

    // Build set of declared submodule names
    Set<String> declaredNames = new HashSet<>();
    for (Map<String, Object> submodule : submodules) {
      String name = (String) submodule.get("name");
      if (name != null) {
        declaredNames.add(name);
      }
    }

    // Get actual non-layer directories with pom.xml
    Set<String> actualSubmodules = getNonLayerDirectories(moduleRoot);

    // Remove submodules that no longer exist
    submodules.removeIf(
        submodule -> {
          String name = (String) submodule.get("name");
          if (name != null && !actualSubmodules.contains(name)) {
            changes.add("- Removed non-existent submodule: " + name);
            context.log("Removed non-existent submodule: %s", name);
            return true;
          }
          return false;
        });

    // Add directories that aren't declared
    for (String actualSubmodule : actualSubmodules) {
      if (!declaredNames.contains(actualSubmodule) && !isIgnored(actualSubmodule)) {
        Map<String, Object> submoduleEntry = new LinkedHashMap<>();
        submoduleEntry.put("name", actualSubmodule);
        submoduleEntry.put("type", detectSubmoduleType(moduleRoot.resolve(actualSubmodule)));

        // If it's a parent type, detect its layers
        if ("parent".equals(submoduleEntry.get("type"))) {
          List<String> submoduleLayers =
              new ArrayList<>(
                  orderLayers(getLayerDirectories(moduleRoot.resolve(actualSubmodule))));
          if (!submoduleLayers.isEmpty()) {
            submoduleEntry.put("layers", submoduleLayers);
          }
        }

        submodules.add(submoduleEntry);
        changes.add("+ Added submodule: " + actualSubmodule);
        context.log("Added submodule: %s", actualSubmodule);
      }
    }
  }

  private String detectSubmoduleType(Path modulePath) {
    if (hasLayerSubmodules(modulePath)) {
      return "parent";
    }
    if (hasSubmodules(modulePath) && !hasSourceCode(modulePath)) {
      return "aggregator";
    }
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

  /** Gets directories that match layer naming pattern. */
  private Set<String> getLayerDirectories(Path modulePath) {
    Set<String> layers = new LinkedHashSet<>();
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
    Set<String> dirs = new LinkedHashSet<>();
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

  /** Orders layers in standard order: api, spi, common, core, facade. */
  private List<String> orderLayers(Set<String> layers) {
    List<String> ordered = new ArrayList<>();
    List<String> suffixOrder = List.of("-api", "-spi", "-common", "-core", "-facade");

    for (String suffix : suffixOrder) {
      for (String layer : layers) {
        if (layer.endsWith(suffix) && !ordered.contains(layer)) {
          ordered.add(layer);
        }
      }
    }

    // Add any remaining layers not matching standard suffixes
    for (String layer : layers) {
      if (!ordered.contains(layer)) {
        ordered.add(layer);
      }
    }

    return ordered;
  }

  private boolean isIgnored(String dirName) {
    return IGNORED_DIRS.contains(dirName) || dirName.startsWith(".");
  }

  // deriveModuleRoot() is inherited from AbstractStructureFixer

  /** Extracts the undeclared directory name from a PA-002 violation. */
  private String extractUndeclaredDirectory(StructureViolation violation) {
    if (violation.location() == null) {
      return null;
    }
    Path location = violation.location();
    return location.getFileName().toString();
  }

  /**
   * Derives the parent module root from a violation location. For PA-002 violations, the location
   * points to the undeclared layer/submodule, so we need to get the parent directory as the actual
   * module root.
   */
  private Path deriveParentModuleRoot(Path location, FixerContext context) {
    if (location == null) {
      return null;
    }

    // Make location absolute if relative
    if (!location.isAbsolute()) {
      location = context.projectRoot().resolve(location);
    }

    // Get the parent directory - this should be the actual module root
    Path parent = location.getParent();
    if (parent != null && Files.isDirectory(parent) && Files.exists(parent.resolve("pom.xml"))) {
      return parent;
    }

    return null;
  }

  /** Adds a single undeclared entry to the config (as layer or submodule). */
  private List<String> addUndeclaredEntry(
      Map<String, Object> config, Path moduleRoot, String dirName, FixerContext context) {

    List<String> changes = new ArrayList<>();

    // Check if it's a layer (ends with layer suffix)
    boolean isLayer = false;
    for (String suffix : LAYER_SUFFIXES) {
      if (dirName.endsWith(suffix)) {
        isLayer = true;
        break;
      }
    }

    if (isLayer) {
      // Add as layer
      @SuppressWarnings("unchecked")
      List<String> layers = (List<String>) config.get("layers");
      if (layers == null) {
        layers = new ArrayList<>();
        config.put("layers", layers);
      }

      if (!layers.contains(dirName)) {
        layers.add(dirName);
        // Re-order layers
        List<String> ordered = orderLayers(new HashSet<>(layers));
        layers.clear();
        layers.addAll(ordered);
        changes.add("+ Added layer: " + dirName);
        context.log("Added undeclared layer: %s", dirName);
      }
    } else {
      // Add as submodule
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
      if (submodules == null) {
        submodules = new ArrayList<>();
        config.put("submodules", submodules);
      }

      boolean alreadyDeclared = submodules.stream().anyMatch(m -> dirName.equals(m.get("name")));

      if (!alreadyDeclared) {
        Map<String, Object> submoduleEntry = new LinkedHashMap<>();
        submoduleEntry.put("name", dirName);
        submoduleEntry.put("type", detectSubmoduleType(moduleRoot.resolve(dirName)));
        submodules.add(submoduleEntry);
        changes.add("+ Added submodule: " + dirName);
        context.log("Added undeclared submodule: %s", dirName);
      }
    }

    return changes;
  }

  // loadYamlConfig() inherited from AbstractStructureFixer

  private void writeYamlConfig(Path configPath, Map<String, Object> config) throws IOException {
    writeYamlConfigWithModuleHeader(configPath, config, "Parent");
  }
}
