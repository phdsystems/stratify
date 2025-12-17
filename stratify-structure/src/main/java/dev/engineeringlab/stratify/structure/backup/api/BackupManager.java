package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;
import java.util.List;

/**
 * Manager for coordinating backup operations.
 *
 * <p>The BackupManager is the main entry point for backup functionality. It coordinates backup
 * operations, manages transactions, and delegates actual I/O to {@link BackupService}.
 *
 * <p>Typical usage:
 *
 * <pre>{@code
 * BackupManager manager = Backups.createManager(projectRoot);
 *
 * // Simple backup
 * BackupResult result = manager.backup(path);
 *
 * // Transactional backup
 * try (BackupTransaction tx = manager.beginTransaction()) {
 *     tx.backup(file1);
 *     tx.backup(file2);
 *     tx.commit();
 * }
 * }</pre>
 */
public interface BackupManager {

  /**
   * Returns the project root directory.
   *
   * @return project root path
   */
  Path getProjectRoot();

  /**
   * Creates a backup of the specified file.
   *
   * @param path the file to backup
   * @return the backup result
   */
  BackupResult backup(Path path);

  /**
   * Restores a file from its backup.
   *
   * @param path the original file path
   * @return the restore result
   */
  RestoreResult restore(Path path);

  /**
   * Restores all backed up files.
   *
   * @return list of restore results
   */
  List<RestoreResult> restoreAll();

  /**
   * Begins a new backup transaction.
   *
   * @return a new transaction
   */
  BackupTransaction beginTransaction();

  /**
   * Checks if a backup exists for the specified file.
   *
   * @param path the original file path
   * @return true if backup exists
   */
  boolean hasBackup(Path path);

  /**
   * Lists all files that have backups.
   *
   * @return list of original file paths
   */
  List<Path> listBackedUpFiles();

  /**
   * Cleans up all backups.
   *
   * @return number of backups deleted
   */
  int cleanup();

  /**
   * Returns the underlying backup service.
   *
   * @return the backup service
   */
  BackupService getService();

  /**
   * Returns the backup strategy being used.
   *
   * @return the backup strategy
   */
  BackupStrategy getStrategy();
}
