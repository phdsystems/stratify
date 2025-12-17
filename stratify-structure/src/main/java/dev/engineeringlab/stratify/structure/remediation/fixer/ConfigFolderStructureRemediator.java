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
 * Remediator for MS-021 and MS-022: YAML config vs folder structure mismatches.
 *
 * <p>Handles two types of fixes:
 *
 * <ul>
 *   <li>MS-021: Sync bootstrap.yaml with actual folder structure
 *   <li>MS-022: Fix module type (lib/feature) based on actual layers
 * </ul>
 *
 * <p>The fixer will:
 *
 * <ol>
 *   <li>Add missing modules to bootstrap.yaml
 *   <li>Remove non-existent modules from bootstrap.yaml
 *   <li>Correct module types based on actual layer structure
 *   <li>Update layer lists to match actual directories
 * </ol>
 */
public class ConfigFolderStructureRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-021", "MS-022"};
  private static final int PRIORITY = 85;
  private static final String CONFIG_FILE = "config/bootstrap.yaml";

  private static final Set<String> LIB_SUFFIXES =
      Set.of("-core", "-common", "-util", "-utils", "-commons");
  private static final Set<String> FEATURE_SUFFIXES = Set.of("-api", "-spi", "-core", "-facade");

  public ConfigFolderStructureRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "ConfigFolderStructureRemediator";
  }

  @Override
  public String getDescription() {
    return "Syncs bootstrap.yaml config with actual folder structure (MS-021, MS-022)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    String ruleId = violation.ruleId();
    if (!"MS-021".equals(ruleId) && !"MS-022".equals(ruleId)) {
      return FixResult.skipped(violation, "Not an MS-021 or MS-022 violation");
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
      if (Files.exists(configPath)) {
        config = loadYamlConfig(configPath);
      } else {
        config = createDefaultConfig(moduleRoot);
      }

      List<String> changes = new ArrayList<>();

      if ("MS-021".equals(ruleId)) {
        changes.addAll(syncSubmodules(config, moduleRoot, context));
      } else {
        changes.addAll(fixModuleTypes(config, moduleRoot, context));
      }

      if (changes.isEmpty()) {
        return FixResult.skipped(violation, "No changes needed");
      }

      if (context.dryRun()) {
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would update " + CONFIG_FILE)
            .diffs(changes)
            .build();
      }

      // Backup and write
      if (Files.exists(configPath)) {
        backup(configPath, context.projectRoot());
      }
      writeYamlConfig(configPath, config);
      context.log("Updated: %s", configPath);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Updated " + CONFIG_FILE + " to match folder structure")
          .modifiedFiles(List.of(configPath))
          .diffs(changes)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to fix config: " + e.getMessage());
    }
  }

  // loadYamlConfig() inherited from AbstractStructureFixer

  private void writeYamlConfig(Path configPath, Map<String, Object> config) throws IOException {
    String moduleName = configPath.getParent().getParent().getFileName().toString();
    String header = "# " + moduleName + " Configuration\n# Auto-synced with folder structure";
    writeYamlConfig(configPath, config, header);
  }

  private Map<String, Object> createDefaultConfig(Path moduleRoot) {
    Map<String, Object> config = new LinkedHashMap<>();

    // Module definition
    Map<String, Object> module = new LinkedHashMap<>();
    String moduleName = moduleRoot.getFileName().toString();
    module.put("name", moduleName);
    module.put("type", detectModuleType(moduleRoot));
    module.put("description", "Auto-generated configuration for " + moduleName);
    config.put("module", module);

    // Submodules
    config.put("submodules", new LinkedHashMap<String, Object>());

    return config;
  }

  private List<String> syncSubmodules(
      Map<String, Object> config, Path moduleRoot, FixerContext context) throws IOException {

    List<String> changes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    Map<String, Object> submodules = (Map<String, Object>) config.get("submodules");
    if (submodules == null) {
      submodules = new LinkedHashMap<>();
      config.put("submodules", submodules);
    }

    // Get actual directories with pom.xml
    Set<String> actualDirs = getDirectoriesWithPom(moduleRoot);

    // Remove configured modules that don't exist
    Iterator<String> it = submodules.keySet().iterator();
    while (it.hasNext()) {
      String configuredModule = it.next();
      if (!actualDirs.contains(configuredModule)) {
        it.remove();
        changes.add("- Removed non-existent submodule: " + configuredModule);
        context.log("Removed non-existent submodule: %s", configuredModule);
      }
    }

    // Add directories that aren't configured
    for (String actualDir : actualDirs) {
      if (!submodules.containsKey(actualDir) && !isIgnoredDirectory(actualDir)) {
        Map<String, Object> submoduleConfig = createSubmoduleConfig(moduleRoot.resolve(actualDir));
        submodules.put(actualDir, submoduleConfig);
        changes.add(
            "+ Added submodule: " + actualDir + " (type=" + submoduleConfig.get("type") + ")");
        context.log("Added submodule: %s", actualDir);
      }
    }

    return changes;
  }

  private List<String> fixModuleTypes(
      Map<String, Object> config, Path moduleRoot, FixerContext context) throws IOException {

    List<String> changes = new ArrayList<>();

    @SuppressWarnings("unchecked")
    Map<String, Object> submodules = (Map<String, Object>) config.get("submodules");
    if (submodules == null) {
      return changes;
    }

    for (Map.Entry<String, Object> entry : submodules.entrySet()) {
      String submoduleName = entry.getKey();
      @SuppressWarnings("unchecked")
      Map<String, Object> submoduleConfig = (Map<String, Object>) entry.getValue();

      if (submoduleConfig == null) {
        continue;
      }

      Path submodulePath = moduleRoot.resolve(submoduleName);
      if (!Files.exists(submodulePath)) {
        continue;
      }

      String currentType = (String) submoduleConfig.get("type");
      String correctType = detectModuleType(submodulePath);

      if (!correctType.equals(currentType)) {
        submoduleConfig.put("type", correctType);
        changes.add(
            "~ Changed type of " + submoduleName + ": " + currentType + " -> " + correctType);
        context.log("Changed type of %s: %s -> %s", submoduleName, currentType, correctType);

        // Update layers if feature type
        if ("feature".equals(correctType)) {
          List<String> layers = detectLayers(submodulePath);
          if (!layers.isEmpty()) {
            submoduleConfig.put("layers", layers);
            changes.add("  + Set layers: " + layers);
          }
        } else {
          submoduleConfig.remove("layers");
        }
      }
    }

    return changes;
  }

  private Map<String, Object> createSubmoduleConfig(Path submodulePath) {
    Map<String, Object> config = new LinkedHashMap<>();
    String type = detectModuleType(submodulePath);
    config.put("type", type);
    config.put("description", "Auto-detected " + type + " module");

    if ("feature".equals(type)) {
      List<String> layers = detectLayers(submodulePath);
      if (!layers.isEmpty()) {
        config.put("layers", layers);
      }
    }

    return config;
  }

  private String detectModuleType(Path modulePath) {
    // Check if it has feature layer subdirectories
    Set<String> subDirs = getDirectoriesWithPom(modulePath);

    boolean hasFeatureLayers =
        subDirs.stream()
            .anyMatch(
                dir ->
                    FEATURE_SUFFIXES.stream()
                        .anyMatch(suffix -> dir.endsWith(suffix) && !suffix.equals("-core")));

    if (hasFeatureLayers) {
      return "feature";
    }

    // Check if module name itself indicates lib type
    String moduleName = modulePath.getFileName().toString();
    for (String suffix : LIB_SUFFIXES) {
      if (moduleName.endsWith(suffix)) {
        return "lib";
      }
    }

    // Default to feature if has subdirectories, lib otherwise
    return subDirs.isEmpty() ? "lib" : "feature";
  }

  private List<String> detectLayers(Path modulePath) {
    List<String> layers = new ArrayList<>();
    Set<String> subDirs = getDirectoriesWithPom(modulePath);

    // Order: api, spi, core, facade
    String baseName = modulePath.getFileName().toString();
    for (String suffix : List.of("-api", "-spi", "-core", "-facade")) {
      String expectedDir = baseName + suffix;
      // Also check for shortened names (e.g., "design-api" instead of "design-scanner-api")
      for (String dir : subDirs) {
        if (dir.equals(expectedDir) || dir.endsWith(suffix)) {
          layers.add(dir);
          break;
        }
      }
    }

    return layers;
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

  private boolean isIgnoredDirectory(String dirName) {
    return dirName.equals("target")
        || dirName.equals("config")
        || dirName.equals("doc")
        || dirName.equals("docs")
        || dirName.startsWith(".");
  }

  // deriveModuleRoot() is inherited from AbstractStructureFixer
}
