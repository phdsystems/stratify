package dev.engineeringlab.stratify.structure.remediation.api;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Interface for structure violation fixers.
 *
 * <p>Implementations of this interface provide automated fixes for specific types of structure
 * violations. Each fixer declares which rules it can handle and provides methods to fix violations
 * individually or in batch.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class NoCommonLayerFixer implements Fixer {
 *     @Override
 *     public String getName() { return "NoCommonLayerFixer"; }
 *
 *     @Override
 *     public String[] getAllSupportedRules() { return new String[]{"SEA-4-001"}; }
 *
 *     @Override
 *     public boolean canFix(StructureViolation violation) {
 *         return "SEA-4-001".equals(violation.ruleId());
 *     }
 *
 *     @Override
 *     public FixResult fix(StructureViolation violation, FixerContext context) {
 *         // Implementation
 *     }
 * }
 * }</pre>
 */
public interface Fixer {

  /**
   * Returns the name of this fixer.
   *
   * @return fixer name
   */
  String getName();

  /**
   * Returns the description of what this fixer does.
   *
   * @return fixer description
   */
  String getDescription();

  /**
   * Returns the array of rule IDs this fixer can handle.
   *
   * <p>Example: {"SEA-4-001", "SEA-4-002", "SEA-4-003"}
   *
   * @return array of supported rule IDs
   */
  String[] getAllSupportedRules();

  /**
   * Returns the priority of this fixer. Higher priority fixers run first. Default is 50.
   *
   * @return priority value
   */
  default int getPriority() {
    return 50;
  }

  /**
   * Returns true if this fixer is enabled. Default is true.
   *
   * @return true if enabled
   */
  default boolean isEnabled() {
    return true;
  }

  /**
   * Returns true if this fixer can fix the given violation.
   *
   * @param violation the violation to check
   * @return true if this fixer can handle the violation
   */
  boolean canFix(StructureViolation violation);

  /**
   * Attempts to fix the given violation.
   *
   * @param violation the violation to fix
   * @param context the execution context
   * @return the result of the fix attempt
   */
  FixResult fix(StructureViolation violation, FixerContext context);

  /**
   * Attempts to fix all given violations.
   *
   * <p>Default implementation processes violations sequentially. Implementations may override for
   * parallel processing.
   *
   * @param violations the violations to fix
   * @param context the execution context
   * @return list of fix results
   */
  default List<FixResult> fixAll(List<StructureViolation> violations, FixerContext context) {
    return violations.stream().filter(this::canFix).map(v -> fix(v, context)).toList();
  }

  /**
   * Attempts to fix all given violations using the provided executor for parallelism.
   *
   * @param violations the violations to fix
   * @param context the execution context
   * @param executor the executor for parallel processing
   * @return list of fix results
   */
  default List<FixResult> fixAll(
      List<StructureViolation> violations, FixerContext context, Executor executor) {
    // Default: ignore executor and process sequentially
    return fixAll(violations, context);
  }
}
