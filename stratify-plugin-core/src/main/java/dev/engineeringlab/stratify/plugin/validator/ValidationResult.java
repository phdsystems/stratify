package dev.engineeringlab.stratify.plugin.validator;

/**
 * Represents a single validation result from SEA compliance checking.
 *
 * @param ruleId unique identifier for the validation rule (e.g., "MS-001", "NC-002")
 * @param severity severity level of the violation
 * @param message human-readable description of the issue
 * @param location optional location information (module name, file path, etc.)
 */
public record ValidationResult(String ruleId, Severity severity, String message, String location) {
  public ValidationResult {
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("ruleId cannot be null or blank");
    }
    if (severity == null) {
      throw new IllegalArgumentException("severity cannot be null");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message cannot be null or blank");
    }
  }

  /** Creates a validation result with no location. */
  public static ValidationResult of(String ruleId, Severity severity, String message) {
    return new ValidationResult(ruleId, severity, message, null);
  }

  /** Creates an ERROR validation result. */
  public static ValidationResult error(String ruleId, String message, String location) {
    return new ValidationResult(ruleId, Severity.ERROR, message, location);
  }

  /** Creates a WARNING validation result. */
  public static ValidationResult warning(String ruleId, String message, String location) {
    return new ValidationResult(ruleId, Severity.WARNING, message, location);
  }

  /** Creates an INFO validation result. */
  public static ValidationResult info(String ruleId, String message, String location) {
    return new ValidationResult(ruleId, Severity.INFO, message, location);
  }

  /** Severity levels for validation results. */
  public enum Severity {
    ERROR,
    WARNING,
    INFO
  }
}
