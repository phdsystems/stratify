package dev.engineeringlab.stratify.structure.scanner.common.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.Severity;
import dev.engineeringlab.stratify.structure.scanner.common.model.ViolationCategory;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a compliance rule definition loaded from YAML.
 *
 * <p>Rule definitions contain all metadata and detection criteria for a compliance rule, allowing
 * rules to be defined declaratively in YAML rather than programmatically in Java.
 */
@Data
@Builder
public class RuleDefinition {

  /** Unique rule identifier (e.g., NC-027, DP-009). */
  private String id;

  /** Human-readable rule name. */
  private String name;

  /** Detailed description of what the rule checks. */
  private String description;

  /** Rule category (e.g., NAMING_CONVENTIONS, DEPENDENCIES). */
  @Builder.Default private ViolationCategory category = ViolationCategory.CODE_QUALITY;

  /** Default severity level for violations. */
  @Builder.Default private Severity severity = Severity.ERROR;

  /** Whether this rule is enabled by default. */
  @Builder.Default private boolean enabled = true;

  /** Module patterns to target (e.g., "*-api", "*-spi"). */
  @Builder.Default private List<String> targetModules = new ArrayList<>();

  /** Detection criteria for this rule. */
  private DetectionCriteria detection;

  /** Reason explaining why this rule exists. */
  private String reason;

  /** Instructions on how to fix violations. */
  private String fix;

  /** Reference to documentation. */
  private String reference;

  /** Detection criteria for identifying violations. */
  @Data
  @Builder
  public static class DetectionCriteria {

    /** File path patterns to match (e.g., "/model/", "/exception/"). */
    @Builder.Default private List<String> pathPatterns = new ArrayList<>();

    /** File name patterns to match (e.g., "*Exception.java"). */
    @Builder.Default private List<String> filePatterns = new ArrayList<>();

    /** Class-level detection patterns. */
    private ClassPatterns classPatterns;

    /** Dependency detection patterns (for dependency rules). */
    private DependencyPatterns dependencyPatterns;

    /** POM element detection patterns (for module structure rules). */
    private PomPatterns pomPatterns;

    /** Vendor/tool name patterns to detect in package/class names. */
    @Builder.Default private List<String> vendorPatterns = new ArrayList<>();

    /** Naming convention patterns (for NC-* rules). */
    private NamingPatterns namingPatterns;
  }

  /**
   * Patterns for detecting naming convention violations.
   *
   * <p>Used by NC-* rules to validate naming conventions for:
   *
   * <ul>
   *   <li>Maven coordinates (groupId, artifactId)
   *   <li>Java packages and classes
   *   <li>Module and directory structure
   * </ul>
   */
  @Data
  @Builder
  public static class NamingPatterns {

    /** Scope for the rule (api, core, facade, spi, common). */
    private String scope;

    /** Required groupId value. */
    private String groupId;

    /** Required package prefix. */
    private String packagePrefix;

    /** Pattern that artifactId must NOT start with. */
    private String artifactIdMustNotStartWith;

    /** Pattern that artifactId must NOT contain. */
    private String artifactIdMustNotContain;

    /** Pattern that artifactId must NOT end with. */
    private String mustNotEndWith;

    /** Pattern that directory must end with. */
    private String directoryMustEndWith;

    /** Pattern that interfaces must NOT end with. */
    private String interfacesMustNotEndWith;

    /** Pattern that packages must start with. */
    private String packagesMustStartWith;

    /** Pattern that classes must end with. */
    private String classesMustEndWith;

    /** Pattern that parent module artifactId must end with. */
    private String parentModuleMustEndWith;

    /** Pattern that packages must contain. */
    private String packagesMustContain;

    /** Pattern that enums must end with. */
    private String enumsMustEndWith;

    /** Whether classes must be singular (not plural). */
    @Builder.Default private boolean classesMustBeSingular = false;

    /** Whether packages must be singular (not plural). */
    @Builder.Default private boolean packagesMustBeSingular = false;

    /** Whether artifactId must be singular (not plural). */
    @Builder.Default private boolean artifactIdMustBeSingular = false;

    /** Whether constants must be defined as enums. */
    @Builder.Default private boolean constantsMustBeEnums = false;

    /** Whether package names must match Maven coordinates. */
    @Builder.Default private boolean packageMatchesMavenCoordinates = false;

    /** Whether submodules must explicitly declare groupId. */
    @Builder.Default private boolean submodulesMustDeclareGroupId = false;

