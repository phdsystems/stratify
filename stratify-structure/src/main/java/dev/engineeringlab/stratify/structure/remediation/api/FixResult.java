package dev.engineeringlab.stratify.structure.remediation.api;

import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Result of a fix attempt for a structure violation.
 *
 * @param violation the original violation that was processed
 * @param status the status of the fix attempt
 * @param description human-readable description of the result
 * @param modifiedFiles list of files that were modified
 * @param diffs unified diffs showing the changes
 * @param errorMessage error message if the fix failed
 */
public record FixResult(
    StructureViolation violation,
    FixStatus status,
    String description,
    List<Path> modifiedFiles,
    List<String> diffs,
    String errorMessage) {

  /** Returns true if the fix was successful. */
  public boolean isSuccess() {
    return status == FixStatus.FIXED;
  }

  /** Returns true if this is a dry-run result. */
  public boolean isDryRun() {
    return status == FixStatus.DRY_RUN;
  }

  /** Returns true if the fix failed. */
  public boolean isFailed() {
    return status == FixStatus.FAILED
        || status == FixStatus.PARSE_ERROR
        || status == FixStatus.VALIDATION_FAILED;
  }

  /** Returns the error message if present. */
  public Optional<String> getError() {
    return Optional.ofNullable(errorMessage);
  }

  // Factory methods

  /** Creates a successful fix result. */
  public static FixResult success(
      StructureViolation violation, List<Path> modifiedFiles, String description) {
    return new FixResult(violation, FixStatus.FIXED, description, modifiedFiles, List.of(), null);
  }

  /** Creates a successful fix result with diffs. */
  public static FixResult success(
      StructureViolation violation,
      List<Path> modifiedFiles,
      String description,
      List<String> diffs) {
    return new FixResult(violation, FixStatus.FIXED, description, modifiedFiles, diffs, null);
  }

  /** Creates a failed fix result. */
  public static FixResult failed(StructureViolation violation, String errorMessage) {
    return new FixResult(
        violation, FixStatus.FAILED, "Fix failed", List.of(), List.of(), errorMessage);
  }

  /** Creates a skipped fix result. */
  public static FixResult skipped(StructureViolation violation, String reason) {
    return new FixResult(violation, FixStatus.SKIPPED, reason, List.of(), List.of(), null);
  }

  /** Creates a not-fixable result. */
  public static FixResult notFixable(StructureViolation violation, String reason) {
    return new FixResult(violation, FixStatus.NOT_FIXABLE, reason, List.of(), List.of(), null);
  }

  /** Creates a dry-run result showing what would be changed. */
  public static FixResult dryRun(
      StructureViolation violation,
      List<Path> wouldModify,
      String description,
      List<String> diffs) {
    return new FixResult(violation, FixStatus.DRY_RUN, description, wouldModify, diffs, null);
  }

  /** Creates a parse error result. */
  public static FixResult parseError(StructureViolation violation, String errorMessage) {
    return new FixResult(
        violation,
        FixStatus.PARSE_ERROR,
        "Failed to parse file",
        List.of(),
        List.of(),
        errorMessage);
  }

  /** Creates a validation failed result. */
  public static FixResult validationFailed(StructureViolation violation, String errorMessage) {
    return new FixResult(
        violation,
        FixStatus.VALIDATION_FAILED,
        "Validation failed after fix",
        List.of(),
        List.of(),
        errorMessage);
  }

  /** Creates a builder for constructing FixResult instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for FixResult. */
  public static class Builder {
    private StructureViolation violation;
    private FixStatus status;
    private String description;
    private List<Path> modifiedFiles = List.of();
    private List<String> diffs = List.of();
    private String errorMessage;

    public Builder violation(StructureViolation violation) {
      this.violation = violation;
      return this;
    }

    public Builder status(FixStatus status) {
      this.status = status;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder modifiedFiles(List<Path> modifiedFiles) {
      this.modifiedFiles = modifiedFiles;
      return this;
    }

    public Builder diffs(List<String> diffs) {
      this.diffs = diffs;
      return this;
    }

    public Builder errorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
      return this;
    }

    public FixResult build() {
      return new FixResult(violation, status, description, modifiedFiles, diffs, errorMessage);
    }
  }
}
