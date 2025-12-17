package dev.engineeringlab.stratify.structure.scanner.common.fix;

import dev.engineeringlab.stratify.structure.scanner.common.model.FixResult;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import dev.engineeringlab.stratify.structure.scanner.common.spi.Fixer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Registry for managing and discovering fixers.
 *
 * <p>The registry can discover fixers via ServiceLoader (SPI) or have fixers registered
 * programmatically.
 *
 * @since 0.2.0
 */
public class FixerRegistry {

  private final List<Fixer> fixers = new ArrayList<>();
  private final Map<String, List<Fixer>> fixersByRuleId = new HashMap<>();

  /** Creates a new empty registry. */
  public FixerRegistry() {}

  /**
   * Creates a registry and loads all fixers via ServiceLoader.
   *
   * @return a registry with all discovered fixers
   */
  public static FixerRegistry loadFromServiceLoader() {
    FixerRegistry registry = new FixerRegistry();
    ServiceLoader<Fixer> loader = ServiceLoader.load(Fixer.class);
    for (Fixer fixer : loader) {
      registry.register(fixer);
    }
    return registry;
  }

  /**
   * Registers a fixer with this registry.
   *
   * @param fixer the fixer to register
   */
  public void register(Fixer fixer) {
    if (fixer == null) {
      return;
    }
    fixers.add(fixer);
    for (String ruleId : fixer.getSupportedRuleIds()) {
      fixersByRuleId.computeIfAbsent(ruleId, k -> new ArrayList<>()).add(fixer);
    }
  }

  /**
   * Gets all registered fixers.
   *
   * @return list of all fixers, sorted by priority
   */
  public List<Fixer> getAllFixers() {
    return fixers.stream().sorted(Comparator.comparingInt(Fixer::getPriority)).toList();
  }

  /**
   * Gets fixers that can handle a specific rule ID.
   *
   * @param ruleId the rule ID to find fixers for
   * @return list of fixers that can handle the rule, sorted by priority
   */
  public List<Fixer> getFixersForRule(String ruleId) {
    return fixersByRuleId.getOrDefault(ruleId, List.of()).stream()
        .sorted(Comparator.comparingInt(Fixer::getPriority))
        .toList();
  }

  /**
   * Finds a fixer that can fix the given violation.
   *
   * @param violation the violation to find a fixer for
   * @return the first fixer that can handle the violation, or empty if none found
   */
  public Optional<Fixer> findFixerFor(Violation violation) {
    if (violation == null || violation.getRuleId() == null) {
      return Optional.empty();
    }
    return getFixersForRule(violation.getRuleId()).stream()
        .filter(f -> f.canFix(violation))
        .findFirst();
  }

  /**
   * Checks if there is a fixer available for the given violation.
   *
   * @param violation the violation to check
   * @return true if a fixer is available
   */
  public boolean hasFixerFor(Violation violation) {
    return findFixerFor(violation).isPresent();
  }

  /**
   * Previews a fix for the given violation.
   *
   * @param violation the violation to preview a fix for
   * @param basePath the base path of the project
   * @return the preview result, or a failure if no fixer is available
   */
  public FixResult preview(Violation violation, Path basePath) {
    return findFixerFor(violation)
        .map(f -> f.preview(violation, basePath))
        .orElse(
            FixResult.notSupported(
                violation, "No fixer available for rule " + violation.getRuleId()));
  }

  /**
   * Applies a fix for the given violation.
   *
   * @param violation the violation to fix
   * @param basePath the base path of the project
   * @return the fix result, or a failure if no fixer is available
   */
  public FixResult fix(Violation violation, Path basePath) {
    return findFixerFor(violation)
        .map(f -> f.fix(violation, basePath))
        .orElse(
            FixResult.notSupported(
                violation, "No fixer available for rule " + violation.getRuleId()));
  }

  /**
   * Applies fixes for all violations.
   *
   * @param violations the violations to fix
   * @param basePath the base path of the project
   * @return list of fix results
   */
  public List<FixResult> fixAll(List<Violation> violations, Path basePath) {
    return violations.stream().map(v -> fix(v, basePath)).toList();
  }

  /**
   * Applies fixes only for violations that are safe to auto-apply.
   *
   * @param violations the violations to fix
   * @param basePath the base path of the project
   * @return list of fix results (only for safe fixes)
   */
  public List<FixResult> fixSafe(List<Violation> violations, Path basePath) {
    List<FixResult> results = new ArrayList<>();
    for (Violation violation : violations) {
      Optional<Fixer> fixer = findFixerFor(violation);
      if (fixer.isPresent() && fixer.get().isSafeToAutoApply(violation)) {
        results.add(fixer.get().fix(violation, basePath));
      }
    }
    return results;
  }

  /**
   * Previews fixes for all violations.
   *
   * @param violations the violations to preview fixes for
   * @param basePath the base path of the project
   * @return list of preview results
   */
  public List<FixResult> previewAll(List<Violation> violations, Path basePath) {
    return violations.stream().map(v -> preview(v, basePath)).toList();
  }

  /**
   * Gets rule IDs that have fixers available.
   *
   * @return list of rule IDs with available fixers
   */
  public List<String> getSupportedRuleIds() {
    return new ArrayList<>(fixersByRuleId.keySet());
  }

  /**
   * Gets the number of registered fixers.
   *
   * @return the number of fixers
   */
  public int size() {
    return fixers.size();
  }
}
