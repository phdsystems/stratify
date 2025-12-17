package dev.engineeringlab.stratify.structure.scanner.common.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * Result of a fix operation or preview.
 *
 * <p>Contains information about what was changed (or would be changed in preview mode), whether the
 * fix was successful, and any messages or errors.
 *
 * @since 0.2.0
 */
@Data
@Builder
public class FixResult {

  /** The violation that was fixed (or attempted to fix). */
  private Violation violation;

  /** Whether the fix was successful. */
  @Builder.Default private boolean success = false;

  /** Whether this is a preview (no actual changes made). */
  @Builder.Default private boolean preview = false;

  /** Human-readable description of what was (or would be) done. */
  private String description;

  /** List of files that were (or would be) modified. */
  @Builder.Default private List<FileChange> changes = new ArrayList<>();

  /** Error message if the fix failed. */
  private String errorMessage;

  /** Exception that caused the fix to fail, if any. */
  private Throwable exception;

  /** Represents a change to a file. */
  @Data
  @Builder
  public static class FileChange {
    /** Type of change. */
    private ChangeType type;

    /** Path to the file. */
    private Path filePath;

    /** Original content (for MODIFY operations). */
    private String originalContent;

    /** New content (for MODIFY and CREATE operations). */
    private String newContent;

    /** Line number where the change starts (1-based). */
    private int startLine;

    /** Line number where the change ends (1-based, inclusive). */
    private int endLine;

    /** Description of what was changed. */
    private String description;
  }

  /** Type of file change. */
  public enum ChangeType {
    /** File was created. */
    CREATE,
    /** File was modified. */
    MODIFY,
    /** File was deleted. */
    DELETE,
    /** File was moved/renamed. */
    MOVE
  }

  /**
   * Creates a successful fix result.
   *
   * @param violation the violation that was fixed
   * @param description description of what was done
   * @return a successful fix result
   */
  public static FixResult success(Violation violation, String description) {
    return FixResult.builder().violation(violation).success(true).description(description).build();
  }

  /**
   * Creates a successful fix result with file changes.
   *
   * @param violation the violation that was fixed
   * @param description description of what was done
   * @param changes list of file changes
   * @return a successful fix result
   */
  public static FixResult success(
      Violation violation, String description, List<FileChange> changes) {
    return FixResult.builder()
        .violation(violation)
        .success(true)
        .description(description)
        .changes(changes)
        .build();
  }

  /**
   * Creates a failed fix result.
   *
   * @param violation the violation that failed to fix
   * @param errorMessage description of why the fix failed
   * @return a failed fix result
   */
  public static FixResult failure(Violation violation, String errorMessage) {
    return FixResult.builder()
        .violation(violation)
        .success(false)
        .errorMessage(errorMessage)
        .build();
  }

  /**
   * Creates a failed fix result with exception.
   *
   * @param violation the violation that failed to fix
   * @param errorMessage description of why the fix failed
   * @param exception the exception that caused the failure
   * @return a failed fix result
   */
  public static FixResult failure(Violation violation, String errorMessage, Throwable exception) {
    return FixResult.builder()
        .violation(violation)
        .success(false)
        .errorMessage(errorMessage)
        .exception(exception)
        .build();
  }

  /**
   * Creates a preview result (no actual changes made).
   *
   * @param violation the violation being previewed
   * @param description description of what would be done
   * @param changes list of file changes that would be made
   * @return a preview fix result
   */
  public static FixResult preview(
      Violation violation, String description, List<FileChange> changes) {
    return FixResult.builder()
        .violation(violation)
        .success(true)
        .preview(true)
        .description(description)
        .changes(changes)
        .build();
  }

  /**
   * Creates a result indicating no fix is needed.
   *
   * @param violation the violation that doesn't need fixing
   * @param reason why no fix is needed
   * @return a no-op fix result
   */
  public static FixResult noFixNeeded(Violation violation, String reason) {
    return FixResult.builder()
        .violation(violation)
        .success(true)
        .description("No fix needed: " + reason)
        .build();
  }

  /**
   * Creates a result indicating the fix is not supported.
   *
   * @param violation the violation that cannot be fixed
   * @param reason why the fix is not supported
   * @return an unsupported fix result
   */
  public static FixResult notSupported(Violation violation, String reason) {
    return FixResult.builder()
        .violation(violation)
        .success(false)
        .errorMessage("Fix not supported: " + reason)
        .build();
  }
}
