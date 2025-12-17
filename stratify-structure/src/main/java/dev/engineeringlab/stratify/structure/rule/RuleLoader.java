package dev.engineeringlab.stratify.structure.rule;

import dev.engineeringlab.stratify.structure.model.Category;
import dev.engineeringlab.stratify.structure.model.Severity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Loads rule definitions from .properties or .yaml files.
 *
 * <p>Supports loading from:
 *
 * <ul>
 *   <li>Classpath resources (default rules)
 *   <li>File system paths (custom rules)
 * </ul>
 *
 * <p>Format support:
 *
 * <ul>
 *   <li>.properties files - Zero dependency, uses standard Java Properties
 *   <li>.yaml files - Optional, requires SnakeYAML on classpath (detected via reflection)
 * </ul>
 *
 * <p>Properties file format example:
 *
 * <pre>
 * # Rule ID is the prefix
 * DP-001.name=No implementation dependencies in API
 * DP-001.description=API modules must not depend on implementation modules
 * DP-001.category=DEPENDENCIES
 * DP-001.severity=ERROR
 * DP-001.enabled=true
 * DP-001.reason=API must remain stable and implementation-agnostic
 * DP-001.fix=Remove implementation dependencies from API module
 * </pre>
 */
public class RuleLoader {

  private final Map<String, RuleDefinition> rules = new HashMap<>();
  private final boolean yamlSupported;
  private final Object yamlParser; // org.yaml.snakeyaml.Yaml instance, if available

  /** Creates a new rule loader and checks for YAML support. */
  public RuleLoader() {
    this.yamlSupported = checkYamlSupport();
    this.yamlParser = yamlSupported ? createYamlParser() : null;
  }

