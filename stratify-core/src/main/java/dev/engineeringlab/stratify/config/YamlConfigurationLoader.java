package dev.engineeringlab.stratify.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import dev.engineeringlab.stratify.annotation.Configuration;
import java.io.InputStream;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading YAML configuration files with environment-based overrides.
 *
 * <p>Provides a layered configuration approach:
 *
 * <ol>
 *   <li>Base configuration from {@code {configName}.yaml}
 *   <li>Environment-specific overrides from {@code {configName}-{ENVIRONMENT}.yaml}
 *   <li>Environment variables as fallback for specific values
 * </ol>
 *
 * <p>The {@code ENVIRONMENT} system environment variable determines which override file to load.
 * Common values: {@code dev}, {@code staging}, {@code prod}, {@code test}, {@code int}.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * // Load configuration for a specific provider
 * MyConfig config = YamlConfigurationLoader.loadConfig(
 *     "model-registry",
 *     "providers.openai",
 *     MyConfig.class
 * );
 *
 * // Load entire configuration file
 * RegistryFile registry = YamlConfigurationLoader.loadConfig(
 *     "model-registry",
 *     RegistryFile.class
 * );
 *
 * // Get environment name
 * String env = YamlConfigurationLoader.getEnvironment(); // "dev", "prod", etc.
 * }</pre>
 *
 * <p><b>Configuration File Structure:</b>
 *
 * <pre>
 * src/main/resources/
 * ├── model-registry.yaml           # Base config (no secrets)
 * ├── model-registry-dev.yaml       # Development overrides
 * ├── model-registry-staging.yaml   # Staging overrides
 * └── model-registry-prod.yaml      # Production overrides
 * </pre>
 *
 * @since 2.1.0
 */
@Configuration(
    name = "yaml-configuration-loader",
    description = "YAML configuration loader with environment-based overrides")
public final class YamlConfigurationLoader {

  private static final Logger log = LoggerFactory.getLogger(YamlConfigurationLoader.class);

  private static final String ENVIRONMENT_VAR = "ENVIRONMENT";
  private static final String YAML_EXTENSION = ".yaml";

  private static final ObjectMapper YAML_MAPPER = createYamlMapper();

  private static final Map<String, Object> CONFIG_CACHE = new ConcurrentHashMap<>();

  private YamlConfigurationLoader() {
    // Utility class - no instantiation
  }

  /**
   * Creates a configured YAML ObjectMapper.
   *
   * @return configured ObjectMapper for YAML
   */
  private static ObjectMapper createYamlMapper() {
    ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    return mapper;
  }

  /**
   * Gets the current environment name from the ENVIRONMENT variable.
   *
   * @return environment name (e.g., "dev", "prod") or empty if not set
   */
  public static Optional<String> getEnvironment() {
    String env = System.getenv(ENVIRONMENT_VAR);
    if (env != null && !env.isBlank()) {
      return Optional.of(env.toLowerCase().trim());
    }
    return Optional.empty();
  }

  /**
   * Loads a configuration file and deserializes it to the specified type.
   *
   * <p>Automatically merges environment-specific overrides if ENVIRONMENT is set.
   *
   * @param configName base name of the config file (without .yaml extension)
   * @param targetType class to deserialize to
   * @param <T> configuration type
   * @return deserialized configuration
   * @throws ConfigurationException if config file cannot be loaded or parsed
   */
  public static <T> T loadConfig(String configName, Class<T> targetType) {
    return loadConfig(configName, null, targetType);
  }

