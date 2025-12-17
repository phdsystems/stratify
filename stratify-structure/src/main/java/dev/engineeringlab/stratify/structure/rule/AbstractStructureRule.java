package dev.engineeringlab.stratify.structure.rule;

import dev.engineeringlab.stratify.structure.model.Category;
import dev.engineeringlab.stratify.structure.model.Severity;
import dev.engineeringlab.stratify.structure.model.StructureViolation;
import java.nio.file.Path;
import java.util.List;

/**
 * Abstract base class for structure rules.
 *
 * <p>Provides common functionality for structure rules including:
 *
 * <ul>
 *   <li>Rule metadata loaded from configuration files (.properties or .yaml)
 *   <li>Violation creation helpers
 *   <li>Standard validation lifecycle
 * </ul>
 *
 * <p>Rule metadata (name, description, severity, fix, reference) is loaded from configuration
 * files. Configuration can be provided in:
 *
 * <ul>
 *   <li>.properties format (zero dependency, always supported)
 *   <li>.yaml format (requires SnakeYAML on classpath, optional)
 * </ul>
 *
 * <p>Subclasses must implement:
 *
 * <ul>
 *   <li>{@link #appliesTo(Path)} - Filter which paths this rule applies to
 *   <li>{@link #doValidate(Path)} - Perform the actual validation
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>
 * public class ModuleStructureRule extends AbstractStructureRule {
 *
 *     public ModuleStructureRule() {
 *         super("MS-001");
 *     }
 *
 *     {@literal @}Override
 *     protected boolean appliesTo(Path modulePath) {
 *         return Files.exists(modulePath.resolve("pom.xml"));
 *     }
 *
 *     {@literal @}Override
 *     protected List&lt;StructureViolation&gt; doValidate(Path modulePath) {
 *         // Validation logic here
 *         return List.of();
 *     }
 * }
 * </pre>
 */
public abstract class AbstractStructureRule {

  /** Maximum number of violations to report per rule per module. */
  protected static final int MAX_VIOLATIONS = 3;

  protected final String ruleId;
  protected final String name;
  protected final String description;
  protected final Severity severity;
  protected final Category category;
  protected final String fix;
  protected final String reference;
  protected final RuleDefinition ruleDefinition;
  protected boolean enabled;

  /**
   * Creates a structure rule that loads metadata from configuration.
   *
   * <p>The rule loader must be initialized with rule definitions before creating rule instances.
   * Typically, this is done at application startup:
   *
   * <pre>
   * RuleLoader loader = new RuleLoader()
   *     .loadFromClasspath("structure-rules.properties")
   *     .loadFromClasspath("structure-rules.yaml");  // optional, if SnakeYAML available
   * </pre>
   *
   * @param ruleId the rule ID (e.g., "MS-001")
   * @param ruleLoader the rule loader containing rule definitions
   * @throws IllegalStateException if rule is not defined in configuration
   */
  protected AbstractStructureRule(String ruleId, RuleLoader ruleLoader) {
    this.ruleId = ruleId;
    this.ruleDefinition = ruleLoader.getRule(ruleId);

    if (ruleDefinition == null) {
      throw new IllegalStateException(
          "Rule "
              + ruleId
              + " not found in rule configuration. "
              + "All structure rules must be defined in .properties or .yaml files.");
    }

    this.name = ruleDefinition.name();
    this.description = ruleDefinition.description();
    this.severity = ruleDefinition.severity();
    this.category = ruleDefinition.category();
    this.fix = ruleDefinition.fix();
    this.reference = ruleDefinition.reference();
    this.enabled = ruleDefinition.enabled();
  }

  /**
   * Alternative constructor that creates a shared rule loader.
   *
   * <p>This constructor creates a rule loader that attempts to load from both .properties and .yaml
   * files (if SnakeYAML is available).
   *
   * @param ruleId the rule ID
   * @param configResourceName the configuration resource name (e.g., "structure-rules")
   * @throws IllegalStateException if rule is not defined in configuration
   */
  protected AbstractStructureRule(String ruleId, String configResourceName) {
    this(ruleId, createDefaultLoader(configResourceName));
  }

  /** Creates a default rule loader that loads from both .properties and .yaml files. */
  private static RuleLoader createDefaultLoader(String configResourceName) {
    RuleLoader loader = new RuleLoader();

    // Try .properties first (always supported)
    loader.loadFromClasspath(configResourceName + ".properties");

    // Try .yaml if SnakeYAML is available
    if (loader.isYamlSupported()) {
      loader.loadFromClasspath(configResourceName + ".yaml");
    }

    return loader;
  }

  /** Gets the rule ID. */
  public String getRuleId() {
    return ruleId;
  }

  /** Gets the rule name. */
  public String getName() {
    return name;
  }

  /** Gets the rule description. */
  public String getDescription() {
    return description;
  }

  /** Gets the rule severity. */
  public Severity getSeverity() {
    return severity;
  }

  /** Gets the rule category. */
  public Category getCategory() {
    return category;
  }

  /** Gets the fix description. */
  public String getFix() {
    return fix;
  }

  /** Gets the reference documentation. */
  public String getReference() {
    return reference;
  }

  /** Gets the rule definition. */
  public RuleDefinition getRuleDefinition() {
    return ruleDefinition;
  }

  /** Checks if this rule is enabled. */
  public boolean isEnabled() {
    return enabled;
  }

  /** Sets whether this rule is enabled. */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Validates the given path.
   *
   * <p>This method first checks if the rule applies to the path using {@link #appliesTo(Path)}. If
   * it does, it calls {@link #doValidate(Path)} to perform the actual validation.
   *
   * @param path the path to validate (typically a module root directory)
   * @return list of violations found (empty if no violations)
   */
  public List<StructureViolation> validate(Path path) {
    if (!enabled) {
      return List.of();
    }
    if (!appliesTo(path)) {
      return List.of();
    }
    return doValidate(path);
  }

  /**
   * Creates a violation for this rule.
   *
   * @param target the target that violated the rule (e.g., module name)
   * @param message the violation message
   * @return the violation
   */
  protected StructureViolation createViolation(String target, String message) {
    return StructureViolation.builder()
        .ruleId(ruleId)
        .ruleName(name)
        .target(target)
        .message(message)
        .severity(severity)
        .category(category)
        .fix(fix)
        .reference(reference)
        .build();
  }

  /**
   * Creates a violation with a specific location.
   *
   * @param target the target that violated the rule
   * @param message the violation message
   * @param location the specific location (e.g., file path, line number)
   * @return the violation
   */
  protected StructureViolation createViolation(String target, String message, String location) {
    return StructureViolation.builder()
        .ruleId(ruleId)
        .ruleName(name)
        .target(target)
        .message(message)
        .severity(severity)
        .category(category)
        .location(location)
        .fix(fix)
        .reference(reference)
        .build();
  }

  /**
   * Checks if this rule applies to the given path.
   *
   * <p>Subclasses should override this method to filter which paths they validate. For example, a
   * rule might only apply to modules with a pom.xml file, or only to modules in a specific
   * directory structure.
   *
   * @param path the path to check
   * @return true if this rule should validate the path
   */
  protected abstract boolean appliesTo(Path path);

  /**
   * Performs the actual validation.
   *
   * <p>This method is called only if {@link #appliesTo(Path)} returns true. Subclasses should
   * implement their validation logic here.
   *
   * @param path the path to validate
   * @return list of violations found (empty if no violations)
   */
  protected abstract List<StructureViolation> doValidate(Path path);
}
