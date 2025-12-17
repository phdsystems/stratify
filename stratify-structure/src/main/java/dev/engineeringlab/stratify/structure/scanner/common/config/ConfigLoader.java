package dev.engineeringlab.stratify.structure.scanner.common.config;

import dev.engineeringlab.stratify.structure.scanner.common.util.PomScanner;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.maven.model.Model;
import org.yaml.snakeyaml.Yaml;

/**
 * Loads compliance configuration from YAML files with profile support.
 *
 * <p>Configuration files follow Spring Boot naming conventions:
 *
 * <ul>
 *   <li>{@code application.yaml} - Base project configuration
 *   <li>{@code application-{profile}.yaml} - Profile-specific configuration overrides
 *   <li>{@code application-rules.yaml} - Base rule overrides
 *   <li>{@code application-rules-{profile}.yaml} - Profile-specific rule overrides
 * </ul>
 *
 * <p>Rule definitions are loaded separately by each scanner module:
 *
 * <ul>
 *   <li>{@code naming-rules.yaml} - NC-* rules in naming-convention-scanner
 *   <li>{@code dependency-rules.yaml} - DP-* rules in dep-scanner
 *   <li>{@code structure-rules.yaml} - MS-* rules in module-structure-scanner
 * </ul>
 *
 * <p>Loading order (later files override earlier):
 *
 * <ol>
 *   <li>{@code application.yaml} (base config, auto-generated if missing)
 *   <li>{@code application-rules.yaml} (base rule overrides)
 *   <li>{@code application-{profile}.yaml} (profile config)
 *   <li>{@code application-rules-{profile}.yaml} (profile rule overrides)
 * </ol>
 *
 * <p>Profile activation via system property: {@code -Dsea.profile=dev}
 *
 * <p>Example profiles: dev, int, staging, prod
 */
public class ConfigLoader {

  private static final String COMPLIANCE_RULES_FILE = "compliance-rules.yaml";
  private static final String CONFIG_DIR = "config";
  private static final String BOOTSTRAP_CONFIG = "bootstrap.yaml";
  private static final String APPLICATION_CONFIG = "application.yaml"; // Legacy support
  private static final String APPLICATION_RULES = "application-rules.yaml";
  private static final String PROFILE_PROPERTY = "sea.profile";

  /**
   * Loads configuration with profile support.
   *
   * <p>Loading order:
   *
   * <ol>
   *   <li>compliance-rules.yaml (required, classpath)
   *   <li>application.yaml (base config)
   *   <li>application-rules.yaml (base rule overrides)
   *   <li>application-{profile}.yaml (profile config)
   *   <li>application-rules-{profile}.yaml (profile rule overrides)
   * </ol>
   *
   * @param projectBasePath Project base directory
   * @return Loaded configuration
   * @throws IllegalStateException if compliance-rules.yaml is not found
   */
  public ConfigurationResult load(Path projectBasePath) {
    ComplianceConfig config = new ComplianceConfig();
    boolean generated = false;

    // Find the parent pom boundary - where config should be located
    Path configRoot = findParentPomBoundary(projectBasePath);

    // 1. Verify compliance-rules.yaml exists (required)
    verifyComplianceRulesExist();

    // 2. Load bootstrap.yaml from config/ directory (or legacy application.yaml)
    Path bootstrapConfig = findBootstrapConfig(configRoot);
    if (bootstrapConfig == null) {
      // Try loading from classpath first
      if (!loadFromClasspath(config, BOOTSTRAP_CONFIG)
          && !loadFromClasspath(config, APPLICATION_CONFIG)) {
        // Not found on classpath either, generate default
        Path configDir = configRoot.resolve(CONFIG_DIR);
        try {
          Files.createDirectories(configDir);
        } catch (IOException e) {
          // Ignore
        }
        bootstrapConfig = configDir.resolve(BOOTSTRAP_CONFIG);
        generateDefaultBootstrapConfig(bootstrapConfig, projectBasePath);
        generated = true;
        loadFromYaml(config, bootstrapConfig);
      }
    } else {
      loadFromYaml(config, bootstrapConfig);
    }

    // 3. Load application-rules.yaml (optional, base rule overrides)
    Path applicationRules = findConfigFile(configRoot, APPLICATION_RULES);
    if (applicationRules != null) {
      loadFromYaml(config, applicationRules);
    } else {
      loadFromClasspath(config, APPLICATION_RULES); // Try classpath
    }

    // 4. Load profile-specific configs if profile is set
    String profile = System.getProperty(PROFILE_PROPERTY);
    if (profile != null && !profile.trim().isEmpty()) {
      profile = profile.trim();

      // Load application-{profile}.yaml
      String profileConfig = "application-" + profile + ".yaml";
      Path profileConfigPath = findConfigFile(configRoot, profileConfig);
      if (profileConfigPath != null) {
        loadFromYaml(config, profileConfigPath);
      } else {
        loadFromClasspath(config, profileConfig); // Try classpath
      }

      // Load application-rules-{profile}.yaml
      String profileRules = "application-rules-" + profile + ".yaml";
      Path profileRulesPath = findConfigFile(configRoot, profileRules);
      if (profileRulesPath != null) {
        loadFromYaml(config, profileRulesPath);
      } else {
        loadFromClasspath(config, profileRules); // Try classpath
      }
    }

    // Apply module-type-specific defaults
    applyModuleTypeDefaults(config, projectBasePath);

    // Apply system property overrides (highest priority)
    applySystemPropertyOverrides(config);

    return new ConfigurationResult(config, generated, bootstrapConfig);
  }

