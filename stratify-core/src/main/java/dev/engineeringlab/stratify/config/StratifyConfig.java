package dev.engineeringlab.stratify.config;

import java.util.*;

/**
 * Configuration for Stratify runtime behavior.
 *
 * <p>StratifyConfig provides a hierarchical configuration system that supports:
 * <ul>
 *   <li>Default values</li>
 *   <li>Property file configuration</li>
 *   <li>Environment variable overrides</li>
 *   <li>System property overrides</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * StratifyConfig config = StratifyConfig.builder()
 *     .property("provider.default", "openai")
 *     .property("provider.timeout", "30000")
 *     .build();
 *
 * String defaultProvider = config.get("provider.default").orElse("default");
 * </pre>
 */
public class StratifyConfig {

    private static final String ENV_PREFIX = "STRATIFY_";
    private static final String SYS_PREFIX = "stratify.";

    private final Map<String, String> properties;

    private StratifyConfig(Map<String, String> properties) {
        this.properties = Map.copyOf(properties);
    }

    /**
     * Creates a new configuration builder.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates an empty configuration.
     *
     * @return empty config
     */
    public static StratifyConfig empty() {
        return new StratifyConfig(Collections.emptyMap());
    }

    /**
     * Gets a configuration value by key.
     *
     * <p>Resolution order (first non-null wins):
     * <ol>
     *   <li>System property (stratify.{key})</li>
     *   <li>Environment variable (STRATIFY_{KEY})</li>
     *   <li>Configuration property</li>
     * </ol>
     *
     * @param key the property key
     * @return the value, or empty if not found
     */
    public Optional<String> get(String key) {
        // System property override
        String sysValue = System.getProperty(SYS_PREFIX + key);
        if (sysValue != null) {
            return Optional.of(sysValue);
        }

        // Environment variable override
        String envKey = ENV_PREFIX + key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            return Optional.of(envValue);
        }

        // Configuration property
        return Optional.ofNullable(properties.get(key));
    }

    /**
     * Gets a configuration value with a default.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the value or default
     */
    public String get(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    /**
     * Gets a configuration value as an integer.
     *
     * @param key the property key
     * @return the integer value, or empty if not found or not a valid integer
     */
    public Optional<Integer> getInt(String key) {
        return get(key).flatMap(v -> {
            try {
                return Optional.of(Integer.parseInt(v));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        });
    }

    /**
     * Gets a configuration value as an integer with default.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the integer value or default
     */
    public int getInt(String key, int defaultValue) {
        return getInt(key).orElse(defaultValue);
    }

    /**
     * Gets a configuration value as a boolean.
     *
     * @param key the property key
     * @return the boolean value, or empty if not found
     */
    public Optional<Boolean> getBoolean(String key) {
        return get(key).map(v ->
            "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v));
    }

    /**
     * Gets a configuration value as a boolean with default.
     *
     * @param key the property key
     * @param defaultValue the default value
     * @return the boolean value or default
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        return getBoolean(key).orElse(defaultValue);
    }

    /**
     * Gets all configuration keys.
     *
     * @return set of keys
     */
    public Set<String> keys() {
        return properties.keySet();
    }

    /**
     * Creates a new config with additional properties.
     *
     * @param key the property key
     * @param value the property value
     * @return new config instance
     */
    public StratifyConfig with(String key, String value) {
        Map<String, String> newProps = new HashMap<>(properties);
        newProps.put(key, value);
        return new StratifyConfig(newProps);
    }

    @Override
    public String toString() {
        return "StratifyConfig{properties=" + properties.keySet() + "}";
    }

    /**
     * Builder for StratifyConfig.
     */
    public static class Builder {

        private final Map<String, String> properties = new HashMap<>();

        private Builder() {}

        /**
         * Sets a configuration property.
         *
         * @param key the property key
         * @param value the property value
         * @return this builder
         */
        public Builder property(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         * Sets multiple properties from a map.
         *
         * @param props the properties map
         * @return this builder
         */
        public Builder properties(Map<String, String> props) {
            properties.putAll(props);
            return this;
        }

        /**
         * Sets multiple properties from a Properties object.
         *
         * @param props the properties
         * @return this builder
         */
        public Builder properties(Properties props) {
            props.forEach((k, v) -> properties.put(k.toString(), v.toString()));
            return this;
        }

        /**
         * Builds the configuration.
         *
         * @return the configuration instance
         */
        public StratifyConfig build() {
            return new StratifyConfig(properties);
        }
    }
}
