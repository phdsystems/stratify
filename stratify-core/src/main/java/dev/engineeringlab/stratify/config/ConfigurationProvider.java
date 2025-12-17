package dev.engineeringlab.stratify.config;

/**
 * Interface for configuration providers that can be auto-discovered and injected.
 *
 * <p>Implementations are discovered at runtime and can be injected via the {@code @Configuration}
 * annotation for automatic configuration loading.
 *
 * @since 1.0.0
 */
public interface ConfigurationProvider {

  /**
   * Returns the configuration name used for loading from files.
   *
   * @return configuration name (e.g., "model-registry", "database")
   */
  String getConfigurationName();

  /**
   * Returns the configuration class type.
   *
   * @return the class of the configuration object
   */
  Class<?> getConfigurationType();

  /**
   * Returns a human-readable description of this configuration.
   *
   * @return configuration description
   */
  String getDescription();

  /**
   * Checks if the configuration is valid.
   *
   * @return true if the configuration is valid and usable
   */
  boolean isValid();

  /**
   * Reloads the configuration from its source.
   *
   * <p>Implementations should clear any cached values and reload from the configuration source.
   */
  void reload();
}