  /**
   * Finds the bootstrap configuration file.
   *
   * <p>Checks the following locations in order:
   *
   * <ol>
   *   <li>{configRoot}/config/bootstrap.yaml (new standard location)
   *   <li>{configRoot}/application.yaml (legacy location)
   * </ol>
   *
   * @param configRoot the root directory to search at
   * @return the path to the file, or null if not found
   */
  private Path findBootstrapConfig(Path configRoot) {
    // 1. Check config/bootstrap.yaml (new standard location)
    Path bootstrapConfig = configRoot.resolve(CONFIG_DIR).resolve(BOOTSTRAP_CONFIG);
    if (Files.exists(bootstrapConfig)) {
      return bootstrapConfig;
    }

    // 2. Check legacy application.yaml at root
    Path legacyConfig = configRoot.resolve(APPLICATION_CONFIG);
    if (Files.exists(legacyConfig)) {
      return legacyConfig;
    }

    return null;
  }

  /**
   * Verifies that compliance-rules.yaml exists on the classpath.
   *
   * <p>Note: Since rules have been migrated to individual scanner modules (naming-rules.yaml,
   * dependency-rules.yaml, structure-rules.yaml), this file is no longer strictly required. Each
   * scanner module now loads its own rules from its classpath.
   *
   * @deprecated The centralized compliance-rules.yaml is no longer required. Rules are now loaded
   *     from scanner-specific YAML files.
   */
  private void verifyComplianceRulesExist() {
    // No-op: Rules are now loaded from individual scanner modules
    // Each scanner module has its own rules file (e.g., naming-rules.yaml)
  }

  /**
   * Finds a config file at the specified configRoot location.
   *
   * <p>Checks the following locations at configRoot level only (does not walk up):
   *
   * <ol>
   *   <li>Project root: {configRoot}/{fileName}
   *   <li>Resources directory: {configRoot}/src/main/resources/{fileName}
   * </ol>
   *
   * <p>The parent boundary has already been determined by {@link #findParentPomBoundary}, so we
   * should NOT search beyond configRoot.
   *
   * <p>Finally checks classpath for compiled resources.
   *
   * @param configRoot the root directory (parent boundary) to search at
   * @param fileName the file name to find
   * @return the path to the file, or null if not found (will fall back to classpath)
   */
  private Path findConfigFile(Path configRoot, String fileName) {
    // Only check at the configRoot level (parent boundary)
    // Do NOT walk up - the boundary was already determined by findParentPomBoundary

    // 1. Check project root
    Path configFile = configRoot.resolve(fileName);
    if (Files.exists(configFile)) {
      return configFile;
    }

    // 2. Check src/main/resources directory
    Path resourcesConfig = configRoot.resolve("src/main/resources").resolve(fileName);
    if (Files.exists(resourcesConfig)) {
      return resourcesConfig;
    }

    // 3. Check classpath (for compiled resources)
    InputStream stream = getClass().getClassLoader().getResourceAsStream(fileName);
    if (stream != null) {
      try {
        stream.close();
      } catch (IOException e) {
        // Ignore
      }
      // Return null - will load from classpath in loadFromYaml
      return null;
    }
    return null;
  }

