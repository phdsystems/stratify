package dev.engineeringlab.stratify.structure.scanner.common.config;

import java.nio.file.Path;

/** Result of loading configuration, including metadata about the source. */
public class ConfigurationResult {

  private final ComplianceConfig config;
  private final boolean wasGenerated;
  private final Path configFile;

  /**
   * Creates a new configuration result.
   *
   * @param configParam The loaded configuration
   * @param wasGeneratedParam True if the config was auto-generated
   * @param configFileParam Path to the configuration file (may be null for classpath)
   */
  public ConfigurationResult(
      ComplianceConfig configParam, boolean wasGeneratedParam, Path configFileParam) {
    this.config = new ComplianceConfig(configParam);
    this.wasGenerated = wasGeneratedParam;
    this.configFile = configFileParam;
  }

  /**
   * Gets a copy of the loaded configuration.
   *
   * @return A copy of the configuration
   */
  public ComplianceConfig getConfig() {
    return new ComplianceConfig(config);
  }

  /**
   * Checks if the configuration was auto-generated.
   *
   * @return True if auto-generated, false if loaded from existing file
   */
  public boolean wasGenerated() {
    return wasGenerated;
  }

  /**
   * Checks if the configuration was auto-generated. Alias for {@link #wasGenerated()}.
   *
   * @return True if auto-generated, false if loaded from existing file
   */
  public boolean isGenerated() {
    return wasGenerated;
  }

  /**
   * Gets the path to the configuration file.
   *
   * @return Path to config file, or null if loaded from classpath
   */
  public Path getConfigFile() {
    return configFile;
  }

  /**
   * Gets the path to the configuration file. Alias for {@link #getConfigFile()}.
   *
   * @return Path to config file, or null if loaded from classpath
   */
  public Path getConfigFilePath() {
    return configFile;
  }
}
