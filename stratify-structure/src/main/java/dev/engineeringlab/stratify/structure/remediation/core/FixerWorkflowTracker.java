package dev.engineeringlab.stratify.structure.remediation.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Tracks fixer workflow method invocations to validate proper workflow compliance.
 *
 * <p>The expected workflow for a successful fix is:
 *
 * <ol>
 *   <li>BACKUP - backup files before modification
 *   <li>WRITE - modify files
 *   <li>CLEANUP - remove backups after successful fix
 * </ol>
 *
 * <p>The expected workflow for a failed fix is:
 *
 * <ol>
 *   <li>BACKUP - backup files before modification
 *   <li>WRITE - attempt to modify files
 *   <li>ROLLBACK - restore files from backup on failure
 * </ol>
 *
 * <p>This tracker is thread-local to support parallel fixer execution.
 */
public class FixerWorkflowTracker {

  /** Workflow operations that can be tracked. */
  public enum Operation {
    /** File backup operation (backup method called). */
    BACKUP,
    /** File restore operation (restore method called). */
    RESTORE,
    /** Backup deletion operation (deleteBackup method called). */
    DELETE_BACKUP,
    /** Staging cleanup operation (cleanupStaging method called). */
    CLEANUP_STAGING,
    /** Cleanup backups on success (cleanupBackupsOnSuccess method called). */
    CLEANUP_ON_SUCCESS,
    /** Rollback on failure (rollbackOnFailure method called). */
    ROLLBACK_ON_FAILURE,
    /** File write operation (writeFile method called). */
    WRITE_FILE,
    /** File read operation (readFile method called). */
    READ_FILE
  }

  /** Represents a single tracked operation. */
  public record TrackedOperation(Operation operation, Path file, long timestamp) {
    public TrackedOperation(Operation operation, Path file) {
      this(operation, file, System.nanoTime());
    }

    public TrackedOperation(Operation operation) {
      this(operation, null, System.nanoTime());
    }
  }

  /** Result of workflow validation. */
  public record ValidationResult(
      boolean isValid,
      List<String> violations,
      Set<Operation> missingOperations,
      List<TrackedOperation> operations) {
    public static ValidationResult valid(List<TrackedOperation> operations) {
      return new ValidationResult(true, List.of(), Set.of(), operations);
    }

    public static ValidationResult invalid(
        List<String> violations, Set<Operation> missing, List<TrackedOperation> operations) {
      return new ValidationResult(false, violations, missing, operations);
    }
  }

  private static final ThreadLocal<FixerWorkflowTracker> CURRENT = new ThreadLocal<>();

  private final List<TrackedOperation> operations = Collections.synchronizedList(new ArrayList<>());
  private final String fixerName;
  private boolean enabled = true;

  /**
   * Creates a new workflow tracker for the specified fixer.
   *
   * @param fixerName the name of the fixer being tracked
   */
  public FixerWorkflowTracker(String fixerName) {
    this.fixerName = fixerName;
  }

  /**
   * Gets the current thread-local tracker, if any.
   *
   * @return the current tracker or null if none is active
   */
  public static FixerWorkflowTracker current() {
    return CURRENT.get();
  }

  /**
   * Starts tracking for the current thread.
   *
   * @return this tracker for method chaining
   */
  public FixerWorkflowTracker start() {
    CURRENT.set(this);
    operations.clear();
    return this;
  }

  /** Stops tracking for the current thread. */
  public void stop() {
    CURRENT.remove();
  }

  /**
   * Records an operation.
   *
   * @param operation the operation type
   * @param file the file involved, or null if not applicable
   */
  public void record(Operation operation, Path file) {
    if (enabled) {
      operations.add(new TrackedOperation(operation, file));
    }
  }

  /**
   * Records an operation without a file.
   *
   * @param operation the operation type
   */
  public void record(Operation operation) {
    record(operation, null);
  }

  /**
   * Enables or disables tracking.
   *
   * @param enabled true to enable tracking
   */
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * Gets all recorded operations.
   *
   * @return unmodifiable list of operations
   */
  public List<TrackedOperation> getOperations() {
    return List.copyOf(operations);
  }

  /**
   * Checks if a specific operation was recorded.
   *
   * @param operation the operation to check
   * @return true if the operation was recorded
   */
  public boolean hasOperation(Operation operation) {
    return operations.stream().anyMatch(op -> op.operation() == operation);
  }

  /**
   * Checks if a specific operation was recorded for a file.
   *
   * @param operation the operation to check
   * @param file the file to check
   * @return true if the operation was recorded for the file
   */
  public boolean hasOperation(Operation operation, Path file) {
    return operations.stream()
        .anyMatch(op -> op.operation() == operation && (file == null || file.equals(op.file())));
  }

  /**
   * Gets the count of a specific operation.
   *
   * @param operation the operation to count
   * @return the number of times the operation was recorded
   */
  public int countOperation(Operation operation) {
    return (int) operations.stream().filter(op -> op.operation() == operation).count();
  }