  /**
   * Loads a configuration file and extracts a nested path, deserializing to the specified type.
   *
   * <p>Automatically merges environment-specific overrides if ENVIRONMENT is set.
   *
   * @param configName base name of the config file (without .yaml extension)
   * @param path dot-separated path to extract (e.g., "providers.openai"), or null for root
   * @param targetType class to deserialize to
   * @param <T> configuration type
   * @return deserialized configuration
   * @throws ConfigurationException if config file cannot be loaded or parsed
   */
  @SuppressWarnings("unchecked")
  public static <T> T loadConfig(String configName, String path, Class<T> targetType) {
    Objects.requireNonNull(configName, "configName cannot be null");
    Objects.requireNonNull(targetType, "targetType cannot be null");

    String cacheKey = configName + ":" + (path != null ? path : "") + ":" + targetType.getName();

    return (T)
        CONFIG_CACHE.computeIfAbsent(
            cacheKey,
            k -> {
              try {
                // Load base configuration
                Map<String, Object> baseConfig = loadYamlFile(getConfigFilePath(configName));
                log.debug("Loaded base config: {}", configName);

                // Load and merge environment-specific overrides
                Optional<String> env = getEnvironment();
                if (env.isPresent()) {
                  String envConfigPath = getConfigFilePath(configName + "-" + env.get());
                  if (resourceExists(envConfigPath)) {
                    Map<String, Object> envConfig = loadYamlFile(envConfigPath);
                    mergeConfig(baseConfig, envConfig);
                    log.info(
                        "Merged environment config: {} (ENVIRONMENT={})", envConfigPath, env.get());
                  } else {
                    log.debug(
                        "No environment config found: {} (ENVIRONMENT={})",
                        envConfigPath,
                        env.get());
                  }
                }

                // Extract nested path if specified
                Object configNode = baseConfig;
                if (path != null && !path.isBlank()) {
                  configNode = extractPath(baseConfig, path);
                  if (configNode == null) {
                    throw new ConfigurationException(
                        "Path '" + path + "' not found in config: " + configName);
                  }
                }

                // Convert to target type
                return YAML_MAPPER.convertValue(configNode, targetType);

              } catch (ConfigurationException e) {
                throw e;
              } catch (Exception e) {
                throw new ConfigurationException("Failed to load config: " + configName, e);
              }
            });
  }

  /**
   * Loads a provider configuration with automatic API key resolution.
   *
   * <p>Resolves API key in order:
   *
   * <ol>
   *   <li>Explicit apiKey parameter (if not null - empty string is valid)
   *   <li>apiKey field from YAML config
   *   <li>Environment variable specified in apiKeyEnvVar
   * </ol>
   *
   * @param configName base name of the config file
   * @param providerPath dot-separated path to provider (e.g., "providers.openai")
   * @param targetType class to deserialize to
   * @param apiKey optional API key override (null = use config, "" = use empty)
   * @param <T> configuration type (must have setApiKey method)
   * @return deserialized configuration with resolved API key
   * @throws ConfigurationException if config cannot be loaded
   */
  public static <T> T loadProviderConfig(
      String configName, String providerPath, Class<T> targetType, String apiKey) {

    T config = loadConfig(configName, providerPath, targetType);

    // Resolve API key
    String resolvedKey = resolveApiKey(config, apiKey);
    setApiKeyIfPossible(config, resolvedKey);

    return config;
  }

  /**
   * Clears the configuration cache.
   *
   * <p>Useful for testing or when configuration files have changed.
   */
  public static void clearCache() {
    CONFIG_CACHE.clear();
    log.debug("Configuration cache cleared");
  }

  /**
   * Loads a YAML file from classpath.
   *
   * @param resourcePath path to resource (e.g., "/model-registry.yaml")
   * @return parsed YAML as a Map
   * @throws ConfigurationException if file cannot be loaded
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> loadYamlFile(String resourcePath) {
    try (InputStream is = YamlConfigurationLoader.class.getResourceAsStream(resourcePath)) {
      if (is == null) {
        throw new ConfigurationException("Config file not found: " + resourcePath);
      }
      return YAML_MAPPER.readValue(is, Map.class);
    } catch (ConfigurationException e) {
      throw e;
    } catch (Exception e) {
      throw new ConfigurationException("Failed to parse YAML: " + resourcePath, e);
    }
  }

  /**
   * Checks if a resource exists on the classpath.
   *
   * @param resourcePath path to check
   * @return true if resource exists
   */
  private static boolean resourceExists(String resourcePath) {
    return YamlConfigurationLoader.class.getResource(resourcePath) != null;
  }

