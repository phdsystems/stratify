package dev.engineeringlab.stratify.structure.remediation.api;

import java.util.List;
import java.util.Optional;

/**
 * Registry for structure fixers.
 *
 * <p>Manages registration and lookup of fixers by rule ID. Fixers are ordered by priority (higher
 * priority first).
 */
public interface FixerRegistry {

  /**
   * Registers a fixer with this registry.
   *
   * @param fixer the fixer to register
   */
  void register(Fixer fixer);

  /**
   * Registers multiple fixers with this registry.
   *
   * @param fixers the fixers to register
   */
  default void registerAll(Fixer... fixers) {
    for (Fixer fixer : fixers) {
      register(fixer);
    }
  }

  /**
   * Unregisters a fixer from this registry.
   *
   * @param fixer the fixer to unregister
   */
  void unregister(Fixer fixer);

  /**
   * Finds the first fixer that can handle the given violation. Returns the highest priority fixer
   * if multiple can handle it.
   *
   * @param violation the violation to find a fixer for
   * @return the fixer, or empty if none can handle the violation
   */
  Optional<Fixer> findFixerFor(StructureViolation violation);

  /**
   * Finds all fixers that can handle the given violation. Returns fixers sorted by priority
   * (highest first).
   *
   * @param violation the violation to find fixers for
   * @return list of fixers that can handle the violation
   */
  List<Fixer> findAllFixersFor(StructureViolation violation);

  /**
   * Finds fixers by their rule ID.
   *
   * @param ruleId the rule ID (e.g., "SEA-4-001")
   * @return list of fixers that support this rule
   */
  List<Fixer> findFixersForRule(String ruleId);

  /**
   * Returns all registered fixers.
   *
   * @return list of all fixers sorted by priority
   */
  List<Fixer> getAllFixers();

  /**
   * Returns all enabled fixers.
   *
   * @return list of enabled fixers sorted by priority
   */
  List<Fixer> getAllEnabledFixers();

  /**
   * Returns the number of registered fixers.
   *
   * @return count of registered fixers
   */
  int size();

  /** Clears all registered fixers. */
  void clear();
}