  /**
   * Generates a default bootstrap.yaml configuration file.
   *
   * @param outputPath path to write the configuration file
   * @param projectBasePath project base path for module type detection
   */
  private void generateDefaultBootstrapConfig(Path outputPath, Path projectBasePath) {
    ModuleType moduleType = detectModuleType(outputPath.getParent());

    StringBuilder config = new StringBuilder();
    config.append("# SEA Platform Configuration\n");
    config.append("# Auto-generated - customize as needed\n");
    config.append("#\n");
    config.append("# Profile activation: -Dsea.profile=dev|int|prod\n");
    config.append("# See compliance-rules.yaml for available rules\n");
    config.append("\n");

    // Naming configuration
    config.append("# Naming conventions\n");
    config.append("naming:\n");
    config.append("  namespace: dev.engineeringlab\n");
    config.append("  project: architecture\n");
    config.append("\n");

    // JaCoCo thresholds based on module type
    if (moduleType != ModuleType.SPI && moduleType != ModuleType.BOM) {
      config.append("# Code coverage thresholds (0.0 - 1.0)\n");
      config.append("jacoco:\n");
      switch (moduleType) {
        case STANDARD_PARENT, CORE -> {
          config.append("  line:\n");
          config.append("    minimum: 0.80\n");
          config.append("  branch:\n");
          config.append("    minimum: 0.60\n");
        }
        default -> {
          config.append("  line:\n");
          config.append("    minimum: 0.70\n");
          config.append("  branch:\n");
          config.append("    minimum: 0.50\n");
        }
      }
      config.append("\n");
    }

    // Report configuration - always include for remediation support
    config.append("# Report configuration for remediation support\n");
    config.append("report:\n");
    config.append("  json:\n");
    config.append("    enabled: true\n");
    config.append("    output:\n");
    config.append("      path: target/compliance-reports/compliance-report.json\n");
    config.append("  consolidated: true\n");
    config.append("\n");

    // Module structure
    if (moduleType == ModuleType.STANDARD_PARENT) {
      config.append("# Module structure requirements\n");
      config.append("module:\n");
      config.append("  require:\n");
      config.append("    api: true\n");
      config.append("    core: true\n");
      config.append("    facade: true\n");
      config.append("    spi: false\n");
      config.append("\n");
    }

    // Rule overrides section (commented out as example)
    config.append("# Rule overrides (uncomment to customize)\n");
    config.append("# rules:\n");
    config.append("#   NC-029:\n");
    config.append("#     enabled: true\n");
    config.append("#     severity: WARNING\n");
    config.append("#     detection:\n");
    config.append("#       vendorPatterns:\n");
    config.append("#         - \"mycompany\"\n");

    try {
      Files.writeString(outputPath, config.toString());
    } catch (IOException e) {
      System.err.println(
          "Warning: Failed to generate default configuration file: " + e.getMessage());
    }
  }

  /** Loads configuration from a YAML file using SnakeYAML. */
  @SuppressWarnings("unchecked")
  private void loadFromYaml(ComplianceConfig config, Path yamlFile) {
    try (InputStream input = Files.newInputStream(yamlFile)) {
      loadFromYamlStream(config, input);
    } catch (IOException e) {
      System.err.println(
          "Warning: Failed to load YAML configuration from " + yamlFile + ": " + e.getMessage());
    }
  }

  /**
   * Loads configuration from a YAML file on the classpath.
   *
   * @param config the configuration to populate
   * @param resourceName the classpath resource name
   * @return true if loaded successfully, false if not found
   */
  private boolean loadFromClasspath(ComplianceConfig config, String resourceName) {
    InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
    if (stream == null) {
      return false;
    }
    try (stream) {
      loadFromYamlStream(config, stream);
      return true;
    } catch (IOException e) {
      System.err.println(
          "Warning: Failed to load YAML configuration from classpath "
              + resourceName
              + ": "
              + e.getMessage());
      return false;
    }
  }

