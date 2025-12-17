package dev.engineeringlab.stratify.structure.scanner.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Represents a compliance violation found during scanning. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Violation {
  /** Unique rule identifier (e.g., "MS-001", "NC-001"). */
  private String ruleId;

  /** Severity level of the violation. */
  private Severity severity;

  /** Category of the violation. (e.g., "Module Structure", "Naming Conventions") */
  private ViolationCategory category;

  /** Human-readable description of the violation. */
  private String description;

  /** File or location where the violation was found. */
  private String location;

  /** Expected value or pattern. */
  private String expected;

  /** Actual value found. */
  private String found;

  /** Reason or rationale for the rule. */
  private String reason;

  /** How to fix the violation. */
  private String fix;

  /** Reference to documentation (e.g., "compliance-checklist.md § 4.2.3"). */
  private String reference;

  /** Whether this violation passed (no violation found). */
  @Builder.Default private boolean passed = false;

  /**
   * Creates a passing validation result.
   *
   * @param ruleId the rule identifier
   * @param category the validation category
   * @param description the validation description
   * @return a passing violation result
   */
  public static Violation passed(
      final String ruleId, final ViolationCategory category, final String description) {
    return Violation.builder()
        .ruleId(ruleId)
        .category(category)
        .description(description)
        .severity(Severity.INFO)
        .passed(true)
        .build();
  }

  /**
   * Creates an error violation.
   *
   * @param ruleId the rule identifier
   * @param category the violation category
   * @param description the violation description
   * @param location the location of the violation
   * @return an error violation result
   */
  public static Violation error(
      final String ruleId,
      final ViolationCategory category,
      final String description,
      final String location) {
    return Violation.builder()
        .ruleId(ruleId)
        .category(category)
        .description(description)
        .location(location)
        .severity(Severity.ERROR)
        .passed(false)
        .build();
  }

  /**
   * Creates a warning violation.
   *
   * @param ruleId the rule identifier
   * @param category the violation category
   * @param description the violation description
   * @param location the location of the violation
   * @return a warning violation result
   */
  public static Violation warning(
      final String ruleId,
      final ViolationCategory category,
      final String description,
      final String location) {
    return Violation.builder()
        .ruleId(ruleId)
        .category(category)
        .description(description)
        .location(location)
        .severity(Severity.WARNING)
        .passed(false)
        .build();
  }
}
