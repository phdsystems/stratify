package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupResult;
import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import dev.engineeringlab.stratify.structure.backup.api.RestoreResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * File-based implementation of BackupService.
 *
 * <p>Performs backup and restore operations using file system operations.
 */
public class FileBackupService implements BackupService {

  private final Path projectRoot;
  private final BackupStrategy strategy;

  public FileBackupService(Path projectRoot, BackupStrategy strategy) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.strategy = strategy;
  }

  @Override
  public BackupResult backup(Path originalPath) {
    Path absPath = originalPath.toAbsolutePath().normalize();

    if (!Files.exists(absPath)) {
      return BackupResult.skipped(absPath, "File does not exist");
    }

    if (!Files.isRegularFile(absPath)) {
      return BackupResult.skipped(absPath, "Not a regular file");
    }

    Path backupPath = strategy.computeBackupPath(projectRoot, absPath);

    try {
      Files.createDirectories(backupPath.getParent());
      Files.copy(absPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
      return BackupResult.success(absPath, backupPath);
    } catch (IOException e) {
      return BackupResult.failure(absPath, "Backup failed: " + e.getMessage());
    }
  }

  @Override
  public RestoreResult restore(Path originalPath) {
    Path absPath = originalPath.toAbsolutePath().normalize();
    Path backupPath = strategy.computeBackupPath(projectRoot, absPath);

    if (!Files.exists(backupPath)) {
      return RestoreResult.failure(absPath, backupPath, "Backup does not exist");
    }

    try {
      Files.createDirectories(absPath.getParent());
      Files.copy(backupPath, absPath, StandardCopyOption.REPLACE_EXISTING);
      return RestoreResult.success(absPath, backupPath);
    } catch (IOException e) {
      return RestoreResult.failure(absPath, backupPath, "Restore failed: " + e.getMessage());
    }
  }

  @Override
  public boolean hasBackup(Path originalPath) {
    Path absPath = originalPath.toAbsolutePath().normalize();
    Path backupPath = strategy.computeBackupPath(projectRoot, absPath);
    return Files.exists(backupPath);
  }

  @Override
  public Path getBackupPath(Path originalPath) {
    Path absPath = originalPath.toAbsolutePath().normalize();
    return strategy.computeBackupPath(projectRoot, absPath);
  }

  @Override
  public List<Path> listBackedUpFiles() {
    Path backupRoot = strategy.getBackupRoot(projectRoot);
    List<Path> files = new ArrayList<>();

    if (!Files.exists(backupRoot)) {
      return files;
    }

    try (Stream<Path> stream = Files.walk(backupRoot)) {
      stream
          .filter(Files::isRegularFile)
          .map(backupPath -> strategy.computeOriginalPath(projectRoot, backupPath))
          .forEach(files::add);
    } catch (IOException e) {
      // Return what we have
    }

    return files;
  }

  @Override
  public boolean deleteBackup(Path originalPath) {
    Path absPath = originalPath.toAbsolutePath().normalize();
    Path backupPath = strategy.computeBackupPath(projectRoot, absPath);

    try {
      return Files.deleteIfExists(backupPath);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public int deleteAllBackups() {
    Path backupRoot = strategy.getBackupRoot(projectRoot);

    if (!Files.exists(backupRoot)) {
      return 0;
    }

    int[] count = {0};
    try (Stream<Path> stream = Files.walk(backupRoot)) {
      stream
          .sorted(Comparator.reverseOrder())
          .forEach(
              path -> {
                try {
                  if (Files.isRegularFile(path)) {
                    count[0]++;
                  }
                  Files.delete(path);
                } catch (IOException e) {
                  // Continue
                }
              });
    } catch (IOException e) {
      // Return what we deleted
    }

    return count[0];
  }
}
