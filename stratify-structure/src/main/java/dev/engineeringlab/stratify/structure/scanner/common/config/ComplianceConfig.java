package dev.engineeringlab.stratify.structure.scanner.common.config;

import dev.engineeringlab.stratify.structure.scanner.common.model.Severity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Configuration for the compliance plugin.
 *
 * <p>This class holds all configurable settings for compliance validation, including coverage
 * thresholds, enabled validators, and severity levels.
 */
public class ComplianceConfig {

  // Default values as constants
  private static final double DEFAULT_JACOCO_LINE_MINIMUM = 0.80;
  private static final double DEFAULT_JACOCO_BRANCH_MINIMUM = 0.60;
  private static final double DEFAULT_JACOCO_INSTRUCTION_MINIMUM = 0.80;
  private static final double DEFAULT_JACOCO_COMPLEXITY_MINIMUM = 0.60;
  private static final double DEFAULT_JACOCO_METHOD_MINIMUM = 0.75;
  private static final double DEFAULT_JACOCO_CLASS_MINIMUM = 0.90;
  private static final int DEFAULT_SPOTBUGS_MAX_EXCLUSIONS = 10;

  /** Minimum line coverage threshold (default: 80%). */
  private double jacocoLineMinimum = DEFAULT_JACOCO_LINE_MINIMUM;

  /** Minimum branch coverage threshold (default: 60%). */
  private double jacocoBranchMinimum = DEFAULT_JACOCO_BRANCH_MINIMUM;

  /** Minimum instruction coverage threshold (default: 80%). */
  private double jacocoInstructionMinimum = DEFAULT_JACOCO_INSTRUCTION_MINIMUM;

  /** Minimum cyclomatic complexity coverage threshold (default: 60%). */
  private double jacocoComplexityMinimum = DEFAULT_JACOCO_COMPLEXITY_MINIMUM;

  /** Minimum method coverage threshold (default: 75%). */
  private double jacocoMethodMinimum = DEFAULT_JACOCO_METHOD_MINIMUM;

  /** Minimum class coverage threshold (default: 90%). */
  private double jacocoClassMinimum = DEFAULT_JACOCO_CLASS_MINIMUM;

  /** Maximum number of SpotBugs exclusions allowed (default: 10). */
  private int spotBugsMaxExclusions = DEFAULT_SPOTBUGS_MAX_EXCLUSIONS;

  /** Whether SpotBugs exclusions must have justification comments (default: true). */
  private boolean spotBugsRequireJustification = true;

  /** Set of disabled validator names. */
  private Set<String> disabledValidators = new HashSet<>();

  /** Set of disabled rule IDs. */
  private Set<String> disabledRules = new HashSet<>();

  /** Map of rule ID to overridden severity level. */
  private Map<String, String> severityOverrides = new HashMap<>();

  /** Map of rule ID to rule override settings (enabled, severity, detection patterns). */
  private Map<String, RuleOverride> ruleOverrides = new HashMap<>();

  /** Whether SPI module is required (default: false). */
  private boolean requireSpiModule = false;

  /** Whether API module is required (default: true). */
  private boolean requireApiModule = true;

  /** Whether Core module is required (default: true). */
  private boolean requireCoreModule = true;

  /** Whether Facade module is required (default: true). */
  private boolean requireFacadeModule = true;

  /** Whether to fail on Deprecated annotations (default: false). */
  private boolean failOnDeprecatedAnnotations = false;

  /** Whether to fail on SuppressWarnings annotations (default: false). */
  private boolean failOnSuppressWarnings = false;

  /** Whether to generate and save JSON compliance reports (default: false). */
  private boolean reportJsonEnabled = false;

  /** Output path for JSON compliance reports. */
  private String reportJsonOutputPath = "target/compliance-reports/compliance-report.json";

  /** Whether to consolidate reports from multiple modules into a single report (default: true). */
  private boolean reportConsolidated = true;

  /** Whether to report violations without failing the build (default: false). */
  private boolean reportOnly = false;

  /** Directory for remediation reports (default: .remediation/reports). */
  private String remediationReportDir = ".remediation/reports";

  /** Minimum compliance score required for build to pass (default: 0.0 = disabled). */
  private double complianceMinScore = 0.0;

  /** Expected Java package namespace for modules (default: dev.engineeringlab). */
  private String namingNamespace = "dev.engineeringlab";

  /**
   * Project name used in package derivation (e.g., "myproject" -> dev.engineeringlab.myproject.*).
   */
  private String project = null;