  /** Loads configuration from a YAML input stream. */
  @SuppressWarnings("unchecked")
  private void loadFromYamlStream(ComplianceConfig config, InputStream input) {
    Yaml yaml = new Yaml();
    Map<String, Object> root = yaml.load(input);

    if (root == null) {
      return;
    }

    // Convert YAML map to Properties for standard config values
    Properties props = new Properties();
    flattenYamlToProperties(root, "", props);
    applyProperties(config, props);

    // Parse rules section for rule overrides
    if (root.containsKey("rules")) {
      Object rulesObj = root.get("rules");
      if (rulesObj instanceof Map) {
        parseRuleOverrides(config, (Map<String, Object>) rulesObj);
      }
    }

    // Parse modular design configuration
    if (root.containsKey("modular")) {
      Object modularObj = root.get("modular");
      if (modularObj instanceof Map) {
        parseModularDesignConfig(config, (Map<String, Object>) modularObj);
      }
    }
  }

  /**
   * Flattens a nested YAML map to Properties format. Skips the 'rules' and 'modular' keys as
   * they're handled separately.
   */
  @SuppressWarnings("unchecked")
  private void flattenYamlToProperties(Map<String, Object> map, String prefix, Properties props) {
    for (Map.Entry<String, Object> entry : map.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // Skip rules and modular sections - handled separately
      if ("rules".equals(key) || "modular".equals(key)) {
        continue;
      }

      String fullKey = prefix.isEmpty() ? key : prefix + "." + key;

      if (value instanceof Map) {
        flattenYamlToProperties((Map<String, Object>) value, fullKey, props);
      } else if (value != null) {
        props.setProperty(fullKey, value.toString());
      }
    }
  }

  /** Parses rule overrides from the 'rules' section of YAML config. */
  @SuppressWarnings("unchecked")
  private void parseRuleOverrides(ComplianceConfig config, Map<String, Object> rulesMap) {
    for (Map.Entry<String, Object> entry : rulesMap.entrySet()) {
      String ruleId = entry.getKey();
      Object ruleConfig = entry.getValue();

      if (!(ruleConfig instanceof Map)) {
        continue;
      }

      Map<String, Object> ruleMap = (Map<String, Object>) ruleConfig;
      ComplianceConfig.RuleOverride.RuleOverrideBuilder builder =
          ComplianceConfig.RuleOverride.builder();

      // Parse enabled
      if (ruleMap.containsKey("enabled")) {
        Object enabled = ruleMap.get("enabled");
        if (enabled instanceof Boolean) {
          builder.enabled((Boolean) enabled);
        } else if (enabled != null) {
          builder.enabled(Boolean.parseBoolean(enabled.toString()));
        }
      }

      // Parse severity
      if (ruleMap.containsKey("severity")) {
        Object severity = ruleMap.get("severity");
        if (severity != null) {
          builder.severity(severity.toString().toUpperCase());
        }
      }

      // Parse detection patterns
      if (ruleMap.containsKey("detection")) {
        Object detectionObj = ruleMap.get("detection");
        if (detectionObj instanceof Map) {
          Map<String, Object> detection = (Map<String, Object>) detectionObj;

          // Parse vendorPatterns
          if (detection.containsKey("vendorPatterns")) {
            Object patterns = detection.get("vendorPatterns");
            if (patterns instanceof List) {
              List<String> vendorPatterns = new ArrayList<>();
              for (Object pattern : (List<?>) patterns) {
                vendorPatterns.add(pattern.toString());
              }
              builder.additionalVendorPatterns(vendorPatterns);
            }
          }
        }
      }

      config.setRuleOverride(ruleId, builder.build());
    }
  }

