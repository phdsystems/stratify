package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import dev.engineeringlab.stratify.structure.backup.spi.BackupServiceFactory;
import java.nio.file.Path;

/**
 * Default implementation of BackupServiceFactory.
 *
 * <p>Creates FileBackupService instances.
 */
public class DefaultBackupServiceFactory implements BackupServiceFactory {

  @Override
  public BackupService create(Path projectRoot, BackupStrategy strategy) {
    return new FileBackupService(projectRoot, strategy);
  }

  @Override
  public int getPriority() {
    return 0;
  }
}