  /** Type suffixes to exclude from parent artifact when calculating expected package. */
  private Set<String> namingExcludedTypeSuffixes = new HashSet<>();

  /** Modular design architecture configuration. */
  private ModularDesignConfig modularDesign = new ModularDesignConfig();

  /** Creates a default configuration. */
  public ComplianceConfig() {
    // Defaults are set via field initializers
  }

  /**
   * Creates a copy of the given configuration.
   *
   * @param other the configuration to copy
   */
  public ComplianceConfig(ComplianceConfig other) {
    this.jacocoLineMinimum = other.jacocoLineMinimum;
    this.jacocoBranchMinimum = other.jacocoBranchMinimum;
    this.jacocoInstructionMinimum = other.jacocoInstructionMinimum;
    this.jacocoComplexityMinimum = other.jacocoComplexityMinimum;
    this.jacocoMethodMinimum = other.jacocoMethodMinimum;
    this.jacocoClassMinimum = other.jacocoClassMinimum;
    this.spotBugsMaxExclusions = other.spotBugsMaxExclusions;
    this.spotBugsRequireJustification = other.spotBugsRequireJustification;
    this.disabledValidators = new HashSet<>(other.disabledValidators);
    this.disabledRules = new HashSet<>(other.disabledRules);
    this.severityOverrides = new HashMap<>(other.severityOverrides);
    this.ruleOverrides = new HashMap<>(other.ruleOverrides);
    this.requireSpiModule = other.requireSpiModule;
    this.requireApiModule = other.requireApiModule;
    this.requireCoreModule = other.requireCoreModule;
    this.requireFacadeModule = other.requireFacadeModule;
    this.failOnDeprecatedAnnotations = other.failOnDeprecatedAnnotations;
    this.failOnSuppressWarnings = other.failOnSuppressWarnings;
    this.reportJsonEnabled = other.reportJsonEnabled;
    this.reportJsonOutputPath = other.reportJsonOutputPath;
    this.reportConsolidated = other.reportConsolidated;
    this.reportOnly = other.reportOnly;
    this.remediationReportDir = other.remediationReportDir;
    this.complianceMinScore = other.complianceMinScore;
    this.namingNamespace = other.namingNamespace;
    this.project = other.project;
    this.namingExcludedTypeSuffixes = new HashSet<>(other.namingExcludedTypeSuffixes);
    this.modularDesign = new ModularDesignConfig(other.modularDesign);
  }

  // Getters and Setters

  public double getJacocoLineMinimum() {
    return jacocoLineMinimum;
  }

  public void setJacocoLineMinimum(double jacocoLineMinimumValue) {
    this.jacocoLineMinimum = jacocoLineMinimumValue;
  }

  public double getJacocoBranchMinimum() {
    return jacocoBranchMinimum;
  }

  public void setJacocoBranchMinimum(double jacocoBranchMinimumValue) {
    this.jacocoBranchMinimum = jacocoBranchMinimumValue;
  }

  public double getJacocoInstructionMinimum() {
    return jacocoInstructionMinimum;
  }

  public void setJacocoInstructionMinimum(double jacocoInstructionMinimumValue) {
    this.jacocoInstructionMinimum = jacocoInstructionMinimumValue;
  }

  public double getJacocoComplexityMinimum() {
    return jacocoComplexityMinimum;
  }

  public void setJacocoComplexityMinimum(double jacocoComplexityMinimumValue) {
    this.jacocoComplexityMinimum = jacocoComplexityMinimumValue;
  }

  public double getJacocoMethodMinimum() {
    return jacocoMethodMinimum;
  }

  public void setJacocoMethodMinimum(double jacocoMethodMinimumValue) {
    this.jacocoMethodMinimum = jacocoMethodMinimumValue;
  }

  public double getJacocoClassMinimum() {
    return jacocoClassMinimum;
  }

  public void setJacocoClassMinimum(double jacocoClassMinimumValue) {
    this.jacocoClassMinimum = jacocoClassMinimumValue;
  }

  public int getSpotBugsMaxExclusions() {
    return spotBugsMaxExclusions;
  }

  public void setSpotBugsMaxExclusions(int spotBugsMaxExclusionsValue) {
    this.spotBugsMaxExclusions = spotBugsMaxExclusionsValue;
  }

  public boolean isSpotBugsRequireJustification() {
    return spotBugsRequireJustification;
  }