  /**
   * Parses modular design configuration from the 'modular' section of YAML config.
   *
   * <p>Example YAML structure:
   *
   * <pre>
   * modular:
   *   type: pure-aggregator
   *   pureAggregator:
   *     modules:
   *       - name: logging-core
   *         type: library
   *       - name: metrics
   *         type: parent-aggregator
   *         layers: [common, api, core, facade]
   *   parentAggregator:
   *     layers:
   *       common: false
   *       spi: false
   *       api: true
   *       core: true
   *       facade: true
   *     customLayers: [impl-postgres]
   *   library:
   *     requireCoreSuffix: true
   *   mavenWrapper:
   *     required: true
   *     allowSystemMvn: true
   * </pre>
   */
  @SuppressWarnings("unchecked")
  private void parseModularDesignConfig(ComplianceConfig config, Map<String, Object> modularMap) {
    ComplianceConfig.ModularDesignConfig modularConfig = config.getModularDesign();

    // Parse type
    if (modularMap.containsKey("type")) {
      String typeStr = modularMap.get("type").toString().toUpperCase().replace("-", "_");
      try {
        modularConfig.setType(ComplianceConfig.ModuleArchitectureType.valueOf(typeStr));
      } catch (IllegalArgumentException e) {
        System.err.println("Warning: Unknown modular.type: " + modularMap.get("type"));
      }
    }

    // Parse pureAggregator section
    if (modularMap.containsKey("pureAggregator")) {
      Object pureAggObj = modularMap.get("pureAggregator");
      if (pureAggObj instanceof Map) {
        parsePureAggregatorConfig(
            modularConfig.getPureAggregator(), (Map<String, Object>) pureAggObj);
      }
    }

    // Parse parentAggregator section
    if (modularMap.containsKey("parentAggregator")) {
      Object parentAggObj = modularMap.get("parentAggregator");
      if (parentAggObj instanceof Map) {
        parseParentAggregatorConfig(
            modularConfig.getParentAggregator(), (Map<String, Object>) parentAggObj);
      }
    }

    // Parse library section
    if (modularMap.containsKey("library")) {
      Object libraryObj = modularMap.get("library");
      if (libraryObj instanceof Map) {
        parseLibraryConfig(modularConfig.getLibrary(), (Map<String, Object>) libraryObj);
      }
    }

    // Parse mavenWrapper section
    if (modularMap.containsKey("mavenWrapper")) {
      Object mavenWrapperObj = modularMap.get("mavenWrapper");
      if (mavenWrapperObj instanceof Map) {
        parseMavenWrapperConfig(
            modularConfig.getMavenWrapper(), (Map<String, Object>) mavenWrapperObj);
      }
    }
  }

  /** Parses Pure Aggregator configuration. */
  @SuppressWarnings("unchecked")
  private void parsePureAggregatorConfig(
      ComplianceConfig.PureAggregatorConfig pureAggConfig, Map<String, Object> configMap) {
    if (configMap.containsKey("modules")) {
      Object modulesObj = configMap.get("modules");
      if (modulesObj instanceof List) {
        List<ComplianceConfig.ModuleConfig> modules = new ArrayList<>();
        for (Object moduleObj : (List<?>) modulesObj) {
          if (moduleObj instanceof Map) {
            Map<String, Object> moduleMap = (Map<String, Object>) moduleObj;
            ComplianceConfig.ModuleConfig.ModuleConfigBuilder builder =
                ComplianceConfig.ModuleConfig.builder();

            if (moduleMap.containsKey("name")) {
              builder.name(moduleMap.get("name").toString());
            }
            if (moduleMap.containsKey("type")) {
              builder.type(moduleMap.get("type").toString());
            }
            if (moduleMap.containsKey("layers")) {
              Object layersObj = moduleMap.get("layers");
              if (layersObj instanceof List) {
                List<String> layers = new ArrayList<>();
                for (Object layer : (List<?>) layersObj) {
                  layers.add(layer.toString());
                }
                builder.layers(layers);
              }
            }
            modules.add(builder.build());
          }
        }
        pureAggConfig.setModules(modules);
      }
    }
  }

