package dev.engineeringlab.stratify.structure.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.Yaml;

/**
 * Configuration for common dependencies that must be managed via dependencyManagement.
 *
 * <p>Loads configuration from {@code config/common-dependencies.yaml} at the project root, which
 * defines:
 *
 * <ul>
 *   <li>Common dependencies that should be in BOM/dependencyManagement
 *   <li>Default versions for auto-BOM generation
 *   <li>BOM generation settings
 * </ul>
 */
public class DependencyConfig {

  private static final String CONFIG_DIR = "config";
  private static final String CONFIG_FILE = "common-dependencies.yaml";
  private static DependencyConfig instance;
  private static Path projectRoot;

  private final Set<String> commonDependencyKeys;
  private final Map<String, CommonDependency> commonDependencies;
  private final BomGenerationConfig bomConfig;

  /** Represents a common dependency from config. */
  public static class CommonDependency {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope;
    private final String description;

    public CommonDependency(
        String groupId, String artifactId, String version, String scope, String description) {
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.scope = scope;
      this.description = description;
    }

    public String getGroupId() {
      return groupId;
    }

    public String getArtifactId() {
      return artifactId;
    }

    public String getVersion() {
      return version;
    }

    public String getScope() {
      return scope;
    }

    public String getDescription() {
      return description;
    }

    public String getKey() {
      return groupId + ":" + artifactId;
    }
  }

  /** Configuration for auto-BOM generation. */
  public static class BomGenerationConfig {
    private final boolean enabled;
    private final String parentSuffix;
    private final String nameTemplate;
    private final String descriptionTemplate;

    public BomGenerationConfig(
        boolean enabled, String parentSuffix, String nameTemplate, String descriptionTemplate) {
      this.enabled = enabled;
      this.parentSuffix = parentSuffix != null ? parentSuffix : "-parent";
      this.nameTemplate = nameTemplate != null ? nameTemplate : "{baseName} (Parent Aggregator)";
      this.descriptionTemplate =
          descriptionTemplate != null
              ? descriptionTemplate
              : "Parent Aggregator providing shared dependency management for child modules";
    }

    public boolean isEnabled() {
      return enabled;
    }

    public String getParentSuffix() {
      return parentSuffix;
    }

    public String getNameTemplate() {
      return nameTemplate;
    }

    public String getDescriptionTemplate() {
      return descriptionTemplate;
    }
  }

  private DependencyConfig(
      Set<String> commonDependencyKeys,
      Map<String, CommonDependency> commonDependencies,
      BomGenerationConfig bomConfig) {
    this.commonDependencyKeys = Collections.unmodifiableSet(commonDependencyKeys);
    this.commonDependencies = Collections.unmodifiableMap(commonDependencies);
    this.bomConfig = bomConfig;
  }

  /**
   * Gets the singleton instance, loading from project root config/ directory.
   *
   * @return the DependencyConfig instance
   */
  public static synchronized DependencyConfig getInstance() {
    if (instance == null) {
      instance = loadFromProjectRoot();
    }
    return instance;
  }

  /**
   * Gets the singleton instance for a specific project root.
   *
   * @param projectRootPath the project root directory
   * @return the DependencyConfig instance
   */
  public static synchronized DependencyConfig getInstance(Path projectRootPath) {
    if (instance == null || (projectRoot != null && !projectRoot.equals(projectRootPath))) {
      projectRoot = projectRootPath;
      instance = loadFromProjectRoot();
    }
    return instance;
  }

  /**
   * Loads configuration from a specific file path.
   *
   * @param configPath path to the YAML config file
   * @return loaded DependencyConfig
   */
  public static DependencyConfig loadFrom(Path configPath) {
    try (InputStream input = Files.newInputStream(configPath)) {
      return parseYaml(input);
    } catch (IOException e) {
      System.err.println(
          "Warning: Failed to load dependency config from "
              + configPath
              + ": "
              + e.getMessage()
              + ". Using defaults.");
      return createDefault();
    }
  }