  public void setSpotBugsRequireJustification(boolean spotBugsRequireJustificationValue) {
    this.spotBugsRequireJustification = spotBugsRequireJustificationValue;
  }

  public Set<String> getDisabledValidators() {
    return new HashSet<>(disabledValidators);
  }

  public void setDisabledValidators(Set<String> disabledValidatorsValue) {
    this.disabledValidators = new HashSet<>(disabledValidatorsValue);
  }

  public Set<String> getDisabledRules() {
    return new HashSet<>(disabledRules);
  }

  public void setDisabledRules(Set<String> disabledRulesValue) {
    this.disabledRules = new HashSet<>(disabledRulesValue);
  }

  public Map<String, String> getSeverityOverrides() {
    return new HashMap<>(severityOverrides);
  }

  public void setSeverityOverrides(Map<String, String> severityOverridesValue) {
    this.severityOverrides = new HashMap<>(severityOverridesValue);
  }

  public boolean isRequireSpiModule() {
    return requireSpiModule;
  }

  public void setRequireSpiModule(boolean requireSpiModuleValue) {
    this.requireSpiModule = requireSpiModuleValue;
  }

  public boolean isRequireApiModule() {
    return requireApiModule;
  }

  public void setRequireApiModule(boolean requireApiModuleValue) {
    this.requireApiModule = requireApiModuleValue;
  }

  public boolean isRequireCoreModule() {
    return requireCoreModule;
  }

  public void setRequireCoreModule(boolean requireCoreModuleValue) {
    this.requireCoreModule = requireCoreModuleValue;
  }

  public boolean isRequireFacadeModule() {
    return requireFacadeModule;
  }

  public void setRequireFacadeModule(boolean requireFacadeModuleValue) {
    this.requireFacadeModule = requireFacadeModuleValue;
  }

  public boolean isFailOnDeprecatedAnnotations() {
    return failOnDeprecatedAnnotations;
  }

  public void setFailOnDeprecatedAnnotations(boolean failOnDeprecatedAnnotationsValue) {
    this.failOnDeprecatedAnnotations = failOnDeprecatedAnnotationsValue;
  }

  public boolean isFailOnSuppressWarnings() {
    return failOnSuppressWarnings;
  }

  public void setFailOnSuppressWarnings(boolean failOnSuppressWarningsValue) {
    this.failOnSuppressWarnings = failOnSuppressWarningsValue;
  }

  public boolean isReportJsonEnabled() {
    return reportJsonEnabled;
  }

  public void setReportJsonEnabled(boolean reportJsonEnabledValue) {
    this.reportJsonEnabled = reportJsonEnabledValue;
  }

  public String getReportJsonOutputPath() {
    return reportJsonOutputPath;
  }

  public void setReportJsonOutputPath(String reportJsonOutputPathValue) {
    this.reportJsonOutputPath = reportJsonOutputPathValue;
  }

  public boolean isReportConsolidated() {
    return reportConsolidated;
  }

  public void setReportConsolidated(boolean reportConsolidatedValue) {
    this.reportConsolidated = reportConsolidatedValue;
  }

  public boolean isReportOnly() {
    return reportOnly;
  }

  public void setReportOnly(boolean reportOnlyValue) {
    this.reportOnly = reportOnlyValue;
  }

  public String getRemediationReportDir() {
    return remediationReportDir;
  }

  public void setRemediationReportDir(String remediationReportDirValue) {
    this.remediationReportDir = remediationReportDirValue;
  }

  public double getComplianceMinScore() {
    return complianceMinScore;
  }

  public void setComplianceMinScore(double complianceMinScoreValue) {
    this.complianceMinScore = complianceMinScoreValue;
  }

  public String getNamingNamespace() {
    return namingNamespace;
  }

  public void setNamingNamespace(String namingNamespaceValue) {
    this.namingNamespace = namingNamespaceValue;
  }

  public String getProject() {
    return project;
  }

  public void setProject(String projectValue) {
    this.project = projectValue;
  }

  public Set<String> getNamingExcludedTypeSuffixes() {
    return new HashSet<>(namingExcludedTypeSuffixes);
  }

  public void setNamingExcludedTypeSuffixes(Set<String> suffixes) {
    this.namingExcludedTypeSuffixes = suffixes != null ? new HashSet<>(suffixes) : new HashSet<>();
  }

  public ModularDesignConfig getModularDesign() {
    return modularDesign;
  }