  /**
   * Gets the config file path from the base name.
   *
   * @param configName base config name
   * @return resource path (e.g., "/model-registry.yaml")
   */
  private static String getConfigFilePath(String configName) {
    return "/" + configName + YAML_EXTENSION;
  }

  /**
   * Extracts a nested path from a configuration map.
   *
   * @param config configuration map
   * @param path dot-separated path (e.g., "providers.openai")
   * @return extracted value or null if not found
   */
  @SuppressWarnings("unchecked")
  private static Object extractPath(Map<String, Object> config, String path) {
    String[] parts = path.split("\\.");
    Object current = config;

    for (String part : parts) {
      if (current instanceof Map) {
        current = ((Map<String, Object>) current).get(part);
      } else {
        return null;
      }
    }

    return current;
  }

  /**
   * Deep merges override configuration into base configuration.
   *
   * <p>Override values replace base values. Maps are merged recursively.
   *
   * @param base base configuration (modified in place)
   * @param override override configuration
   */
  @SuppressWarnings("unchecked")
  private static void mergeConfig(Map<String, Object> base, Map<String, Object> override) {
    for (Map.Entry<String, Object> entry : override.entrySet()) {
      String key = entry.getKey();
      Object overrideValue = entry.getValue();
      Object baseValue = base.get(key);

      if (overrideValue instanceof Map && baseValue instanceof Map) {
        // Recursively merge maps
        mergeConfig((Map<String, Object>) baseValue, (Map<String, Object>) overrideValue);
      } else if (overrideValue != null) {
        // Override value takes precedence
        base.put(key, overrideValue);
      }
    }
  }

  /**
   * Resolves the API key from config, environment variable, or parameter.
   *
   * <p>Priority order:
   *
   * <ol>
   *   <li>Explicit parameter (if not null - empty string is valid)
   *   <li>Config apiKey field from YAML
   *   <li>Environment variable specified in apiKeyEnvVar
   * </ol>
   *
   * @param config configuration object
   * @param apiKey optional API key parameter (null means "use config", empty means "use empty")
   * @return resolved API key or null
   */
  private static String resolveApiKey(Object config, String apiKey) {
    // Priority 1: Explicit parameter (null means not provided, empty string is explicit)
    if (apiKey != null) {
      return apiKey;
    }

    // Priority 2: Config apiKey field
    String configApiKey = getApiKeyFromConfig(config);
    if (configApiKey != null && !configApiKey.isBlank()) {
      return configApiKey;
    }

    // Priority 3: Environment variable
    String envVarName = getApiKeyEnvVarFromConfig(config);
    if (envVarName != null) {
      String envValue = System.getenv(envVarName);
      if (envValue != null && !envValue.isBlank()) {
        log.debug("Using API key from environment variable: {}", envVarName);
        return envValue;
      }
    }

    return null;
  }

  /**
   * Gets the apiKey field value from a config object via reflection.
   *
   * @param config configuration object
   * @return apiKey value or null
   */
  private static String getApiKeyFromConfig(Object config) {
    try {
      var method = config.getClass().getMethod("getApiKey");
      Object result = method.invoke(config);
      return result instanceof String ? (String) result : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Gets the apiKeyEnvVar field value from a config object via reflection.
   *
   * @param config configuration object
   * @return apiKeyEnvVar value or null
   */
  private static String getApiKeyEnvVarFromConfig(Object config) {
    try {
      var method = config.getClass().getMethod("getApiKeyEnvVar");
      Object result = method.invoke(config);
      return result instanceof String ? (String) result : null;
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Sets the apiKey field on a config object via reflection.
   *
   * @param config configuration object
   * @param apiKey API key to set
   */
  private static void setApiKeyIfPossible(Object config, String apiKey) {
    if (apiKey == null) {
      return;
    }
    try {
      var method = config.getClass().getMethod("setApiKey", String.class);
      method.invoke(config, apiKey);
    } catch (Exception e) {
      log.trace("Could not set apiKey on config: {}", e.getMessage());
    }
  }

  /** Exception thrown when configuration loading fails. */
  public static class ConfigurationException extends RuntimeException {

    public ConfigurationException(String message) {
      super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
