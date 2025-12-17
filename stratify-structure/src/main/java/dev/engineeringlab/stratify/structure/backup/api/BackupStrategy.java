package dev.engineeringlab.stratify.structure.backup.api;

import java.nio.file.Path;

/**
 * Strategy for determining backup file locations.
 *
 * <p>Implementations define how backup paths are computed from original paths. Common strategies
 * include:
 *
 * <ul>
 *   <li>Staging: backups in a staging directory with relative paths preserved
 *   <li>Sibling: backups alongside originals with .bak extension
 *   <li>Timestamped: backups with timestamp suffixes
 * </ul>
 */
public interface BackupStrategy {

  /**
   * Returns the name of this strategy.
   *
   * @return strategy name
   */
  String getName();

  /**
   * Computes the backup path for the given original file.
   *
   * @param projectRoot the project root directory
   * @param originalPath the original file path (absolute)
   * @return the computed backup path
   */
  Path computeBackupPath(Path projectRoot, Path originalPath);

  /**
   * Computes the original path from a backup path.
   *
   * @param projectRoot the project root directory
   * @param backupPath the backup file path
   * @return the original file path
   */
  Path computeOriginalPath(Path projectRoot, Path backupPath);

  /**
   * Returns the root directory where backups are stored.
   *
   * @param projectRoot the project root directory
   * @return the backup root directory
   */
  Path getBackupRoot(Path projectRoot);
}
