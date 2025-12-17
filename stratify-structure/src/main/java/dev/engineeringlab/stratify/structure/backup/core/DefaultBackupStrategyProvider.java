package dev.engineeringlab.stratify.structure.backup.core;

import dev.engineeringlab.stratify.structure.backup.api.BackupStrategy;
import dev.engineeringlab.stratify.structure.backup.spi.BackupStrategyProvider;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Default implementation of BackupStrategyProvider.
 *
 * <p>Provides the built-in backup strategies.
 */
public class DefaultBackupStrategyProvider implements BackupStrategyProvider {

  private final Map<String, BackupStrategy> strategies;

  public DefaultBackupStrategyProvider() {
    List<BackupStrategy> strategyList = List.of(new StagingBackupStrategy());

    this.strategies =
        strategyList.stream()
            .collect(Collectors.toMap(BackupStrategy::getName, Function.identity()));
  }

  @Override
  public List<BackupStrategy> getStrategies() {
    return List.copyOf(strategies.values());
  }

  @Override
  public BackupStrategy getStrategy(String name) {
    return strategies.get(name);
  }

  @Override
  public BackupStrategy getDefaultStrategy(Path projectRoot) {
    return new StagingBackupStrategy();
  }
}