  /**
   * Loads configuration from project root config/ directory. Falls back to defaults if not found.
   *
   * @return loaded DependencyConfig
   */
  private static DependencyConfig loadFromProjectRoot() {
    // Try to load from project root config/ directory
    if (projectRoot != null) {
      Path configFile = projectRoot.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
      if (Files.exists(configFile)) {
        return loadFrom(configFile);
      }
    }

    // Try current working directory
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path configFile = cwd.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
    if (Files.exists(configFile)) {
      return loadFrom(configFile);
    }

    // Walk up to find aggregator root with config/
    Path searchPath = cwd;
    while (searchPath != null) {
      configFile = searchPath.resolve(CONFIG_DIR).resolve(CONFIG_FILE);
      if (Files.exists(configFile)) {
        return loadFrom(configFile);
      }
      // Stop at aggregator root (pom.xml with no parent)
      Path pomPath = searchPath.resolve("pom.xml");
      if (Files.exists(pomPath) && isAggregatorRoot(pomPath)) {
        break;
      }
      searchPath = searchPath.getParent();
    }

    // Fall back to defaults
    return createDefault();
  }

  /** Checks if a pom.xml is an aggregator root (no parent element). */
  private static boolean isAggregatorRoot(Path pomPath) {
    try {
      String content = Files.readString(pomPath);
      // Simple check: no <parent> element means aggregator root
      return !content.contains("<parent>");
    } catch (IOException e) {
      return false;
    }
  }

  /** Parses YAML configuration from an input stream. */
  @SuppressWarnings("unchecked")
  private static DependencyConfig parseYaml(InputStream input) {
    Yaml yaml = new Yaml();
    Map<String, Object> root = yaml.load(input);

    Set<String> keys = new HashSet<>();
    Map<String, CommonDependency> deps = new HashMap<>();

    // Parse commonDependencies
    List<Map<String, Object>> depsList = (List<Map<String, Object>>) root.get("commonDependencies");
    if (depsList != null) {
      for (Map<String, Object> depMap : depsList) {
        String groupId = (String) depMap.get("groupId");
        String artifactId = (String) depMap.get("artifactId");
        String version = (String) depMap.get("version");
        String scope = (String) depMap.get("scope");
        String description = (String) depMap.get("description");

        if (groupId != null && artifactId != null) {
          CommonDependency dep =
              new CommonDependency(groupId, artifactId, version, scope, description);
          String key = dep.getKey();
          keys.add(key);
          deps.put(key, dep);
        }
      }
    }

    // Parse bomGeneration config
    Map<String, Object> bomMap = (Map<String, Object>) root.get("bomGeneration");
    BomGenerationConfig bomConfig;
    if (bomMap != null) {
      boolean enabled = bomMap.get("enabled") != null && (Boolean) bomMap.get("enabled");
      String parentSuffix = (String) bomMap.get("parentSuffix");
      String nameTemplate = (String) bomMap.get("nameTemplate");
      String descriptionTemplate = (String) bomMap.get("descriptionTemplate");
      bomConfig = new BomGenerationConfig(enabled, parentSuffix, nameTemplate, descriptionTemplate);
    } else {
      bomConfig = new BomGenerationConfig(true, "-parent", null, null);
    }

    return new DependencyConfig(keys, deps, bomConfig);
  }

