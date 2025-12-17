package dev.engineeringlab.stratify.structure.backup.spi;

import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import java.nio.file.Path;
import java.util.List;

/**
 * Service Provider Interface for discovering and providing backup strategies.
 *
 * <p>Implementations can be discovered via ServiceLoader to provide custom backup strategies.
 */
public interface BackupStrategyProvider {

  /**
   * Returns all strategies provided by this provider.
   *
   * @return list of backup strategies
   */
  List<BackupStrategy> getStrategies();

  /**
   * Returns a strategy by name.
   *
   * @param name the strategy name
   * @return the strategy, or null if not found
   */
  BackupStrategy getStrategy(String name);

  /**
   * Returns the default strategy for the given project.
   *
   * @param projectRoot the project root directory
   * @return the default strategy
   */
  BackupStrategy getDefaultStrategy(Path projectRoot);
}
