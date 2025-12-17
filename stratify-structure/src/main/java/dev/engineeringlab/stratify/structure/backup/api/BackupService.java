package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Service interface for performing backup and restore I/O operations.
 *
 * <p>This service handles the actual file operations (copy, move, delete) for backup and restore.
 * It does not manage transactions or coordinate multiple operations - that is the responsibility of
 * {@link BackupManager}.
 */
public interface BackupService {

  /**
   * Creates a backup of the specified file.
   *
   * @param originalPath the file to backup
   * @return the result of the backup operation
   */
  BackupResult backup(Path originalPath);

  /**
   * Restores a file from its backup.
   *
   * @param originalPath the original file path to restore to
   * @return the result of the restore operation
   */
  RestoreResult restore(Path originalPath);

  /**
   * Checks if a backup exists for the specified file.
   *
   * @param originalPath the original file path
   * @return true if a backup exists
   */
  boolean hasBackup(Path originalPath);

  /**
   * Returns the backup path for the given original file.
   *
   * @param originalPath the original file path
   * @return the backup path (may not exist)
   */
  Path getBackupPath(Path originalPath);

  /**
   * Lists all backed up files.
   *
   * @return list of original file paths that have backups
   */
  List<Path> listBackedUpFiles();

  /**
   * Deletes the backup for the specified file.
   *
   * @param originalPath the original file path
   * @return true if backup was deleted or didn't exist
   */
  boolean deleteBackup(Path originalPath);

  /**
   * Deletes all backups.
   *
   * @return number of backups deleted
   */
  int deleteAllBackups();
}
