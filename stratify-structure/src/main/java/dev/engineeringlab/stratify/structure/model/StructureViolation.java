package dev.engineeringlab.stratify.structure.model;

/**
 * Represents a structure rule violation with detailed metadata.
 *
 * <p>This is a structure-specific violation that includes additional context beyond the core
 * stratify-rules Violation class.
 */
public record StructureViolation(
    String ruleId,
    String ruleName,
    String target,
    String message,
    Severity severity,
    Category category,
    String location,
    String fix,
    String reference) {

  /** Creates a builder for constructing violations. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for StructureViolation. */
  public static class Builder {
    private String ruleId;
    private String ruleName;
    private String target;
    private String message;
    private Severity severity = Severity.ERROR;
    private Category category = Category.STRUCTURE;
    private String location;
    private String fix;
    private String reference;

    public Builder ruleId(String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    public Builder ruleName(String ruleName) {
      this.ruleName = ruleName;
      return this;
    }

    public Builder target(String target) {
      this.target = target;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder severity(Severity severity) {
      this.severity = severity;
      return this;
    }

    public Builder category(Category category) {
      this.category = category;
      return this;
    }

    public Builder location(String location) {
      this.location = location;
      return this;
    }

    public Builder fix(String fix) {
      this.fix = fix;
      return this;
    }

    public Builder reference(String reference) {
      this.reference = reference;
      return this;
    }

    public StructureViolation build() {
      return new StructureViolation(
          ruleId, ruleName, target, message, severity, category, location, fix, reference);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[").append(ruleId).append("] ");
    sb.append(ruleName).append(": ");
    sb.append(message);
    if (location != null && !location.isEmpty()) {
      sb.append(" (").append(location).append(")");
    }
    return sb.toString();
  }
}