  public void setModularDesign(ModularDesignConfig modularDesignValue) {
    this.modularDesign =
        modularDesignValue != null ? modularDesignValue : new ModularDesignConfig();
  }

  public Map<String, RuleOverride> getRuleOverrides() {
    return new HashMap<>(ruleOverrides);
  }

  public void setRuleOverrides(Map<String, RuleOverride> ruleOverridesValue) {
    this.ruleOverrides = new HashMap<>(ruleOverridesValue);
  }

  public RuleOverride getRuleOverride(String ruleId) {
    return ruleOverrides.get(ruleId);
  }

  public void setRuleOverride(String ruleId, RuleOverride override) {
    ruleOverrides.put(ruleId, override);
  }

  /**
   * Checks if a validator is enabled.
   *
   * @param validatorName The validator name (e.g., "ModuleStructureValidator")
   * @return true if enabled, false if disabled
   */
  public boolean isValidatorEnabled(String validatorName) {
    return !disabledValidators.contains(validatorName);
  }

  /**
   * Checks if a rule is enabled.
   *
   * @param ruleId The rule ID (e.g., "MS-001", "CQ-010")
   * @return true if enabled, false if disabled
   */
  public boolean isRuleEnabled(String ruleId) {
    return !disabledRules.contains(ruleId);
  }

  /**
   * Gets the severity override for a rule, if any.
   *
   * @param ruleId The rule ID
   * @return Overridden severity, or null if no override
   */
  public String getSeverityOverride(String ruleId) {
    return severityOverrides.get(ruleId);
  }

  /**
   * Resolves the severity for a rule, considering any configured overrides.
   *
   * @param ruleId The rule ID (e.g., "SS-001", "CN-001")
   * @param defaultSeverity The default severity to use if no override is configured
   * @return The resolved severity (either from override or default)
   */
  public Severity resolveSeverity(String ruleId, Severity defaultSeverity) {
    String override = severityOverrides.get(ruleId);
    if (override != null && !override.isBlank()) {
      try {
        return Severity.valueOf(override.toUpperCase().trim());
      } catch (IllegalArgumentException e) {
        // Invalid severity name in config, fall back to default
        return defaultSeverity;
      }
    }
    return defaultSeverity;
  }

  /** Rule override settings for customizing individual rules via configuration. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RuleOverride {
    /** Whether the rule is enabled (null means use default). */
    private Boolean enabled;

    /** Override severity level (null means use default). */
    private String severity;