  /** Parses Parent Aggregator configuration. */
  @SuppressWarnings("unchecked")
  private void parseParentAggregatorConfig(
      ComplianceConfig.ParentAggregatorConfig parentAggConfig, Map<String, Object> configMap) {
    // Parse layers section
    if (configMap.containsKey("layers")) {
      Object layersObj = configMap.get("layers");
      if (layersObj instanceof Map) {
        Map<String, Object> layersMap = (Map<String, Object>) layersObj;
        ComplianceConfig.LayerConfig layers = parentAggConfig.getLayers();

        if (layersMap.containsKey("common")) {
          layers.setCommon(Boolean.parseBoolean(layersMap.get("common").toString()));
        }
        if (layersMap.containsKey("spi")) {
          layers.setSpi(Boolean.parseBoolean(layersMap.get("spi").toString()));
        }
        if (layersMap.containsKey("api")) {
          layers.setApi(Boolean.parseBoolean(layersMap.get("api").toString()));
        }
        if (layersMap.containsKey("core")) {
          layers.setCore(Boolean.parseBoolean(layersMap.get("core").toString()));
        }
        if (layersMap.containsKey("facade")) {
          layers.setFacade(Boolean.parseBoolean(layersMap.get("facade").toString()));
        }
      }
    }

    // Parse customLayers
    if (configMap.containsKey("customLayers")) {
      Object customLayersObj = configMap.get("customLayers");
      if (customLayersObj instanceof List) {
        List<String> customLayers = new ArrayList<>();
        for (Object layer : (List<?>) customLayersObj) {
          customLayers.add(layer.toString());
        }
        parentAggConfig.setCustomLayers(customLayers);
      }
    }
  }

  /** Parses Library configuration. */
  private void parseLibraryConfig(
      ComplianceConfig.LibraryConfig libraryConfig, Map<String, Object> configMap) {
    if (configMap.containsKey("requireCoreSuffix")) {
      libraryConfig.setRequireCoreSuffix(
          Boolean.parseBoolean(configMap.get("requireCoreSuffix").toString()));
    }
  }

  /** Parses Maven Wrapper configuration. */
  private void parseMavenWrapperConfig(
      ComplianceConfig.MavenWrapperConfig mavenWrapperConfig, Map<String, Object> configMap) {
    if (configMap.containsKey("required")) {
      mavenWrapperConfig.setRequired(Boolean.parseBoolean(configMap.get("required").toString()));
    }
    if (configMap.containsKey("allowSystemMvn")) {
      mavenWrapperConfig.setAllowSystemMvn(
          Boolean.parseBoolean(configMap.get("allowSystemMvn").toString()));
    }
  }