  /**
   * Validates the workflow for a successful fix.
   *
   * <p>A valid successful fix workflow requires:
   *
   * <ul>
   *   <li>At least one BACKUP operation before any WRITE_FILE
   *   <li>CLEANUP_ON_SUCCESS called after the fix
   *   <li>No ROLLBACK_ON_FAILURE (since fix succeeded)
   * </ul>
   *
   * @return validation result
   */
  public ValidationResult validateSuccessWorkflow() {
    List<String> violations = new ArrayList<>();
    Set<Operation> missing = EnumSet.noneOf(Operation.class);

    // Check for backup before write
    boolean hasBackup = hasOperation(Operation.BACKUP);
    boolean hasWrite = hasOperation(Operation.WRITE_FILE);

    if (hasWrite && !hasBackup) {
      violations.add("WRITE_FILE called without prior BACKUP");
      missing.add(Operation.BACKUP);
    }

    // Check backup comes before write
    if (hasBackup && hasWrite) {
      long firstBackup =
          operations.stream()
              .filter(op -> op.operation() == Operation.BACKUP)
              .mapToLong(TrackedOperation::timestamp)
              .min()
              .orElse(Long.MAX_VALUE);
      long firstWrite =
          operations.stream()
              .filter(op -> op.operation() == Operation.WRITE_FILE)
              .mapToLong(TrackedOperation::timestamp)
              .min()
              .orElse(Long.MIN_VALUE);

      if (firstWrite < firstBackup) {
        violations.add("WRITE_FILE called before BACKUP");
      }
    }

    // Check for cleanup on success
    if (hasWrite && !hasOperation(Operation.CLEANUP_ON_SUCCESS)) {
      violations.add("CLEANUP_ON_SUCCESS not called after successful fix");
      missing.add(Operation.CLEANUP_ON_SUCCESS);
    }

    // Check no rollback on success path
    if (hasOperation(Operation.ROLLBACK_ON_FAILURE)) {
      violations.add("ROLLBACK_ON_FAILURE should not be called on success path");
    }

    if (violations.isEmpty()) {
      return ValidationResult.valid(getOperations());
    }
    return ValidationResult.invalid(violations, missing, getOperations());
  }

  /**
   * Validates the workflow for a failed fix.
   *
   * <p>A valid failed fix workflow requires:
   *
   * <ul>
   *   <li>BACKUP called before any WRITE_FILE
   *   <li>ROLLBACK_ON_FAILURE called after failure
   *   <li>No CLEANUP_ON_SUCCESS (since fix failed)
   * </ul>
   *
   * @return validation result
   */
  public ValidationResult validateFailureWorkflow() {
    List<String> violations = new ArrayList<>();
    Set<Operation> missing = EnumSet.noneOf(Operation.class);

    // Check for backup before write
    boolean hasBackup = hasOperation(Operation.BACKUP);
    boolean hasWrite = hasOperation(Operation.WRITE_FILE);

    if (hasWrite && !hasBackup) {
      violations.add("WRITE_FILE called without prior BACKUP");
      missing.add(Operation.BACKUP);
    }

    // Check for rollback on failure
    if (hasWrite && !hasOperation(Operation.ROLLBACK_ON_FAILURE)) {
      violations.add("ROLLBACK_ON_FAILURE not called after failed fix");
      missing.add(Operation.ROLLBACK_ON_FAILURE);
    }

    // Check no cleanup on failure path
    if (hasOperation(Operation.CLEANUP_ON_SUCCESS)) {
      violations.add("CLEANUP_ON_SUCCESS should not be called on failure path");
    }

    if (violations.isEmpty()) {
      return ValidationResult.valid(getOperations());
    }
    return ValidationResult.invalid(violations, missing, getOperations());
  }

  /**
   * Validates the workflow for a skipped fix (no modifications).
   *
   * <p>A valid skipped fix workflow should have no WRITE_FILE operations.
   *
   * @return validation result
   */
  public ValidationResult validateSkippedWorkflow() {
    List<String> violations = new ArrayList<>();
    Set<Operation> missing = EnumSet.noneOf(Operation.class);

    if (hasOperation(Operation.WRITE_FILE)) {
      violations.add("WRITE_FILE should not be called for skipped fix");
    }

    if (violations.isEmpty()) {
      return ValidationResult.valid(getOperations());
    }
    return ValidationResult.invalid(violations, missing, getOperations());
  }

  /** Clears all recorded operations. */
  public void clear() {
    operations.clear();
  }

  /**
   * Gets the fixer name.
   *
   * @return the fixer name
   */
  public String getFixerName() {
    return fixerName;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FixerWorkflowTracker[").append(fixerName).append("] {\n");
    for (TrackedOperation op : operations) {
      sb.append("  ").append(op.operation());
      if (op.file() != null) {
        sb.append(" -> ").append(op.file().getFileName());
      }
      sb.append("\n");
    }
    sb.append("}");
    return sb.toString();
  }
}
