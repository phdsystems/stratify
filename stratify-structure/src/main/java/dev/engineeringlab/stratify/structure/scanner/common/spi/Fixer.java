package dev.engineeringlab.stratify.structure.scanner.common.spi;

import dev.engineeringlab.stratify.structure.scanner.common.model.FixResult;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import java.nio.file.Path;

/**
 * Base interface for all fixers.
 *
 * <p>Fixers are responsible for automatically remedying violations found by validators. Each fixer
 * is associated with one or more rule IDs and can fix violations of those rules.
 *
 * <p>Fixers follow a preview-then-apply pattern:
 *
 * <ol>
 *   <li>{@link #preview(Violation, Path)} - Shows what changes would be made without applying them
 *   <li>{@link #fix(Violation, Path)} - Actually applies the fix
 * </ol>
 *
 * @since 0.2.0
 */
public interface Fixer {

  /**
   * Gets the rule IDs that this fixer can handle.
   *
   * @return array of rule IDs (e.g., "MS-011", "NC-001")
   */
  String[] getSupportedRuleIds();

  /**
   * Gets the name of this fixer.
   *
   * @return the fixer name
   */
  String getName();

  /**
   * Checks if this fixer can fix the given violation.
   *
   * @param violation the violation to check
   * @return true if this fixer can fix the violation
   */
  default boolean canFix(Violation violation) {
    if (violation == null || violation.getRuleId() == null) {
      return false;
    }
    for (String ruleId : getSupportedRuleIds()) {
      if (ruleId.equals(violation.getRuleId())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Previews the fix without applying it.
   *
   * <p>This method analyzes the violation and returns what changes would be made, but does not
   * modify any files.
   *
   * @param violation the violation to preview a fix for
   * @param basePath the base path of the project/module
   * @return the preview result describing what changes would be made
   */
  FixResult preview(Violation violation, Path basePath);

  /**
   * Applies the fix for the violation.
   *
   * <p>This method actually modifies files to fix the violation.
   *
   * @param violation the violation to fix
   * @param basePath the base path of the project/module
   * @return the result of the fix operation
   */
  FixResult fix(Violation violation, Path basePath);

  /**
   * Gets the priority of this fixer.
   *
   * <p>Lower values run first. Default is 100. Use lower priority for fixers that should run before
   * others.
   *
   * @return the priority
   */
  default int getPriority() {
    return 100;
  }

  /**
   * Checks if this fix is safe to apply automatically.
   *
   * <p>Safe fixes are non-destructive and can be applied without user confirmation. Unsafe fixes
   * may delete code, change behavior, or have other significant impacts.
   *
   * @param violation the violation to check
   * @return true if the fix is safe to apply automatically
   */
  default boolean isSafeToAutoApply(Violation violation) {
    return false;
  }
}
