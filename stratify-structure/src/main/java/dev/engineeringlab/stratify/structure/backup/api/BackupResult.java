package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Result of a backup operation.
 *
 * @param originalPath the original file path
 * @param backupPath the backup file path (empty if backup failed)
 * @param success whether the backup succeeded
 * @param message optional message (error or info)
 */
public record BackupResult(
    Path originalPath, Optional<Path> backupPath, boolean success, Optional<String> message) {

  public static BackupResult success(Path original, Path backup) {
    return new BackupResult(original, Optional.of(backup), true, Optional.empty());
  }

  public static BackupResult success(Path original, Path backup, String message) {
    return new BackupResult(original, Optional.of(backup), true, Optional.of(message));
  }

  public static BackupResult failure(Path original, String reason) {
    return new BackupResult(original, Optional.empty(), false, Optional.of(reason));
  }

  public static BackupResult skipped(Path original, String reason) {
    return new BackupResult(original, Optional.empty(), true, Optional.of(reason));
  }
}
