package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import java.nio.file.Path;

/**
 * Backup strategy that stores backups in a staging directory.
 *
 * <p>Backups are stored in {@code .remediation/staging/} with the relative path preserved and a
 * {@code .bak} extension added.
 *
 * <p>Example:
 *
 * <pre>
 * Original: /project/src/main/java/Foo.java
 * Backup:   /project/.remediation/staging/src/main/java/Foo.java.bak
 * </pre>
 */
public class StagingBackupStrategy implements BackupStrategy {

  public static final String NAME = "staging";
  public static final String STAGING_DIR = ".remediation/staging";
  public static final String BACKUP_EXTENSION = ".bak";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Path computeBackupPath(Path projectRoot, Path originalPath) {
    Path relativePath = projectRoot.relativize(originalPath);
    return projectRoot.resolve(STAGING_DIR).resolve(relativePath + BACKUP_EXTENSION);
  }

  @Override
  public Path computeOriginalPath(Path projectRoot, Path backupPath) {
    Path stagingRoot = projectRoot.resolve(STAGING_DIR);
    Path relativePath = stagingRoot.relativize(backupPath);
    String pathStr = relativePath.toString();

    if (pathStr.endsWith(BACKUP_EXTENSION)) {
      pathStr = pathStr.substring(0, pathStr.length() - BACKUP_EXTENSION.length());
    }

    return projectRoot.resolve(pathStr);
  }

  @Override
  public Path getBackupRoot(Path projectRoot) {
    return projectRoot.resolve(STAGING_DIR);
  }
}
