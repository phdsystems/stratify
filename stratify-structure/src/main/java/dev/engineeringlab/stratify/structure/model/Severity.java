package dev.engineeringlab.stratify.structure.model;

/**
 * Represents the severity level of a validation violation.
 *
 * <p>Severity levels are used to classify violations by their importance:
 *
 * <ul>
 *   <li>{@link #ERROR} - Critical violations that must be fixed
 *   <li>{@link #WARNING} - Important issues that should be addressed
 *   <li>{@link #INFO} - Informational messages or suggestions
 * </ul>
 */
public enum Severity {
  /**
   * Critical violations that must be fixed. Indicates a violation of mandatory rules that will
   * cause build failures or structural integrity issues.
   */
  ERROR,

  /**
   * Important issues that should be addressed. Indicates violations that may not break the build
   * but represent deviations from best practices or recommendations.
   */
  WARNING,

  /**
   * Informational messages or suggestions. Provides guidance or suggestions for improvements
   * without requiring immediate action.
   */
  INFO
}