    /** Required suffixes for submodules. */
    @Builder.Default private List<String> submodulesMustHaveSuffix = new ArrayList<>();

    /** Patterns that modules must NOT end with. */
    @Builder.Default private List<String> modulesMustNotEndWith = new ArrayList<>();

    /** Required suffixes for aggregator artifactId. */
    @Builder.Default private List<String> aggregatorMustEndWith = new ArrayList<>();

    /** Allowed exceptions for pattern matching. */
    @Builder.Default private List<String> exceptions = new ArrayList<>();

    /** Allowed shared packages for NC-017. */
    @Builder.Default private List<String> allowedSharedPackages = new ArrayList<>();

    /** Module pattern for where models must be placed. */
    private String modelsMustBeIn;

    /** Module pattern for where exceptions must be placed. */
    private String exceptionsMustBeIn;

    /** Vendor name categories to check against. */
    private Object mustNotContainVendorNames;
  }

  /** Patterns for detecting classes. */
  @Data
  @Builder
  public static class ClassPatterns {

    /** Whether to detect Java records. */
    @Builder.Default private boolean detectRecords = false;

    /** Annotations to detect (e.g., "Data", "Value"). */
    @Builder.Default private List<String> annotations = new ArrayList<>();

    /** Class name suffixes to detect (e.g., "DTO", "Request"). */
    @Builder.Default private List<String> suffixes = new ArrayList<>();

    /** Parent classes/interfaces to detect (e.g., "Exception", "RuntimeException"). */
    @Builder.Default private List<String> extendsClasses = new ArrayList<>();

    /** Whether to detect interfaces. */
    @Builder.Default private boolean detectInterfaces = false;

    /** Whether to detect enums. */
    @Builder.Default private boolean detectEnums = false;
  }

  /** Patterns for detecting dependency issues. */
  @Data
  @Builder
  public static class DependencyPatterns {

    /** Dependencies that must NOT be present (forbidden). */
    @Builder.Default private List<String> mustNotContain = new ArrayList<>();

    /** Dependencies that MUST be present (required). */
    @Builder.Default private List<String> mustContain = new ArrayList<>();

    /** Dependency group patterns to check. */
    @Builder.Default private List<String> groupPatterns = new ArrayList<>();

    /** Dependency artifact patterns to check. */
    @Builder.Default private List<String> artifactPatterns = new ArrayList<>();

    /** Dependencies to exclude from mustNotContain checks (allowed exceptions). */
    @Builder.Default private List<String> exceptions = new ArrayList<>();

    /**
     * Submodule scope for the rule (api, core, facade, spi). When set, the rule only applies to
     * that specific submodule type.
     */
    private String scope;

    /**
     * Required sibling dependencies (uses placeholders like {base}-api). Used for rules like "core
     * must depend on api".
     */
    @Builder.Default private List<String> requiresSiblings = new ArrayList<>();

    /**
     * Condition for when this rule applies. E.g., "hasSpi" means only check if SPI module exists.
     */
    private String condition;
  }

  /** Patterns for detecting POM structure issues. */
  @Data
  @Builder
  public static class PomPatterns {

    /** Required modules in parent POM. */
    @Builder.Default private List<String> requiredModules = new ArrayList<>();

    /** Module ordering requirements. */
    @Builder.Default private List<String> moduleOrder = new ArrayList<>();

    /** Required POM elements. */
    @Builder.Default private List<String> requiredElements = new ArrayList<>();

    /** Whether the module must have a parent POM declared. */
    @Builder.Default private boolean requireParent = false;

    /** Whether the module must NOT have a parent POM declared. */
    @Builder.Default private boolean requireNoParent = false;

    /** Required suffix for parent artifactId (e.g., "-parent"). */
    private String parentArtifactIdSuffix;

    /** Whether the module must NOT contain source code (src/main/java, src/test/java). */
    @Builder.Default private boolean noSourceCode = false;

    /** Whether the module must NOT have direct dependencies (only dependencyManagement). */
    @Builder.Default private boolean noDependencies = false;

    /** Whether all subdirectories with pom.xml must be listed in modules section. */
    @Builder.Default private boolean allSubmodulesListed = false;

    /** Required suffix for pure aggregator modules (e.g., "-aggregator"). */
    private String pureAggregatorSuffix;

    /** Required suffixes for aggregator artifactId (e.g., ["-parent", "-common"]). */
    @Builder.Default private List<String> artifactIdMustEndWith = new ArrayList<>();
  }
}
