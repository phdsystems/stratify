package dev.engineeringlab.stratify.structure.scanner.common.model;

import dev.engineeringlab.stratify.structure.scanner.common.config.ComplianceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

/** Aggregated results of compliance validation. */
@Data
public class ComplianceResult {
  private static final int FULL_COMPLIANCE_SCORE = 100;

  /** List of all validation violations found. */
  private final List<Violation> violations = new ArrayList<>();

  /** Execution time in milliseconds. */
  private long executionTimeMs;

  /**
   * Adds a single violation.
   *
   * @param violation the violation to add
   */
  public void add(final Violation violation) {
    violations.add(violation);
  }

  /**
   * Adds multiple violations.
   *
   * @param newViolations the violations to add
   */
  public void addAll(final List<Violation> newViolations) {
    this.violations.addAll(newViolations);
  }

  /**
   * Filters out disabled rules from violations.
   *
   * @param disabledRules set of rule IDs to filter out
   */
  public void filterDisabledRules(final Set<String> disabledRules) {
    if (disabledRules == null || disabledRules.isEmpty()) {
      return;
    }
    violations.removeIf(v -> disabledRules.contains(v.getRuleId()));
  }

  /**
   * Applies severity overrides from configuration to all violations.
   *
   * <p>This method should be called after all validators have run and before reporting results. It
   * allows the severity of specific rules to be overridden via configuration (e.g.,
   * rule.severity.SS-001=ERROR).
   *
   * @param config the compliance configuration containing severity overrides
   */
  public void applySeverityOverrides(final ComplianceConfig config) {
    if (config == null) {
      return;
    }
    Map<String, String> overrides = config.getSeverityOverrides();
    if (overrides == null || overrides.isEmpty()) {
      return;
    }

    for (Violation violation : violations) {
      String ruleId = violation.getRuleId();
      if (ruleId != null && overrides.containsKey(ruleId)) {
        Severity newSeverity = config.resolveSeverity(ruleId, violation.getSeverity());
        violation.setSeverity(newSeverity);
      }
    }
  }

  /**
   * Gets all violations of ERROR severity.
   *
   * @return list of error violations
   */
  public List<Violation> getErrors() {
    return violations.stream()
        .filter(v -> v.getSeverity() == Severity.ERROR && !v.isPassed())
        .collect(Collectors.toList());
  }

  /**
   * Gets all violations of WARNING severity.
   *
   * @return list of warning violations
   */
  public List<Violation> getWarnings() {
    return violations.stream()
        .filter(v -> v.getSeverity() == Severity.WARNING && !v.isPassed())
        .collect(Collectors.toList());
  }

  /**
   * Gets all passed validations.
   *
   * @return list of passed validations
   */
  public List<Violation> getPassed() {
    return violations.stream().filter(Violation::isPassed).collect(Collectors.toList());
  }

  /**
   * Checks if there are any ERROR violations.
   *
   * @return true if errors exist
   */
  public boolean hasErrors() {
    return !getErrors().isEmpty();
  }

  /**
   * Checks if there are any WARNING violations.
   *
   * @return true if warnings exist
   */
  public boolean hasWarnings() {
    return !getWarnings().isEmpty();
  }

  /**
   * Gets count of ERROR violations.
   *
   * @return number of errors
   */
  public int getErrorCount() {
    return getErrors().size();
  }

  /**
   * Gets count of WARNING violations.
   *
   * @return number of warnings
   */
  public int getWarningCount() {
    return getWarnings().size();
  }

  /**
   * Gets count of passed validations.
   *
   * @return number of passed validations
   */
  public int getPassedCount() {
    return getPassed().size();
  }

  /**
   * Gets total count of all validations.
   *
   * @return total number of validations
   */
  public int getTotalCount() {
    return violations.size();
  }

  /**
   * Calculates compliance score as percentage.
   *
   * @return compliance score (0-100)
   */
  public int getComplianceScore() {
    if (violations.isEmpty()) {
      return FULL_COMPLIANCE_SCORE;
    }
    return (int) ((double) getPassedCount() / getTotalCount() * FULL_COMPLIANCE_SCORE);
  }

  /**
   * Checks if module is compliant (no errors, optionally no warnings).
   *
   * @param failOnWarning whether to treat warnings as failures
   * @return true if compliant
   */
  public boolean isCompliant(final boolean failOnWarning) {
    if (hasErrors()) {
      return false;
    }
    if (failOnWarning && hasWarnings()) {
      return false;
    }
    return true;
  }
}
