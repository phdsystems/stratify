package dev.engineeringlab.stratify.structure.scanner.common.rule;

import dev.engineeringlab.stratify.structure.scanner.common.config.ComplianceConfig;
import dev.engineeringlab.stratify.structure.scanner.common.model.Severity;
import dev.engineeringlab.stratify.structure.scanner.common.model.ViolationCategory;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads rule definitions from YAML files.
 *
 * <p>Supports loading from:
 *
 * <ul>
 *   <li>Classpath resource (default rules)
 *   <li>File system path (custom rules)
 * </ul>
 *
 * <p>Default rules are loaded from {@code compliance-rules.yaml} on the classpath. Custom rules can
 * override or extend default rules.
 */
public class RuleLoader {

  private static final String CUSTOM_RULES_FILE = "sea-rules.yaml";

  /**
   * Scanner-specific rule files loaded by their respective modules.
   *
   * <p>Each YAML file contains rule definitions for a specific category:
   *
   * <ul>
   *   <li>naming-rules.yaml (NC-*) - Naming conventions
   *   <li>method-naming-rules.yaml (MN-*) - Method naming patterns
   *   <li>dependency-rules.yaml (DP-*) - Dependency management
   *   <li>modular-design-rules.yaml (MD-*) - Modular design patterns
   *   <li>subsystem-rules.yaml (SS-*) - Subsystem organization
   *   <li>spi-rules.yaml (SP-*) - Service provider interfaces
   *   <li>module-structure-rules.yaml (MS-*) - Module structure
   *   <li>exception-infra-rules.yaml (EI-*) - Exception/infrastructure layers
   *   <li>code-quality-rules.yaml (CQ-*) - Code quality
   *   <li>package-organization-rules.yaml (PO-*) - Package organization
   *   <li>test-compliance-rules.yaml (TC-*) - Test compliance
   *   <li>pattern-rules.yaml (PT-*) - Design patterns
   * </ul>
   */
  private static final String[] SCANNER_RULE_FILES = {
    // Naming convention scanner
    "naming-rules.yaml",
    "method-naming-rules.yaml",

    // Dependency scanner
    "dependency-rules.yaml",

    // Subsystem scanner
    "modular-design-rules.yaml",
    "subsystem-rules.yaml",
    "spi-rules.yaml",

    // Clean architecture scanner
    "module-structure-rules.yaml",
    "exception-infra-rules.yaml",

    // Quality rules
    "code-quality-rules.yaml",
    "package-organization-rules.yaml",
    "test-compliance-rules.yaml",
    "pattern-rules.yaml"
  };

  private final Map<String, RuleDefinition> rules = new HashMap<>();

  /**
   * Loads all rules from all scanner modules on the classpath.
   *
   * @return this loader for chaining
   */
  public RuleLoader loadAll() {
    for (String ruleFile : SCANNER_RULE_FILES) {
      loadFromClasspath(ruleFile);
    }
    return this;
  }

  /**
   * Loads rules from a specific classpath resource.
   *
   * <p>If the resource is not found, this method silently returns without loading any rules. Use
   * {@link #loadRequiredFromClasspath(String)} if the resource must exist.
   *
   * @param resourceName the resource name (e.g., "naming-rules.yaml")
   * @return this loader for chaining
   */
  public RuleLoader loadFromClasspath(String resourceName) {
    InputStream input = getClass().getClassLoader().getResourceAsStream(resourceName);
    if (input != null) {
      loadFromStream(input);
    }
    return this;
  }

  /**
   * Loads rules from a required classpath resource.
   *
   * <p>Unlike {@link #loadFromClasspath(String)}, this method throws an exception if the resource
   * is not found.
   *
   * @param resourceName the resource name (e.g., "naming-rules.yaml")
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
    loadFromStream(input);
    return this;
  }

  /**
   * Loads rules from a specific path.
   *
   * @param path the path to the YAML file
   * @return this loader for chaining
   */
  public RuleLoader loadFromPath(Path path) {
    if (Files.exists(path)) {
      try (InputStream input = Files.newInputStream(path)) {
        loadFromStream(input);
      } catch (IOException e) {
        System.err.println("Warning: Failed to load rules from " + path + ": " + e.getMessage());
      }
    }
    return this;
  }

