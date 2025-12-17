package dev.engineeringlab.stratify.structure.model;

/**
 * Represents a validation violation discovered during structural analysis.
 *
 * <p>A violation captures all relevant information about a rule violation, including the rule that
 * was violated, the target element, severity, category, and optional fix information.
 *
 * @param ruleId the unique identifier of the rule that was violated
 * @param ruleName the human-readable name of the rule
 * @param target the element that violated the rule (module, package, class, etc.)
 * @param message a descriptive message explaining the violation
 * @param severity the severity level of this violation
 * @param category the category of this violation
 * @param fix an optional suggestion for how to fix the violation (may be null)
 * @param location the location of the violation (file path, line number, etc.)
 */
public record Violation(
    String ruleId,
    String ruleName,
    String target,
    String message,
    Severity severity,
    Category category,
    String fix,
    String location) {
  /** Creates a violation with compact constructor validation. */
  public Violation {
    if (ruleId == null || ruleId.isBlank()) {
      throw new IllegalArgumentException("ruleId cannot be null or blank");
    }
    if (ruleName == null || ruleName.isBlank()) {
      throw new IllegalArgumentException("ruleName cannot be null or blank");
    }
    if (target == null || target.isBlank()) {
      throw new IllegalArgumentException("target cannot be null or blank");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message cannot be null or blank");
    }
    if (severity == null) {
      throw new IllegalArgumentException("severity cannot be null");
    }
    if (category == null) {
      throw new IllegalArgumentException("category cannot be null");
    }
    if (location == null || location.isBlank()) {
      throw new IllegalArgumentException("location cannot be null or blank");
    }
  }

  /**
   * Creates a structure violation (ERROR severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param location the violation location
   * @return a new structure violation
   */
  public static Violation structureError(
      String ruleId, String ruleName, String target, String message, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.ERROR, Category.STRUCTURE, null, location);
  }

  /**
   * Creates a structure violation with a suggested fix (ERROR severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param fix the suggested fix
   * @param location the violation location
   * @return a new structure violation with fix
   */
  public static Violation structureError(
      String ruleId, String ruleName, String target, String message, String fix, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.ERROR, Category.STRUCTURE, fix, location);
  }

  /**
   * Creates a dependency violation (ERROR severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param location the violation location
   * @return a new dependency violation
   */
  public static Violation dependencyError(
      String ruleId, String ruleName, String target, String message, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.ERROR, Category.DEPENDENCIES, null, location);
  }

  /**
   * Creates a dependency violation with a suggested fix (ERROR severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param fix the suggested fix
   * @param location the violation location
   * @return a new dependency violation with fix
   */
  public static Violation dependencyError(
      String ruleId, String ruleName, String target, String message, String fix, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.ERROR, Category.DEPENDENCIES, fix, location);
  }

  /**
   * Creates a naming violation (WARNING severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param location the violation location
   * @return a new naming violation
   */
  public static Violation namingWarning(
      String ruleId, String ruleName, String target, String message, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.WARNING, Category.NAMING, null, location);
  }

  /**
   * Creates a naming violation with a suggested fix (WARNING severity).
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param fix the suggested fix
   * @param location the violation location
   * @return a new naming violation with fix
   */
  public static Violation namingWarning(
      String ruleId, String ruleName, String target, String message, String fix, String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.WARNING, Category.NAMING, fix, location);
  }

  /**
   * Creates an informational violation.
   *
   * @param ruleId the rule identifier
   * @param ruleName the rule name
   * @param target the target element
   * @param message the violation message
   * @param category the violation category
   * @param location the violation location
   * @return a new informational violation
   */
  public static Violation info(
      String ruleId,
      String ruleName,
      String target,
      String message,
      Category category,
      String location) {
    return new Violation(
        ruleId, ruleName, target, message, Severity.INFO, category, null, location);
  }

  /**
   * Returns whether this violation has a suggested fix.
   *
   * @return true if a fix is available, false otherwise
   */
  public boolean hasFix() {
    return fix != null && !fix.isBlank();
  }

  /**
   * Returns whether this violation is an error.
   *
   * @return true if severity is ERROR, false otherwise
   */
  public boolean isError() {
    return severity == Severity.ERROR;
  }

  /**
   * Returns whether this violation is a warning.
   *
   * @return true if severity is WARNING, false otherwise
   */
  public boolean isWarning() {
    return severity == Severity.WARNING;
  }

  /**
   * Returns whether this violation is informational.
   *
   * @return true if severity is INFO, false otherwise
   */
  public boolean isInfo() {
    return severity == Severity.INFO;
  }
}
