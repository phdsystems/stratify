package dev.engineeringlab.stratify.structure.remediation.api;

import java.nio.file.Path;

/**
 * Represents a structure compliance violation that can potentially be auto-fixed.
 *
 * @param ruleId the rule identifier (e.g., "SEA-4-001", "SEA-4-002")
 * @param ruleCategory the category of the rule (e.g., "ModuleStructure", "LayerCompliance")
 * @param message human-readable description of the violation
 * @param location file or directory path where the violation was found
 * @param expected what SEA expects to find
 * @param found what was actually found (or "Not found")
 * @param suggestedFix the suggested fix
 * @param reference documentation reference (e.g., "compliance-checklist.md § 1.1")
 */
public record StructureViolation(
    String ruleId,
    String ruleCategory,
    String message,
    Path location,
    String expected,
    String found,
    String suggestedFix,
    String reference) {

  /** Creates a builder for constructing StructureViolation instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns the severity based on the rule ID prefix. */
  public ViolationSeverity severity() {
    if (ruleId == null) {
      return ViolationSeverity.WARNING;
    }
    return ViolationSeverity.WARNING;
  }

  /** Returns true if this violation has a suggested fix. */
  public boolean hasFixSuggestion() {
    return suggestedFix != null && !suggestedFix.isBlank();
  }

  /** Returns the rule category extracted from the rule ID. */
  public String extractCategory() {
    if (ruleId == null || ruleId.length() < 5) {
      return "UNKNOWN";
    }
    // SEA-4-XXX format
    if (ruleId.startsWith("SEA-4")) {
      return "StructureCompliance";
    }
    return switch (ruleId.substring(0, 2)) {
      case "MS" -> "ModuleStructure";
      case "EI" -> "ExceptionInfra";
      case "DP" -> "Dependency";
      case "AP" -> "ApiModule";
      case "MA" -> "ModuleAnnotation";
      case "PO" -> "PackageOrganization";
      case "CQ" -> "CodeQuality";
      case "TC" -> "TestCompliance";
      case "PT" -> "PatternCompliance";
      default -> "OTHER";
    };
  }

  /** Builder for StructureViolation. */
  public static class Builder {
    private String ruleId;
    private String ruleCategory;
    private String message;
    private Path location;
    private String expected;
    private String found;
    private String suggestedFix;
    private String reference;

    public Builder ruleId(String ruleId) {
      this.ruleId = ruleId;
      return this;
    }

    public Builder ruleCategory(String ruleCategory) {
      this.ruleCategory = ruleCategory;
      return this;
    }

    public Builder message(String message) {
      this.message = message;
      return this;
    }

    public Builder location(Path location) {
      this.location = location;
      return this;
    }

    public Builder expected(String expected) {
      this.expected = expected;
      return this;
    }

    public Builder found(String found) {
      this.found = found;
      return this;
    }

    public Builder suggestedFix(String suggestedFix) {
      this.suggestedFix = suggestedFix;
      return this;
    }

    public Builder reference(String reference) {
      this.reference = reference;
      return this;
    }

    public StructureViolation build() {
      return new StructureViolation(
          ruleId,
          ruleCategory != null ? ruleCategory : extractCategoryFromRuleId(ruleId),
          message,
          location,
          expected,
          found,
          suggestedFix,
          reference);
    }

    private String extractCategoryFromRuleId(String ruleId) {
      if (ruleId == null || ruleId.length() < 2) {
        return "UNKNOWN";
      }
      if (ruleId.startsWith("SEA-4")) {
        return "StructureCompliance";
      }
      return switch (ruleId.substring(0, 2)) {
        case "MS" -> "ModuleStructure";
        case "EI" -> "ExceptionInfra";
        case "DP" -> "Dependency";
        case "AP" -> "ApiModule";
        case "MA" -> "ModuleAnnotation";
        case "PO" -> "PackageOrganization";
        case "CQ" -> "CodeQuality";
        case "TC" -> "TestCompliance";
        case "PT" -> "PatternCompliance";
        default -> "OTHER";
      };
    }
  }
}
