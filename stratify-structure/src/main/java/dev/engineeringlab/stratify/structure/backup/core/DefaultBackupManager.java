package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupManager;
import dev.engineeringlab.stratify.structure.backup.api.BackupResult;
import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import dev.engineeringlab.stratify.structure.backup.api.BackupTransaction;
import dev.engineeringlab.stratify.structure.backup.api.RestoreResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of BackupManager.
 *
 * <p>Coordinates backup operations and manages transactions.
 */
public class DefaultBackupManager implements BackupManager {

  private final Path projectRoot;
  private final BackupService service;
  private final BackupStrategy strategy;

  public DefaultBackupManager(Path projectRoot, BackupStrategy strategy) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.strategy = strategy;
    this.service = new FileBackupService(projectRoot, strategy);
  }

  public DefaultBackupManager(Path projectRoot, BackupService service, BackupStrategy strategy) {
    this.projectRoot = projectRoot.toAbsolutePath().normalize();
    this.service = service;
    this.strategy = strategy;
  }

  @Override
  public Path getProjectRoot() {
    return projectRoot;
  }

  @Override
  public BackupResult backup(Path path) {
    return service.backup(path);
  }

  @Override
  public RestoreResult restore(Path path) {
    return service.restore(path);
  }

  @Override
  public List<RestoreResult> restoreAll() {
    List<RestoreResult> results = new ArrayList<>();
    for (Path file : service.listBackedUpFiles()) {
      results.add(service.restore(file));
    }
    return results;
  }

  @Override
  public BackupTransaction beginTransaction() {
    return new DefaultBackupTransaction(service);
  }

  @Override
  public boolean hasBackup(Path path) {
    return service.hasBackup(path);
  }

  @Override
  public List<Path> listBackedUpFiles() {
    return service.listBackedUpFiles();
  }

  @Override
  public int cleanup() {
    return service.deleteAllBackups();
  }

  @Override
  public BackupService getService() {
    return service;
  }

  @Override
  public BackupStrategy getStrategy() {
    return strategy;
  }
}