  /** Applies properties to configuration. */
  private void applyProperties(ComplianceConfig config, Properties props) {
    // JaCoCo Coverage Thresholds
    applyDoubleProperty(props, "jacoco.line.minimum", config::setJacocoLineMinimum);
    applyDoubleProperty(props, "jacoco.branch.minimum", config::setJacocoBranchMinimum);
    applyDoubleProperty(props, "jacoco.instruction.minimum", config::setJacocoInstructionMinimum);
    applyDoubleProperty(props, "jacoco.complexity.minimum", config::setJacocoComplexityMinimum);
    applyDoubleProperty(props, "jacoco.method.minimum", config::setJacocoMethodMinimum);
    applyDoubleProperty(props, "jacoco.class.minimum", config::setJacocoClassMinimum);

    // SpotBugs Configuration
    applyIntProperty(props, "spotbugs.max.exclusions", config::setSpotBugsMaxExclusions);
    applyBooleanProperty(
        props, "spotbugs.require.justification", config::setSpotBugsRequireJustification);

    // Module Structure
    applyBooleanProperty(props, "module.require.spi", config::setRequireSpiModule);
    applyBooleanProperty(props, "module.require.api", config::setRequireApiModule);
    applyBooleanProperty(props, "module.require.core", config::setRequireCoreModule);
    applyBooleanProperty(props, "module.require.facade", config::setRequireFacadeModule);

    // Namespace and Package Configuration
    applyStringProperty(props, "naming.namespace", config::setNamingNamespace);
    applyStringProperty(props, "naming.project", config::setProject);

    // Excluded Type Suffixes (comma-separated list of suffixes to exclude from parent artifact)
    String excludedTypeSuffixes = props.getProperty("naming.excludedTypeSuffixes");
    if (excludedTypeSuffixes != null && !excludedTypeSuffixes.trim().isEmpty()) {
      config.setNamingExcludedTypeSuffixes(
          new HashSet<>(
              Arrays.asList(excludedTypeSuffixes.split(",")).stream()
                  .map(String::trim)
                  .map(String::toLowerCase)
                  .collect(Collectors.toSet())));
    }

    // Code Quality
    applyBooleanProperty(
        props, "code.quality.fail.on.deprecated", config::setFailOnDeprecatedAnnotations);
    applyBooleanProperty(
        props, "code.quality.fail.on.suppress.warnings", config::setFailOnSuppressWarnings);

    // Report Configuration
    applyBooleanProperty(props, "report.json.enabled", config::setReportJsonEnabled);
    applyStringProperty(props, "report.json.output.path", config::setReportJsonOutputPath);
    applyBooleanProperty(props, "report.consolidated", config::setReportConsolidated);
    applyBooleanProperty(props, "report.only", config::setReportOnly);
    applyStringProperty(props, "remediation.report.dir", config::setRemediationReportDir);

    // Compliance Score Threshold
    applyDoubleProperty(props, "compliance.minScore", config::setComplianceMinScore);

    // Disabled Validators (comma-separated)
    String disabledValidators = props.getProperty("validators.disabled");
    if (disabledValidators != null && !disabledValidators.trim().isEmpty()) {
      config.setDisabledValidators(
          new HashSet<>(
              Arrays.asList(disabledValidators.split(",")).stream()
                  .map(String::trim)
                  .collect(Collectors.toList())));
    }

    // Disabled Rules (comma-separated)
    String disabledRules = props.getProperty("rules.disabled");
    if (disabledRules != null && !disabledRules.trim().isEmpty()) {
      config.setDisabledRules(
          new HashSet<>(
              Arrays.asList(disabledRules.split(",")).stream()
                  .map(String::trim)
                  .collect(Collectors.toList())));
    }

    // Severity Overrides (format: rule.severity.RULE-ID=SEVERITY)
    for (String key : props.stringPropertyNames()) {
      if (key.startsWith("rule.severity.")) {
        String ruleId = key.substring("rule.severity.".length());
        String severity = props.getProperty(key);
        config.getSeverityOverrides().put(ruleId, severity);
      }
    }
  }

  /**
   * Applies system property overrides to the configuration. System properties take precedence over
   * file configuration.
   */
  private void applySystemPropertyOverrides(ComplianceConfig config) {
    String reportJsonEnabled = System.getProperty("report.json.enabled");
    if (reportJsonEnabled != null) {
      config.setReportJsonEnabled(Boolean.parseBoolean(reportJsonEnabled));
    }

    String reportConsolidated = System.getProperty("report.consolidated");
    if (reportConsolidated != null) {
      config.setReportConsolidated(Boolean.parseBoolean(reportConsolidated));
    }

    String reportOutputPath = System.getProperty("report.json.output.path");
    if (reportOutputPath != null) {
      config.setReportJsonOutputPath(reportOutputPath);
    }

    String remediationReportDir = System.getProperty("remediation.report.dir");
    if (remediationReportDir != null) {
      config.setRemediationReportDir(remediationReportDir);
    }

    String project = System.getProperty("naming.project");
    if (project != null && !project.trim().isEmpty()) {
      config.setProject(project.trim());
    }
  }

  /** Applies a double property if present. */
  private void applyDoubleProperty(
      Properties props, String key, java.util.function.DoubleConsumer setter) {
    String value = props.getProperty(key);
    if (value != null) {
      try {
        setter.accept(Double.parseDouble(value));
      } catch (NumberFormatException e) {
        System.err.println("Warning: Invalid double value for " + key + ": " + value);
      }
    }
  }

  /** Applies an integer property if present. */
  private void applyIntProperty(
      Properties props, String key, java.util.function.IntConsumer setter) {
    String value = props.getProperty(key);
    if (value != null) {
      try {
        setter.accept(Integer.parseInt(value));
      } catch (NumberFormatException e) {
        System.err.println("Warning: Invalid integer value for " + key + ": " + value);
      }
    }
  }

  /** Applies a boolean property if present. */
  private void applyBooleanProperty(
      Properties props, String key, java.util.function.Consumer<Boolean> setter) {
    String value = props.getProperty(key);
    if (value != null) {
      setter.accept(Boolean.parseBoolean(value));
    }
  }