  /**
   * Creates a default configuration with hardcoded common dependencies. Used as fallback when YAML
   * config is not found.
   */
  private static DependencyConfig createDefault() {
    Set<String> keys = new HashSet<>();
    Map<String, CommonDependency> deps = new HashMap<>();

    // Default common dependencies
    addDefault(keys, deps, "org.slf4j", "slf4j-api", "2.0.9", null);
    addDefault(keys, deps, "ch.qos.logback", "logback-classic", "1.4.14", "runtime");
    addDefault(keys, deps, "ch.qos.logback", "logback-core", "1.4.14", "runtime");
    addDefault(keys, deps, "org.projectlombok", "lombok", "1.18.36", "provided");
    addDefault(keys, deps, "org.junit.jupiter", "junit-jupiter", "5.10.1", "test");
    addDefault(keys, deps, "org.junit.jupiter", "junit-jupiter-api", "5.10.1", "test");
    addDefault(keys, deps, "org.junit.jupiter", "junit-jupiter-engine", "5.10.1", "test");
    addDefault(keys, deps, "org.assertj", "assertj-core", "3.24.2", "test");
    addDefault(keys, deps, "org.mockito", "mockito-core", "5.8.0", "test");
    addDefault(keys, deps, "org.mockito", "mockito-junit-jupiter", "5.8.0", "test");
    addDefault(keys, deps, "io.projectreactor", "reactor-core", "3.6.0", null);
    addDefault(keys, deps, "io.projectreactor", "reactor-test", "3.6.0", "test");
    addDefault(keys, deps, "com.fasterxml.jackson.core", "jackson-databind", "2.16.0", null);
    addDefault(keys, deps, "com.fasterxml.jackson.core", "jackson-core", "2.16.0", null);
    addDefault(keys, deps, "com.fasterxml.jackson.core", "jackson-annotations", "2.16.0", null);
    addDefault(keys, deps, "com.google.guava", "guava", "32.1.3-jre", null);
    addDefault(keys, deps, "org.apache.commons", "commons-lang3", "3.14.0", null);
    addDefault(keys, deps, "commons-io", "commons-io", "2.15.1", null);

    BomGenerationConfig bomConfig = new BomGenerationConfig(true, "-parent", null, null);

    return new DependencyConfig(keys, deps, bomConfig);
  }

  private static void addDefault(
      Set<String> keys,
      Map<String, CommonDependency> deps,
      String groupId,
      String artifactId,
      String version,
      String scope) {
    CommonDependency dep = new CommonDependency(groupId, artifactId, version, scope, null);
    String key = dep.getKey();
    keys.add(key);
    deps.put(key, dep);
  }

  /**
   * Gets the set of common dependency keys (groupId:artifactId).
   *
   * @return unmodifiable set of dependency keys
   */
  public Set<String> getCommonDependencyKeys() {
    return commonDependencyKeys;
  }

  /**
   * Checks if a dependency is a common dependency.
   *
   * @param groupId the group ID
   * @param artifactId the artifact ID
   * @return true if it's a common dependency
   */
  public boolean isCommonDependency(String groupId, String artifactId) {
    return commonDependencyKeys.contains(groupId + ":" + artifactId);
  }

  /**
   * Gets a common dependency by key.
   *
   * @param key the dependency key (groupId:artifactId)
   * @return the CommonDependency, or null if not found
   */
  public CommonDependency getCommonDependency(String key) {
    return commonDependencies.get(key);
  }

  /**
   * Gets all common dependencies.
   *
   * @return unmodifiable map of dependencies by key
   */
  public Map<String, CommonDependency> getCommonDependencies() {
    return commonDependencies;
  }

  /**
   * Gets all common dependencies as a list.
   *
   * @return list of all common dependencies
   */
  public List<CommonDependency> getCommonDependencyList() {
    return new ArrayList<>(commonDependencies.values());
  }

  /**
   * Gets the BOM generation configuration.
   *
   * @return BOM generation config
   */
  public BomGenerationConfig getBomConfig() {
    return bomConfig;
  }

  /**
   * Gets the default version for a common dependency.
   *
   * @param groupId the group ID
   * @param artifactId the artifact ID
   * @return the default version, or "FIXME" if not found
   */
  public String getDefaultVersion(String groupId, String artifactId) {
    CommonDependency dep = commonDependencies.get(groupId + ":" + artifactId);
    return dep != null && dep.getVersion() != null ? dep.getVersion() : "FIXME";
  }

  /** Resets the singleton instance (for testing). */
  public static synchronized void resetInstance() {
    instance = null;
  }

  /**
   * Sets a custom instance (for testing).
   *
   * @param config the config to use
   */
  public static synchronized void setInstance(DependencyConfig config) {
    instance = config;
  }
}
