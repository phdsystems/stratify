package dev.engineeringlab.stratify.structure.backup.facade;

import dev.engineeringlab.stratify.structure.backup.api.BackupManager;
import dev.engineeringlab.stratify.structure.backup.api.BackupResult;
import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import dev.engineeringlab.stratify.structure.backup.api.BackupTransaction;
import dev.engineeringlab.stratify.structure.backup.api.RestoreResult;
import dev.engineeringlab.stratify.structure.backup.core.DefaultBackupManager;
import dev.engineeringlab.stratify.structure.backup.core.FileBackupService;
import dev.engineeringlab.stratify.structure.backup.core.StagingBackupStrategy;
import java.nio.file.Path;
import java.util.List;

/**
 * Simplified entry point for backup operations.
 *
 * <p>Provides factory methods and convenience operations for common backup tasks.
 *
 * <h2>Quick Start</h2>
 *
 * <pre>{@code
 * // Create a manager
 * BackupManager manager = Backups.createManager(projectRoot);
 *
 * // Simple backup
 * BackupResult result = manager.backup(file);
 *
 * // Transactional backup
 * try (BackupTransaction tx = manager.beginTransaction()) {
 *     tx.backup(file1);
 *     tx.backup(file2);
 *     tx.commit();
 * }
 *
 * // Static convenience methods
 * Backups.backup(projectRoot, file);
 * Backups.restore(projectRoot, file);
 * }</pre>
 */
public final class Backups {

  private Backups() {
    // Utility class
  }

  // ========== Factory Methods ==========

  /**
   * Creates a BackupManager with the default staging strategy.
   *
   * @param projectRoot the project root directory
   * @return a new BackupManager
   */
  public static BackupManager createManager(Path projectRoot) {
    return createManager(projectRoot, new StagingBackupStrategy());
  }

  /**
   * Creates a BackupManager with the specified strategy.
   *
   * @param projectRoot the project root directory
   * @param strategy the backup strategy
   * @return a new BackupManager
   */
  public static BackupManager createManager(Path projectRoot, BackupStrategy strategy) {
    return new DefaultBackupManager(projectRoot, strategy);
  }

  /**
   * Creates a BackupService with the default staging strategy.
   *
   * @param projectRoot the project root directory
   * @return a new BackupService
   */
  public static BackupService createService(Path projectRoot) {
    return createService(projectRoot, new StagingBackupStrategy());
  }

  /**
   * Creates a BackupService with the specified strategy.
   *
   * @param projectRoot the project root directory
   * @param strategy the backup strategy
   * @return a new BackupService
   */
  public static BackupService createService(Path projectRoot, BackupStrategy strategy) {
    return new FileBackupService(projectRoot, strategy);
  }

  /**
   * Returns the default backup strategy.
   *
   * @return the staging backup strategy
   */
  public static BackupStrategy defaultStrategy() {
    return new StagingBackupStrategy();
  }

  // ========== Convenience Methods ==========

  /**
   * Backs up a file using the default staging strategy.
   *
   * @param projectRoot the project root directory
   * @param file the file to backup
   * @return the backup result
   */
  public static BackupResult backup(Path projectRoot, Path file) {
    return createManager(projectRoot).backup(file);
  }

  /**
   * Restores a file from its backup.
   *
   * @param projectRoot the project root directory
   * @param file the original file path
   * @return the restore result
   */
  public static RestoreResult restore(Path projectRoot, Path file) {
    return createManager(projectRoot).restore(file);
  }

  /**
   * Restores all backed up files.
   *
   * @param projectRoot the project root directory
   * @return list of restore results
   */
  public static List<RestoreResult> restoreAll(Path projectRoot) {
    return createManager(projectRoot).restoreAll();
  }

  /**
   * Checks if a backup exists for a file.
   *
   * @param projectRoot the project root directory
   * @param file the original file path
   * @return true if backup exists
   */
  public static boolean hasBackup(Path projectRoot, Path file) {
    return createManager(projectRoot).hasBackup(file);
  }

  /**
   * Lists all backed up files.
   *
   * @param projectRoot the project root directory
   * @return list of original file paths
   */
  public static List<Path> listBackedUpFiles(Path projectRoot) {
    return createManager(projectRoot).listBackedUpFiles();
  }

  /**
   * Deletes all backups.
   *
   * @param projectRoot the project root directory
   * @return number of backups deleted
   */
  public static int cleanup(Path projectRoot) {
    return createManager(projectRoot).cleanup();
  }

  /**
   * Begins a backup transaction.
   *
   * @param projectRoot the project root directory
   * @return a new transaction
   */
  public static BackupTransaction beginTransaction(Path projectRoot) {
    return createManager(projectRoot).beginTransaction();
  }
}
