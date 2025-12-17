package dev.engineeringlab.stratify.structure;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import dev.engineeringlab.stratify.structure.rule.impl.ApiModuleExists;
import dev.engineeringlab.stratify.structure.rule.impl.ComponentCompleteness;
import dev.engineeringlab.stratify.structure.rule.impl.CoreModuleExists;
import dev.engineeringlab.stratify.structure.rule.impl.FacadeModuleExists;
import dev.engineeringlab.stratify.structure.rule.impl.NoCommonLayer;
import dev.engineeringlab.stratify.structure.rule.impl.NoUtilModules;
import dev.engineeringlab.stratify.structure.rule.impl.SpiModuleConditional;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * SEA-4 Structure Rules - Main entry point for structure validation.
 *
 * <p>This facade provides convenient access to all structure validation rules for stratified
 * architecture. It follows the same pattern as the stratify-rules module's {@code Rules} class but
 * focuses on project and module structure rather than class-level dependencies.
 *
 * <p>Structure rules validate:
 *
 * <ul>
 *   <li>Module existence and completeness
 *   <li>Prohibited module patterns (common, util)
 *   <li>Component organization
 *   <li>Architectural conformance
 * </ul>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * // Check all SEA-4 rules
 * StructureRules.enforce(Path.of("."));
 *
 * // Get violations without throwing
 * List<StructureViolation> violations = StructureRules.check(Path.of("."));
 *
 * // Check specific rules
 * StructureRules.noCommonLayer().enforce(Path.of("my-component"));
 *
 * // Check multiple rules
 * List<StructureRule> rules = List.of(
 *     StructureRules.apiModuleExists(),
 *     StructureRules.coreModuleExists()
 * );
 * for (StructureRule rule : rules) {
 *     rule.enforce(projectPath);
 * }
 * }</pre>
 *
 * <h2>Available Rules</h2>
 *
 * <ul>
 *   <li>{@link #apiModuleExists()} - SEA-101: API module must exist
 *   <li>{@link #coreModuleExists()} - SEA-102: Core module must exist
 *   <li>{@link #facadeModuleExists()} - SEA-103: Facade module recommended for complex components
 *   <li>{@link #spiModuleConditional()} - SEA-104: SPI module recommended when appropriate
 *   <li>{@link #componentCompleteness()} - SEA-105: Components must be complete (api + core)
 *   <li>{@link #noCommonLayer()} - SEA-4: Common layer is prohibited
 *   <li>{@link #noUtilModules()} - SEA-106: Utility modules are prohibited
 * </ul>
 *
 * @see StructureRule
 * @see StructureViolation
 */
public final class StructureRules {

  private static final RuleLoader RULE_LOADER = createDefaultRuleLoader();

  private StructureRules() {
    // Prevent instantiation
  }

  /**
   * Creates and configures the default rule loader.
   *
   * <p>Loads rule definitions from classpath resources in the following order:
   *
   * <ol>
   *   <li>structure-rules.properties (zero dependency, always loaded)
   *   <li>structure-rules.yaml (optional, if SnakeYAML available)
   * </ol>
   */
  private static RuleLoader createDefaultRuleLoader() {
    RuleLoader loader = new RuleLoader();

    // Load from properties (zero dependency, always supported)
    loader.loadFromClasspath("structure-rules.properties");

    // Load from YAML if available (optional, requires SnakeYAML)
    if (loader.isYamlSupported()) {
      loader.loadFromClasspath("structure-rules.yaml");
    }

    return loader;
  }

  /**
   * Rule: API module must exist for each component.
   *
   * <p>Validates that for each component base, an API module ({base}-api) exists with a pom.xml
   * file. The API module contains public contracts, interfaces, and DTOs that external consumers
   * depend on.
   *
   * @return the API module exists rule (SEA-101)
   */
  public static StructureRule apiModuleExists() {
    return new ApiModuleExists(RULE_LOADER)::validate;
  }

  /**
   * Rule: Core module must exist for each component.
   *
   * <p>Validates that for each component base, a core module ({base}-core) exists with a pom.xml
   * file. The core module contains the implementation logic and business rules.
   *
   * @return the core module exists rule (SEA-102)
   */
  public static StructureRule coreModuleExists() {
    return new CoreModuleExists(RULE_LOADER)::validate;
  }

  /**
   * Rule: Facade module is recommended for complex components.
   *
   * <p>Suggests creating a facade module ({base}-facade) for components with multiple submodules.
   * The facade provides a simplified, unified API for external consumers.
   *
   * <p>This rule generates informational violations, not errors.
   *
   * @return the facade module exists rule (SEA-103)
   */
  public static StructureRule facadeModuleExists() {
    return new FacadeModuleExists(RULE_LOADER)::validate;
  }

  /**
   * Rule: SPI module is recommended when extensibility is needed.
   *
   * <p>Warns when an SPI module ({base}-spi) might be beneficial, such as when a component has
   * multiple implementations or uses interfaces extensively in its API.
   *
   * <p>This rule generates informational violations, not errors.
   *
   * @return the SPI module conditional rule (SEA-104)
   */
  public static StructureRule spiModuleConditional() {
    return new SpiModuleConditional(RULE_LOADER)::validate;
  }

  /**
   * Rule: Components must be complete.
   *
   * <p>Validates that each component has at least both API and core modules. Detects orphan modules
   * (e.g., -api without -core, or -core without -api).
   *
   * @return the component completeness rule (SEA-105)
   */
  public static StructureRule componentCompleteness() {
    return new ComponentCompleteness(RULE_LOADER)::validate;
  }

  /**
   * Rule: Common layer is prohibited.
   *
   * <p>Enforces the prohibition of {base}-common modules as per SEA-4 architectural decisions. The
   * common layer pattern has been deprecated in favor of:
   *
   * <ul>
   *   <li>Placing shared contracts and DTOs in the API module
   *   <li>Placing shared implementation utilities in the core module
   *   <li>Using dedicated utility libraries for cross-cutting concerns
   * </ul>
   *
   * @return the no common layer rule (SEA-4)
   */
  public static StructureRule noCommonLayer() {
    return new NoCommonLayer(RULE_LOADER)::validate;
  }

  /**
   * Rule: Utility modules are prohibited.
   *
   * <p>Prohibits {base}-util and {base}-utils modules. Utility modules are anti-patterns that
   * create unclear boundaries and tend to become dumping grounds. Developers should instead:
   *
   * <ul>
   *   <li>Place component-specific utilities in the core module
   *   <li>Create focused modules with clear names (e.g., {base}-validation)
   *   <li>Use existing utility libraries (Apache Commons, Guava, etc.)
   *   <li>Extract truly shared utilities into separate library projects
   * </ul>
   *
   * @return the no utility modules rule (SEA-106)
   */
  public static StructureRule noUtilModules() {
    return new NoUtilModules(RULE_LOADER)::validate;
  }

  /**
   * Returns all SEA-4 structure rules.
   *
   * <p>This includes all mandatory and recommended rules for stratified architecture:
   *
   * <ul>
   *   <li>API module exists (SEA-101)
   *   <li>Core module exists (SEA-102)
   *   <li>Facade module exists (SEA-103) - informational
   *   <li>SPI module conditional (SEA-104) - informational
   *   <li>Component completeness (SEA-105)
   *   <li>No common layer (SEA-4)
   *   <li>No utility modules (SEA-106)
   * </ul>
   *
   * @return list of all SEA-4 rules
   */
  public static List<StructureRule> sea4() {
    return List.of(
        apiModuleExists(),
        coreModuleExists(),
        facadeModuleExists(),
        spiModuleConditional(),
        componentCompleteness(),
        noCommonLayer(),
        noUtilModules());
  }

  /**
   * Checks all SEA-4 rules against the given project root.
   *
   * <p>This method aggregates violations from all rules without throwing exceptions. It is useful
   * for reporting all violations at once or for custom handling of violations.
   *
   * <p>Example:
   *
   * <pre>{@code
   * List<StructureViolation> violations = StructureRules.check(Path.of("."));
   * if (!violations.isEmpty()) {
   *     System.err.println("Found " + violations.size() + " violations:");
   *     violations.forEach(System.err::println);
   * }
   * }</pre>
   *
   * @param projectRoot the project root directory to validate
   * @return list of all violations found (empty if all rules pass)
   */
  public static List<StructureViolation> check(Path projectRoot) {
    List<StructureViolation> allViolations = new ArrayList<>();

    for (StructureRule rule : sea4()) {
      allViolations.addAll(rule.check(projectRoot));
    }

    return allViolations;
  }

  /**
   * Enforces all SEA-4 rules against the given project root.
   *
   * <p>This method checks all rules and throws an exception if any violations are found. It is
   * useful in test scenarios where violations should cause test failures.
   *
   * <p>Example:
   *
   * <pre>{@code
   * // In a JUnit test
   * {@literal @}Test
   * void projectStructureShouldConformToSea4() {
   *     StructureRules.enforce(Path.of("."));
   * }
   * }</pre>
   *
   * @param projectRoot the project root directory to validate
   * @throws AssertionError if any violations are found
   */
  public static void enforce(Path projectRoot) {
    List<StructureViolation> violations = check(projectRoot);

    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder("Structure violations found:\n");
      for (StructureViolation v : violations) {
        sb.append("  - ").append(v).append("\n");
      }
      throw new AssertionError(sb.toString());
    }
  }
}