    /** Additional vendor patterns to detect (merged with defaults). */
    @Builder.Default private List<String> additionalVendorPatterns = new ArrayList<>();
  }

  /**
   * Configuration for modular design architecture (MD-001 to MD-007).
   *
   * <p>Defines the expected module structure including Pure Aggregators, Parent Aggregators,
   * Libraries, and Submodules.
   *
   * @see <a href="doc/3-design/modular-design-architecture.md">Modular Design Architecture</a>
   */
  @Data
  @NoArgsConstructor
  public static class ModularDesignConfig {

    /**
     * Module architecture type.
     *
     * <ul>
     *   <li>{@code auto} - Auto-detect based on pom.xml structure (default)
     *   <li>{@code pure-aggregator} - This module is a Pure Aggregator
     *   <li>{@code parent-aggregator} - This module is a Parent Aggregator
     *   <li>{@code library} - This module is a standalone Library
     * </ul>
     */
    private ModuleArchitectureType type = ModuleArchitectureType.AUTO;

    /** Configuration for Pure Aggregator modules. */
    private PureAggregatorConfig pureAggregator = new PureAggregatorConfig();

    /** Configuration for Parent Aggregator modules. */
    private ParentAggregatorConfig parentAggregator = new ParentAggregatorConfig();

    /** Configuration for Library modules. */
    private LibraryConfig library = new LibraryConfig();

    /** Maven wrapper configuration. */
    private MavenWrapperConfig mavenWrapper = new MavenWrapperConfig();

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public ModularDesignConfig(ModularDesignConfig other) {
      this.type = other.type;
      this.pureAggregator = new PureAggregatorConfig(other.pureAggregator);
      this.parentAggregator = new ParentAggregatorConfig(other.parentAggregator);
      this.library = new LibraryConfig(other.library);
      this.mavenWrapper = new MavenWrapperConfig(other.mavenWrapper);
    }
  }

  /** Module architecture type enumeration. */
  public enum ModuleArchitectureType {
    /** Auto-detect module type based on pom.xml structure. */
    AUTO,
    /** Pure Aggregator - groups related domains, no parent, has mvnw. */
    PURE_AGGREGATOR,
    /** Parent Aggregator - manages submodules with shared config. */
    PARENT_AGGREGATOR,
    /** Library - standalone utility module with -core suffix. */
    LIBRARY
  }

  /** Configuration for Pure Aggregator modules. */
  @Data
  @NoArgsConstructor
  public static class PureAggregatorConfig {
    /** List of child module configurations. */
    private List<ModuleConfig> modules = new ArrayList<>();

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public PureAggregatorConfig(PureAggregatorConfig other) {
      this.modules = other.modules.stream().map(ModuleConfig::new).collect(Collectors.toList());
    }
  }

  /** Configuration for a child module within a Pure Aggregator. */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class ModuleConfig {
    /** Module name (directory name). */
    private String name;

    /** Module type: library or parent-aggregator. */
    private String type;

    /** Required layers for parent-aggregator type (e.g., [common, api, core, facade]). */
    @Builder.Default private List<String> layers = new ArrayList<>();

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public ModuleConfig(ModuleConfig other) {
      this.name = other.name;
      this.type = other.type;
      this.layers = new ArrayList<>(other.layers);
    }
  }

  /** Configuration for Parent Aggregator modules. */
  @Data
  @NoArgsConstructor
  public static class ParentAggregatorConfig {
    /** Layer configuration for submodules. */
    private LayerConfig layers = new LayerConfig();

    /** Additional custom layers allowed (e.g., impl-postgres, impl-redis). */
    private List<String> customLayers = new ArrayList<>();

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public ParentAggregatorConfig(ParentAggregatorConfig other) {
      this.layers = new LayerConfig(other.layers);
      this.customLayers = new ArrayList<>(other.customLayers);
    }
  }

  /** Configuration for required/optional layers in a Parent Aggregator. */
  @Data
  @NoArgsConstructor
  public static class LayerConfig {
    /** Whether common layer ({domain}-common) is required. */
    private boolean common = false;

    /** Whether SPI layer ({domain}-spi) is required. */
    private boolean spi = false;

    /** Whether API layer ({domain}-api) is required. */
    private boolean api = true;

    /** Whether Core layer ({domain}-core) is required. */
    private boolean core = true;

    /** Whether Facade layer ({domain}-facade) is required. */
    private boolean facade = true;

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public LayerConfig(LayerConfig other) {
      this.common = other.common;
      this.spi = other.spi;
      this.api = other.api;
      this.core = other.core;
      this.facade = other.facade;
    }

    /**
     * Gets the list of required layer suffixes.
     *
     * @return list of required layer suffixes (e.g., ["-api", "-core", "-facade"])
     */
    public List<String> getRequiredLayers() {
      List<String> required = new ArrayList<>();
      if (common) {
        required.add("-common");
      }
      if (spi) {
        required.add("-spi");
      }
      if (api) {
        required.add("-api");
      }
      if (core) {
        required.add("-core");
      }
      if (facade) {
        required.add("-facade");
      }
      return required;
    }

    /**
     * Checks if a layer is required by suffix.
     *
     * @param suffix the layer suffix (e.g., "-api", "-core")
     * @return true if the layer is required
     */
    public boolean isLayerRequired(String suffix) {
      return switch (suffix) {
        case "-common" -> common;
        case "-spi" -> spi;
        case "-api" -> api;
        case "-core" -> core;
        case "-facade" -> facade;
        default -> false;
      };
    }
  }

  /** Configuration for Library modules. */
  @Data
  @NoArgsConstructor
  public static class LibraryConfig {
    /** Whether library must end with -core suffix (default: true). */
    private boolean requireCoreSuffix = true;

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public LibraryConfig(LibraryConfig other) {
      this.requireCoreSuffix = other.requireCoreSuffix;
    }
  }

  /** Configuration for Maven wrapper requirements. */
  @Data
  @NoArgsConstructor
  public static class MavenWrapperConfig {
    /** Whether Maven wrapper (mvnw) is required at build root (default: true). */
    private boolean required = true;

    /** Whether system mvn command is allowed as alternative (default: true). */
    private boolean allowSystemMvn = true;

    /**
     * Creates a copy of the given configuration.
     *
     * @param other the configuration to copy
     */
    public MavenWrapperConfig(MavenWrapperConfig other) {
      this.required = other.required;
      this.allowSystemMvn = other.allowSystemMvn;
    }
  }
}