  /** Applies a string property if present. */
  private void applyStringProperty(
      Properties props, String key, java.util.function.Consumer<String> setter) {
    String value = props.getProperty(key);
    if (value != null && !value.trim().isEmpty()) {
      setter.accept(value.trim());
    }
  }

  /** Module types for configuration generation. */
  private enum ModuleType {
    STANDARD_PARENT,
    AGGREGATOR,
    BOM,
    API,
    CORE,
    FACADE,
    SPI,
    UNKNOWN
  }

  /** Detects the module type based on directory structure and naming. */
  private ModuleType detectModuleType(Path moduleBasePath) {
    if (moduleBasePath == null || !Files.isDirectory(moduleBasePath)) {
      return ModuleType.UNKNOWN;
    }

    Path fileName = moduleBasePath.getFileName();
    if (fileName == null) {
      return ModuleType.UNKNOWN;
    }
    String dirName = fileName.toString().toLowerCase();

    // Check pom.xml for packaging and modules
    Path pomPath = moduleBasePath.resolve("pom.xml");
    if (Files.exists(pomPath)) {
      PomScanner pomScanner = new PomScanner();
      Model pomModel = pomScanner.readPom(pomPath);

      if (pomScanner.isParentPom(pomModel)) {
        boolean hasModules = pomModel.getModules() != null && !pomModel.getModules().isEmpty();

        if (!hasModules) {
          return ModuleType.BOM;
        }

        List<String> modules = pomModel.getModules();
        String baseName = extractBaseName(dirName);

        boolean hasApiModule =
            modules.stream().anyMatch(m -> m.endsWith("-api") || m.equals(baseName + "-api"));
        boolean hasCoreModule =
            modules.stream().anyMatch(m -> m.endsWith("-core") || m.equals(baseName + "-core"));

        if (hasApiModule && hasCoreModule) {
          return ModuleType.STANDARD_PARENT;
        } else {
          return ModuleType.AGGREGATOR;
        }
      }
    }

    // Detect by directory name suffix
    if (dirName.endsWith("-api")) {
      return ModuleType.API;
    } else if (dirName.endsWith("-core")) {
      return ModuleType.CORE;
    } else if (dirName.endsWith("-facade")) {
      return ModuleType.FACADE;
    } else if (dirName.endsWith("-spi")) {
      return ModuleType.SPI;
    }

    return ModuleType.UNKNOWN;
  }

  /** Extracts the base name from a directory name by removing common suffixes. */
  private String extractBaseName(String dirName) {
    String[] suffixes = {"-api", "-core", "-facade", "-spi"};
    for (String suffix : suffixes) {
      if (dirName.endsWith(suffix)) {
        return dirName.substring(0, dirName.length() - suffix.length());
      }
    }
    return dirName;
  }

  /** Finds the parent pom boundary by walking up the directory tree. */
  private Path findParentPomBoundary(Path startPath) {
    Path searchPath = startPath;
    PomScanner pomScanner = new PomScanner();

    while (searchPath != null) {
      Path pomPath = searchPath.resolve("pom.xml");
      if (Files.exists(pomPath)) {
        Model pomModel = pomScanner.readPom(pomPath);
        if (pomModel != null && isParentPomBoundary(pomModel)) {
          return searchPath;
        }
      }
      searchPath = searchPath.getParent();
    }

    return startPath;
  }

  /** Checks if a pom.xml represents a parent pom boundary. */
  private boolean isParentPomBoundary(Model pomModel) {
    String artifactId = pomModel.getArtifactId();
    if (artifactId != null && artifactId.endsWith("-parent")) {
      return true;
    }
    return pomModel.getParent() == null;
  }

  /** Applies module-type-specific defaults for leaf modules. */
  private void applyModuleTypeDefaults(ComplianceConfig config, Path moduleBasePath) {
    ModuleType moduleType = detectModuleType(moduleBasePath);

    if (moduleType != ModuleType.STANDARD_PARENT) {
      config.setRequireSpiModule(false);
      config.setRequireApiModule(false);
      config.setRequireCoreModule(false);
      config.setRequireFacadeModule(false);
    }
  }
}
