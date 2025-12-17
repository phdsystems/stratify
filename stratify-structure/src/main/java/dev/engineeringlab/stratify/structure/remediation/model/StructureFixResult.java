package dev.engineeringlab.stratify.structure.remediation.model;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
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
public record StructureFixResult(
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
  public static StructureFixResult success(
      StructureViolation violation, List<Path> modifiedFiles, String description) {
    return new StructureFixResult(
        violation, FixStatus.FIXED, description, modifiedFiles, List.of(), null);
  }

  /** Creates a successful fix result with diffs. */
  public static StructureFixResult success(
      StructureViolation violation,
      List<Path> modifiedFiles,
      String description,
      List<String> diffs) {
    return new StructureFixResult(
        violation, FixStatus.FIXED, description, modifiedFiles, diffs, null);
  }

  /** Creates a failed fix result. */
  public static StructureFixResult failed(StructureViolation violation, String errorMessage) {
    return new StructureFixResult(
        violation, FixStatus.FAILED, "Fix failed", List.of(), List.of(), errorMessage);
  }

  /** Creates a skipped fix result. */
  public static StructureFixResult skipped(StructureViolation violation, String reason) {
    return new StructureFixResult(violation, FixStatus.SKIPPED, reason, List.of(), List.of(), null);
  }

  /** Creates a not-fixable result. */
  public static StructureFixResult notFixable(StructureViolation violation, String reason) {
    return new StructureFixResult(
        violation, FixStatus.NOT_FIXABLE, reason, List.of(), List.of(), null);
  }

  /** Creates a dry-run result showing what would be changed. */
  public static StructureFixResult dryRun(
      StructureViolation violation,
      List<Path> wouldModify,
      String description,
      List<String> diffs) {
    return new StructureFixResult(
        violation, FixStatus.DRY_RUN, description, wouldModify, diffs, null);
  }

  /** Creates a parse error result. */
  public static StructureFixResult parseError(StructureViolation violation, String errorMessage) {
    return new StructureFixResult(
        violation,
        FixStatus.PARSE_ERROR,
        "Failed to parse file",
        List.of(),
        List.of(),
        errorMessage);
  }

  /** Creates a validation failed result. */
  public static StructureFixResult validationFailed(
      StructureViolation violation, String errorMessage) {
    return new StructureFixResult(
        violation,
        FixStatus.VALIDATION_FAILED,
        "Validation failed after fix",
        List.of(),
        List.of(),
        errorMessage);
  }

  /** Creates a builder for constructing StructureFixResult instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builder for StructureFixResult. */
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

    public StructureFixResult build() {
      return new StructureFixResult(
          violation, status, description, modifiedFiles, diffs, errorMessage);
    }
  }
}
