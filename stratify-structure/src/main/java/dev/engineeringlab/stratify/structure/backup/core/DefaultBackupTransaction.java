package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupResult;
import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupTransaction;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of BackupTransaction.
 *
 * <p>Manages a group of backup operations that can be committed or rolled back together.
 */
public class DefaultBackupTransaction implements BackupTransaction {

  private final String id;
  private final BackupService service;
  private final List<BackupResult> backups;
  private boolean active;
  private boolean committed;

  public DefaultBackupTransaction(BackupService service) {
    this.id = UUID.randomUUID().toString();
    this.service = service;
    this.backups = new ArrayList<>();
    this.active = true;
    this.committed = false;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public BackupResult backup(Path path) {
    checkActive();
    BackupResult result = service.backup(path);
    backups.add(result);
    return result;
  }

  @Override
  public List<BackupResult> getBackups() {
    return Collections.unmodifiableList(backups);
  }

  @Override
  public void commit() {
    checkActive();
    active = false;
    committed = true;
  }

  @Override
  public void rollback() {
    if (!active && committed) {
      throw new IllegalStateException("Cannot rollback committed transaction");
    }

    active = false;

    // Restore all backed up files in reverse order
    for (int i = backups.size() - 1; i >= 0; i--) {
      BackupResult backup = backups.get(i);
      if (backup.success() && backup.backupPath().isPresent()) {
        service.restore(backup.originalPath());
        service.deleteBackup(backup.originalPath());
      }
    }
  }

  @Override
  public boolean isActive() {
    return active;
  }

  @Override
  public void close() {
    if (active) {
      rollback();
    }
  }

  private void checkActive() {
    if (!active) {
      throw new IllegalStateException("Transaction is no longer active");
    }
  }
}
