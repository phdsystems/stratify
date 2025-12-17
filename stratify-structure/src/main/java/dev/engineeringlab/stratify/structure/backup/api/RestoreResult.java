package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Result of a restore operation.
 *
 * @param targetPath the restored file path
 * @param backupPath the backup file that was restored from
 * @param success whether the restore succeeded
 * @param message optional message (error or info)
 */
public record RestoreResult(
    Path targetPath, Path backupPath, boolean success, Optional<String> message) {

  public static RestoreResult success(Path target, Path backup) {
    return new RestoreResult(target, backup, true, Optional.empty());
  }

  public static RestoreResult success(Path target, Path backup, String message) {
    return new RestoreResult(target, backup, true, Optional.of(message));
  }

  public static RestoreResult failure(Path target, Path backup, String reason) {
    return new RestoreResult(target, backup, false, Optional.of(reason));
  }
}
