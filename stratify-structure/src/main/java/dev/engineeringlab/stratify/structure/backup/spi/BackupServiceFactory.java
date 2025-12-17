package dev.engineeringlab.stratify.structure.backup.spi;

import dev.engineeringlab.stratify.structure.backup.api.BackupService;
import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import java.nio.file.Path;

/**
 * Factory for creating BackupService instances.
 *
 * <p>Implementations can be discovered via ServiceLoader to provide custom backup service
 * implementations.
 */
public interface BackupServiceFactory {

  /**
   * Creates a backup service for the given project.
   *
   * @param projectRoot the project root directory
   * @param strategy the backup strategy to use
   * @return a new backup service
   */
  BackupService create(Path projectRoot, BackupStrategy strategy);

  /**
   * Returns the priority of this factory. Higher priority factories are preferred.
   *
   * @return the priority (default 0)
   */
  default int getPriority() {
    return 0;
  }
}