  /**
   * Loads rules from an input stream.
   *
   * <p>This method is primarily used for testing and for loading rules from non-classpath sources.
   *
   * @param input the input stream containing YAML
   * @return this loader for chaining
   */
  @SuppressWarnings("unchecked")
  public RuleLoader loadFromStream(InputStream input) {
    Yaml yaml = new Yaml();
    Map<String, Object> root = yaml.load(input);

    if (root == null) {
      return this;
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
        RuleDefinition rule = parseRule(ruleId, (Map<String, Object>) entry.getValue());
        rules.put(ruleId, rule);
      }
    }
    return this;
  }

  /** Parses a single rule definition from a map. */
  @SuppressWarnings("unchecked")
  private RuleDefinition parseRule(String id, Map<String, Object> map) {
    RuleDefinition.RuleDefinitionBuilder builder =
        RuleDefinition.builder()
            .id(id)
            .name(getString(map, "name", id))
            .description(getString(map, "description", ""))
            .category(parseCategory(getString(map, "category", "CODE_QUALITY")))
            .severity(parseSeverity(getString(map, "severity", "ERROR")))
            .enabled(getBoolean(map, "enabled", true))
            .targetModules(getStringList(map, "targetModules"))
            .reason(getString(map, "reason", ""))
            .fix(getString(map, "fix", ""))
            .reference(getString(map, "reference", ""));

    // Parse detection criteria
    if (map.containsKey("detection")) {
      Map<String, Object> detectionMap = (Map<String, Object>) map.get("detection");
      builder.detection(parseDetection(detectionMap));
    }

    return builder.build();
  }

  /** Parses detection criteria from a map. */
  @SuppressWarnings("unchecked")
  private RuleDefinition.DetectionCriteria parseDetection(Map<String, Object> map) {
    RuleDefinition.DetectionCriteria.DetectionCriteriaBuilder builder =
        RuleDefinition.DetectionCriteria.builder()
            .pathPatterns(getStringList(map, "pathPatterns"))
            .filePatterns(getStringList(map, "filePatterns"));

    // Parse class patterns
    if (map.containsKey("classPatterns")) {
      Map<String, Object> classMap = (Map<String, Object>) map.get("classPatterns");
      builder.classPatterns(parseClassPatterns(classMap));
    }

    // Parse dependency patterns
    if (map.containsKey("dependencyPatterns")) {
      Map<String, Object> depMap = (Map<String, Object>) map.get("dependencyPatterns");
      builder.dependencyPatterns(parseDependencyPatterns(depMap));
    }

    // Parse POM patterns
    if (map.containsKey("pomPatterns")) {
      Map<String, Object> pomMap = (Map<String, Object>) map.get("pomPatterns");
      builder.pomPatterns(parsePomPatterns(pomMap));
    }

    // Parse vendor patterns
    builder.vendorPatterns(getStringList(map, "vendorPatterns"));

    // Parse naming patterns (for NC-* rules)
    if (map.containsKey("namingPatterns")) {
      Map<String, Object> namingMap = (Map<String, Object>) map.get("namingPatterns");
      builder.namingPatterns(parseNamingPatterns(namingMap));
    }

    return builder.build();
  }

  /** Parses naming patterns from a map. */
  @SuppressWarnings("unchecked")
  private RuleDefinition.NamingPatterns parseNamingPatterns(Map<String, Object> map) {
    return RuleDefinition.NamingPatterns.builder()
        .scope(getString(map, "scope", null))
        .groupId(getString(map, "groupId", null))
        .packagePrefix(getString(map, "packagePrefix", null))
        .artifactIdMustNotStartWith(getString(map, "artifactIdMustNotStartWith", null))
        .artifactIdMustNotContain(getString(map, "artifactIdMustNotContain", null))
        .mustNotEndWith(getString(map, "mustNotEndWith", null))
        .directoryMustEndWith(getString(map, "directoryMustEndWith", null))
        .interfacesMustNotEndWith(getString(map, "interfacesMustNotEndWith", null))
        .packagesMustStartWith(getString(map, "packagesMustStartWith", null))
        .classesMustEndWith(getString(map, "classesMustEndWith", null))
        .parentModuleMustEndWith(getString(map, "parentModuleMustEndWith", null))
        .packagesMustContain(getString(map, "packagesMustContain", null))
        .enumsMustEndWith(getString(map, "enumsMustEndWith", null))
        .classesMustBeSingular(getBoolean(map, "classesMustBeSingular", false))
        .packagesMustBeSingular(getBoolean(map, "packagesMustBeSingular", false))
        .artifactIdMustBeSingular(getBoolean(map, "artifactIdMustBeSingular", false))
        .constantsMustBeEnums(getBoolean(map, "constantsMustBeEnums", false))
        .packageMatchesMavenCoordinates(getBoolean(map, "packageMatchesMavenCoordinates", false))
        .submodulesMustDeclareGroupId(getBoolean(map, "submodulesMustDeclareGroupId", false))
        .submodulesMustHaveSuffix(getStringList(map, "submodulesMustHaveSuffix"))
        .modulesMustNotEndWith(getStringList(map, "modulesMustNotEndWith"))
        .aggregatorMustEndWith(getStringList(map, "aggregatorMustEndWith"))
        .exceptions(getStringList(map, "exceptions"))
        .allowedSharedPackages(getStringList(map, "allowedSharedPackages"))
        .modelsMustBeIn(getString(map, "modelsMustBeIn", null))
        .exceptionsMustBeIn(getString(map, "exceptionsMustBeIn", null))
        .mustNotContainVendorNames(map.get("mustNotContainVendorNames"))
        .build();
  }

  /** Parses class patterns from a map. */
  private RuleDefinition.ClassPatterns parseClassPatterns(Map<String, Object> map) {
    return RuleDefinition.ClassPatterns.builder()
        .detectRecords(getBoolean(map, "detectRecords", false))
        .annotations(getStringList(map, "annotations"))
        .suffixes(getStringList(map, "suffixes"))
        .extendsClasses(getStringList(map, "extendsClasses"))
        .detectInterfaces(getBoolean(map, "detectInterfaces", false))
        .detectEnums(getBoolean(map, "detectEnums", false))
        .build();
  }

  /** Parses dependency patterns from a map. */
  private RuleDefinition.DependencyPatterns parseDependencyPatterns(Map<String, Object> map) {
    return RuleDefinition.DependencyPatterns.builder()
        .mustNotContain(getStringList(map, "mustNotContain"))
        .mustContain(getStringList(map, "mustContain"))
        .groupPatterns(getStringList(map, "groupPatterns"))
        .artifactPatterns(getStringList(map, "artifactPatterns"))
        .exceptions(getStringList(map, "exceptions"))
        .scope(getString(map, "scope", null))
        .requiresSiblings(getStringList(map, "requiresSiblings"))
        .condition(getString(map, "condition", null))
        .build();
  }

  /** Parses POM patterns from a map. */
  private RuleDefinition.PomPatterns parsePomPatterns(Map<String, Object> map) {
    return RuleDefinition.PomPatterns.builder()
        .requiredModules(getStringList(map, "requiredModules"))
        .moduleOrder(getStringList(map, "moduleOrder"))
        .requiredElements(getStringList(map, "requiredElements"))
        .requireParent(getBoolean(map, "requireParent", false))
        .requireNoParent(getBoolean(map, "requireNoParent", false))
        .parentArtifactIdSuffix(getString(map, "parentArtifactIdSuffix", null))
        .noSourceCode(getBoolean(map, "noSourceCode", false))
        .noDependencies(getBoolean(map, "noDependencies", false))
        .allSubmodulesListed(getBoolean(map, "allSubmodulesListed", false))
        .pureAggregatorSuffix(getString(map, "pureAggregatorSuffix", null))
        .artifactIdMustEndWith(getStringList(map, "artifactIdMustEndWith"))
        .build();
  }

  /** Gets a string value from a map. */
  private String getString(Map<String, Object> map, String key, String defaultValue) {
    Object value = map.get(key);
    return value != null ? value.toString() : defaultValue;
  }

  /** Gets a boolean value from a map. */
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

  /** Gets a list of strings from a map. */
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
    return new ArrayList<>();
  }

  /** Parses a severity string to enum. */
  private Severity parseSeverity(String value) {
    try {
      return Severity.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return Severity.ERROR;
    }
  }

  /** Parses a category string to enum. */
  private ViolationCategory parseCategory(String value) {
    try {
      return ViolationCategory.valueOf(value.toUpperCase());
    } catch (IllegalArgumentException e) {
      return ViolationCategory.CODE_QUALITY;
    }
  }

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
  public List<RuleDefinition> getRulesByCategory(ViolationCategory category) {
    List<RuleDefinition> result = new ArrayList<>();
    for (RuleDefinition rule : rules.values()) {
      if (category.equals(rule.getCategory())) {
        result.add(rule);
      }
    }
    return result;
  }

  /**
   * Gets all rules for a specific category by name.
   *
   * @param categoryName the category name
   * @return list of rules in that category
   */
  public List<RuleDefinition> getRulesByCategory(String categoryName) {
    ViolationCategory category = parseCategory(categoryName);
    return getRulesByCategory(category);
  }

  /**
   * Gets all enabled rules.
   *
   * @return list of enabled rules
   */
  public List<RuleDefinition> getEnabledRules() {
    List<RuleDefinition> result = new ArrayList<>();
    for (RuleDefinition rule : rules.values()) {
      if (rule.isEnabled()) {
        result.add(rule);
      }
    }
    return result;
  }

  /**
   * Applies rule overrides from ComplianceConfig.
   *
   * <p>This method merges user-defined rule overrides from sea.yaml with the default rules from
   * compliance-rules.yaml. Overrides can:
   *
   * <ul>
   *   <li>Enable/disable rules
   *   <li>Change severity levels
   *   <li>Add additional vendor patterns (for NC-029)
   * </ul>
   *
   * @param config the compliance configuration containing rule overrides
   * @return this loader for chaining
   */
  public RuleLoader applyOverrides(ComplianceConfig config) {
    if (config == null) {
      return this;
    }

    Map<String, ComplianceConfig.RuleOverride> overrides = config.getRuleOverrides();
    if (overrides == null || overrides.isEmpty()) {
      return this;
    }

    for (Map.Entry<String, ComplianceConfig.RuleOverride> entry : overrides.entrySet()) {
      String ruleId = entry.getKey();
      ComplianceConfig.RuleOverride override = entry.getValue();

      RuleDefinition rule = rules.get(ruleId);
      if (rule == null) {
        // Rule doesn't exist - could be a user error or future rule
        continue;
      }

      // Apply enabled override
      if (override.getEnabled() != null) {
        rule.setEnabled(override.getEnabled());
      }

      // Apply severity override
      if (override.getSeverity() != null && !override.getSeverity().isEmpty()) {
        try {
          rule.setSeverity(Severity.valueOf(override.getSeverity().toUpperCase()));
        } catch (IllegalArgumentException e) {
          System.err.println(
              "Warning: Invalid severity '" + override.getSeverity() + "' for rule " + ruleId);
        }
      }

      // Apply additional vendor patterns (merge with existing)
      if (override.getAdditionalVendorPatterns() != null
          && !override.getAdditionalVendorPatterns().isEmpty()) {
        if (rule.getDetection() != null) {
          List<String> existingPatterns = rule.getDetection().getVendorPatterns();
          List<String> mergedPatterns = new ArrayList<>(existingPatterns);
          for (String pattern : override.getAdditionalVendorPatterns()) {
            if (!mergedPatterns.contains(pattern)) {
              mergedPatterns.add(pattern);
            }
          }
          rule.getDetection().setVendorPatterns(mergedPatterns);
        }
      }
    }

    return this;
  }
}