  /** Checks if SnakeYAML is available on the classpath. */
  private boolean checkYamlSupport() {
    try {
      Class.forName("org.yaml.snakeyaml.Yaml");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  /** Creates a YAML parser instance via reflection. */
  private Object createYamlParser() {
    try {
      Class<?> yamlClass = Class.forName("org.yaml.snakeyaml.Yaml");
      return yamlClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Loads rules from a classpath resource.
   *
   * <p>Automatically detects format based on file extension:
   *
   * <ul>
   *   <li>.properties - Java Properties format
   *   <li>.yaml or .yml - YAML format (requires SnakeYAML)
   * </ul>
   *
   * <p>If the resource is not found, this method silently returns without loading any rules. Use
   * {@link #loadRequiredFromClasspath(String)} if the resource must exist.
   *
   * @param resourceName the resource name (e.g., "structure-rules.yaml" or
   *     "structure-rules.properties")
   * @return this loader for chaining
   */
  public RuleLoader loadFromClasspath(String resourceName) {
    InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
    if (input != null) {
      try {
        loadFromStream(input, resourceName);
      } catch (IOException e) {
        System.err.println(
            "Warning: Failed to load rules from " + resourceName + ": " + e.getMessage());
      }
    }
    return this;
  }

  /**
   * Loads rules from a required classpath resource.
   *
   * <p>Unlike {@link #loadFromClasspath(String)}, this method throws an exception if the resource
   * is not found.
   *
   * @param resourceName the resource name
   * @return this loader for chaining
   * @throws IllegalStateException if the resource is not found on the classpath
   */
  public RuleLoader loadRequiredFromClasspath(String resourceName) {
    InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
    if (input == null) {
      throw new IllegalStateException(
          "Required rules file '"
              + resourceName
              + "' not found on classpath. "
              + "This file contains rule definitions and must be present.");
    }
    try {
      loadFromStream(input, resourceName);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to load required rules from " + resourceName, e);
    }
    return this;
  }

  /**
   * Loads rules from a file system path.
   *
   * @param path the path to the rules file
   * @return this loader for chaining
   */
  public RuleLoader loadFromPath(Path path) {
    if (Files.exists(path)) {
      try (InputStream input = Files.newInputStream(path)) {
        loadFromStream(input, path.getFileName().toString());
      } catch (IOException e) {
        System.err.println("Warning: Failed to load rules from " + path + ": " + e.getMessage());
      }
    }
    return this;
  }

  /**
   * Loads rules from an input stream.
   *
   * <p>Format is detected based on the filename extension.
   *
   * @param input the input stream
   * @param filename the filename (used to detect format)
   * @return this loader for chaining
   * @throws IOException if reading fails
   */
  public RuleLoader loadFromStream(InputStream input, String filename) throws IOException {
    if (filename.endsWith(".properties")) {
      loadFromProperties(input);
    } else if (filename.endsWith(".yaml") || filename.endsWith(".yml")) {
      loadFromYaml(input);
    } else {
      System.err.println("Warning: Unknown rule file format: " + filename);
    }
    return this;
  }

  /** Loads rules from a Properties file. */
  private void loadFromProperties(InputStream input) throws IOException {
    Properties props = new Properties();
    props.load(input);

    // Group properties by rule ID
    Map<String, Map<String, String>> ruleProps = new HashMap<>();
    for (String key : props.stringPropertyNames()) {
      int dotIndex = key.indexOf('.');
      if (dotIndex > 0) {
        String ruleId = key.substring(0, dotIndex);
        String property = key.substring(dotIndex + 1);
        ruleProps
            .computeIfAbsent(ruleId, k -> new HashMap<>())
            .put(property, props.getProperty(key));
      }
    }

    // Parse each rule
    for (Map.Entry<String, Map<String, String>> entry : ruleProps.entrySet()) {
      String ruleId = entry.getKey();
      Map<String, String> properties = entry.getValue();
      RuleDefinition rule = parseRuleFromProperties(ruleId, properties);
      rules.put(ruleId, rule);
    }
  }

  /** Parses a rule from properties map. */
  private RuleDefinition parseRuleFromProperties(String id, Map<String, String> props) {
    return RuleDefinition.builder()
        .id(id)
        .name(props.getOrDefault("name", id))
        .description(props.getOrDefault("description", ""))
        .category(parseCategory(props.getOrDefault("category", "STRUCTURE")))
        .severity(parseSeverity(props.getOrDefault("severity", "ERROR")))
        .enabled(Boolean.parseBoolean(props.getOrDefault("enabled", "true")))
        .targetModules(parseList(props.get("targetModules")))
        .reason(props.getOrDefault("reason", ""))
        .fix(props.getOrDefault("fix", ""))
        .reference(props.getOrDefault("reference", ""))
        .detection(parseDetectionFromProperties(props))
        .build();
  }

  /** Parses detection criteria from properties. */
  private RuleDefinition.DetectionCriteria parseDetectionFromProperties(Map<String, String> props) {
    RuleDefinition.DetectionCriteria.Builder builder = RuleDefinition.DetectionCriteria.builder();

    builder.pathPatterns(parseList(props.get("detection.pathPatterns")));
    builder.filePatterns(parseList(props.get("detection.filePatterns")));
    builder.packagePatterns(parseList(props.get("detection.packagePatterns")));

    // Parse dependency patterns if present
    if (props.containsKey("detection.dependencyPatterns.mustNotContain")
        || props.containsKey("detection.dependencyPatterns.mustContain")) {
      RuleDefinition.DependencyPatterns depPatterns =
          RuleDefinition.DependencyPatterns.builder()
              .mustNotContain(parseList(props.get("detection.dependencyPatterns.mustNotContain")))
              .mustContain(parseList(props.get("detection.dependencyPatterns.mustContain")))
              .exceptions(parseList(props.get("detection.dependencyPatterns.exceptions")))
              .scope(props.get("detection.dependencyPatterns.scope"))
              .build();
      builder.dependencyPatterns(depPatterns);
    }

    // Parse structure patterns if present
    if (props.containsKey("detection.structurePatterns.requiredModules")) {
      RuleDefinition.StructurePatterns structPatterns =
          RuleDefinition.StructurePatterns.builder()
              .requiredModules(parseList(props.get("detection.structurePatterns.requiredModules")))
              .moduleOrder(parseList(props.get("detection.structurePatterns.moduleOrder")))
              .requiredElements(
                  parseList(props.get("detection.structurePatterns.requiredElements")))
              .requireParent(
                  Boolean.parseBoolean(
                      props.getOrDefault("detection.structurePatterns.requireParent", "false")))
              .noSourceCode(
                  Boolean.parseBoolean(
                      props.getOrDefault("detection.structurePatterns.noSourceCode", "false")))
              .build();
      builder.structurePatterns(structPatterns);
    }

    return builder.build();
  }

  /** Loads rules from a YAML file using reflection. */
  @SuppressWarnings("unchecked")
  private void loadFromYaml(InputStream input) {
    if (!yamlSupported || yamlParser == null) {
      System.err.println(
          "Warning: YAML file detected but SnakeYAML is not on classpath. "
              + "Add org.yaml:snakeyaml dependency to load YAML rules.");
      return;
    }

    try {
      // Use reflection to call yaml.load(input)
      Class<?> yamlClass = yamlParser.getClass();
      Map<String, Object> root =
          (Map<String, Object>)
              yamlClass.getMethod("load", InputStream.class).invoke(yamlParser, input);

      if (root == null) {
        return;
      }

      // Check for "rules" key or assume root is rules map
      Map<String, Object> rulesMap;
      if (root.containsKey("rules")) {
        rulesMap = (Map<String, Object>) root.get("rules");
      } else {
        rulesMap = root;
      }

      for (Map.Entry<String, Object> entry : rulesMap.entrySet()) {
        String ruleId = entry.getKey();
        if (entry.getValue() instanceof Map) {
          RuleDefinition rule = parseRuleFromYaml(ruleId, (Map<String, Object>) entry.getValue());
          rules.put(ruleId, rule);
        }
      }
    } catch (Exception e) {
      System.err.println("Warning: Failed to parse YAML: " + e.getMessage());
    }
  }

  /** Parses a rule from YAML map. */
  @SuppressWarnings("unchecked")
  private RuleDefinition parseRuleFromYaml(String id, Map<String, Object> map) {
    RuleDefinition.Builder builder =
        RuleDefinition.builder()
            .id(id)
            .name(getString(map, "name", id))
            .description(getString(map, "description", ""))
            .category(parseCategory(getString(map, "category", "STRUCTURE")))
            .severity(parseSeverity(getString(map, "severity", "ERROR")))
            .enabled(getBoolean(map, "enabled", true))
            .targetModules(getStringList(map, "targetModules"))
            .reason(getString(map, "reason", ""))
            .fix(getString(map, "fix", ""))
            .reference(getString(map, "reference", ""));

    // Parse detection criteria
    if (map.containsKey("detection")) {
      Map<String, Object> detectionMap = (Map<String, Object>) map.get("detection");
      builder.detection(parseDetectionFromYaml(detectionMap));
    }

    return builder.build();
  }

  /** Parses detection criteria from YAML map. */
  @SuppressWarnings("unchecked")
  private RuleDefinition.DetectionCriteria parseDetectionFromYaml(Map<String, Object> map) {
    RuleDefinition.DetectionCriteria.Builder builder =
        RuleDefinition.DetectionCriteria.builder()
            .pathPatterns(getStringList(map, "pathPatterns"))
            .filePatterns(getStringList(map, "filePatterns"))
            .packagePatterns(getStringList(map, "packagePatterns"));

    // Parse dependency patterns
    if (map.containsKey("dependencyPatterns")) {
      Map<String, Object> depMap = (Map<String, Object>) map.get("dependencyPatterns");
      builder.dependencyPatterns(parseDependencyPatternsFromYaml(depMap));
    }

    // Parse structure patterns
    if (map.containsKey("structurePatterns")) {
      Map<String, Object> structMap = (Map<String, Object>) map.get("structurePatterns");
      builder.structurePatterns(parseStructurePatternsFromYaml(structMap));
    }

    return builder.build();
  }

  /** Parses dependency patterns from YAML map. */
  private RuleDefinition.DependencyPatterns parseDependencyPatternsFromYaml(
      Map<String, Object> map) {
    return RuleDefinition.DependencyPatterns.builder()
        .mustNotContain(getStringList(map, "mustNotContain"))
        .mustContain(getStringList(map, "mustContain"))
        .exceptions(getStringList(map, "exceptions"))
        .scope(getString(map, "scope", null))
        .build();
  }

  /** Parses structure patterns from YAML map. */
  private RuleDefinition.StructurePatterns parseStructurePatternsFromYaml(Map<String, Object> map) {
    return RuleDefinition.StructurePatterns.builder()
        .requiredModules(getStringList(map, "requiredModules"))
        .moduleOrder(getStringList(map, "moduleOrder"))
        .requiredElements(getStringList(map, "requiredElements"))
        .requireParent(getBoolean(map, "requireParent", false))
        .noSourceCode(getBoolean(map, "noSourceCode", false))
        .build();
  }

  // Helper methods for parsing

  private String getString(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
    Object value = map.get(key);
    if (value instanceof Boolean) {
      return (Boolean) value;
    }
    if (value != null) {
      return Boolean.parseBoolean(value.toString());
    }
    return defaultValue;
  }

  @SuppressWarnings("unchecked")
  private List<String> getStringList(Map<String, Object> map, String key) {
    Object value = map.get(key);
    if (value instanceof List) {
      List<String> result = new ArrayList<>();
      for (Object item : (List<?>) value) {
        result.add(item.toString());
      }
      return result;
    }
    return List.of();
  }

  private List<String> parseList(String value) {
    if (value == null || value.trim().isEmpty()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
  }

  private Severity parseSeverity(String value) {
    try {
      return Severity.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Severity.ERROR;
    }
  }

  private Category parseCategory(String value) {
    try {
      return Category.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Category.STRUCTURE;
    }
  }

  // Query methods

  /**
   * Gets all loaded rules.
   *
   * @return unmodifiable map of rule ID to definition
   */
  public Map<String, RuleDefinition> getRules() {
    return Collections.unmodifiableMap(rules);
  }

  /**
   * Gets a specific rule by ID.
   *
   * @param ruleId the rule ID
   * @return the rule definition, or null if not found
   */
  public RuleDefinition getRule(String ruleId) {
    return rules.get(ruleId);
  }

  /**
   * Gets all rules for a specific category.
   *
   * @param category the category
   * @return list of rules in that category
   */
  public List<RuleDefinition> getRulesByCategory(Category category) {
    List<RuleDefinition> result = new ArrayList<>();
    for (RuleDefinition rule : rules.values()) {
      if (category.equals(rule.category())) {
        result.add(rule);
      }
    }
    return result;
  }

  /**
   * Gets all enabled rules.
   *
   * @return list of enabled rules
   */
  public List<RuleDefinition> getEnabledRules() {
    List<RuleDefinition> result = new ArrayList<>();
    for (RuleDefinition rule : rules.values()) {
      if (rule.enabled()) {
        result.add(rule);
      }
    }
    return result;
  }

  /**
   * Checks if YAML support is available.
   *
   * @return true if SnakeYAML is on classpath
   */
  public boolean isYamlSupported() {
    return yamlSupported;
  }
}
