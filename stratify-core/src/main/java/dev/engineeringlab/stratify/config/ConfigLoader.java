package dev.engineeringlab.stratify.config;

import dev.engineeringlab.stratify.error.ErrorCode;
import dev.engineeringlab.stratify.error.StratifyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Loads Stratify configuration from various sources.
 *
 * <p>ConfigLoader searches for configuration in the following order:
 * <ol>
 *   <li>Classpath: stratify.properties</li>
 *   <li>Working directory: stratify.properties</li>
 *   <li>User home: ~/.stratify/stratify.properties</li>
 * </ol>
 *
 * <p>Example usage:
 * <pre>
 * StratifyConfig config = ConfigLoader.load();
 * // or from a specific file
 * StratifyConfig config = ConfigLoader.loadFrom(Path.of("custom-config.properties"));
 * </pre>
 */
public final class ConfigLoader {

    private static final Logger log = LoggerFactory.getLogger(ConfigLoader.class);

    private static final String DEFAULT_FILENAME = "stratify.properties";
    private static final String USER_CONFIG_DIR = ".stratify";

    private ConfigLoader() {
        // Utility class
    }

    /**
     * Loads configuration from default locations.
     *
     * @return the loaded configuration
     */
    public static StratifyConfig load() {
        StratifyConfig.Builder builder = StratifyConfig.builder();

        // Try classpath first
        loadFromClasspath(DEFAULT_FILENAME, builder);

        // Then working directory
        Path workingDirConfig = Path.of(DEFAULT_FILENAME);
        if (Files.exists(workingDirConfig)) {
            loadFromPath(workingDirConfig, builder);
        }

        // Then user home
        Path userConfig = Path.of(System.getProperty("user.home"), USER_CONFIG_DIR, DEFAULT_FILENAME);
        if (Files.exists(userConfig)) {
            loadFromPath(userConfig, builder);
        }

        return builder.build();
    }

    /**
     * Loads configuration from a specific file.
     *
     * @param path the configuration file path
     * @return the loaded configuration
     * @throws StratifyException if the file cannot be read
     */
    public static StratifyConfig loadFrom(Path path) {
        StratifyConfig.Builder builder = StratifyConfig.builder();
        loadFromPath(path, builder);
        return builder.build();
    }

    /**
     * Loads configuration from classpath resource.
     *
     * @param resourceName the resource name
     * @return the loaded configuration
     */
    public static StratifyConfig loadFromClasspath(String resourceName) {
        StratifyConfig.Builder builder = StratifyConfig.builder();
        loadFromClasspath(resourceName, builder);
        return builder.build();
    }

    private static void loadFromClasspath(String resourceName, StratifyConfig.Builder builder) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try (InputStream is = classLoader.getResourceAsStream(resourceName)) {
            if (is != null) {
                Properties props = new Properties();
                props.load(is);
                builder.properties(props);
                log.debug("Loaded configuration from classpath: {}", resourceName);
            }
        } catch (IOException e) {
            log.debug("Could not load configuration from classpath: {}", resourceName);
        }
    }

    private static void loadFromPath(Path path, StratifyConfig.Builder builder) {
        try (InputStream is = Files.newInputStream(path)) {
            Properties props = new Properties();
            props.load(is);
            builder.properties(props);
            log.debug("Loaded configuration from: {}", path);
        } catch (IOException e) {
            throw new StratifyException(ErrorCode.CONFIG_LOAD_FAILED,
                "Failed to load configuration from " + path, e);
        }
    }
}
