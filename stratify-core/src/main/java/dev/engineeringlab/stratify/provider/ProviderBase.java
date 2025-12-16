package dev.engineeringlab.stratify.provider;

import dev.engineeringlab.stratify.annotation.Provider;

/**
 * Base interface for all Stratify providers.
 *
 * <p>Providers implementing this interface can be automatically discovered
 * and managed by the Stratify framework. Implementations should be annotated
 * with {@link Provider} to enable metadata-based discovery and selection.
 *
 * <p>Example implementation:
 * <pre>
 * {@literal @}Provider(name = "default", priority = 0)
 * public class DefaultProvider implements ProviderBase {
 *
 *     {@literal @}Override
 *     public String name() {
 *         return "default";
 *     }
 *
 *     {@literal @}Override
 *     public boolean isHealthy() {
 *         return true; // Check actual health
 *     }
 * }
 * </pre>
 */
public interface ProviderBase {

    /**
     * Returns the unique name of this provider.
     *
     * <p>This should match the name in the {@link Provider} annotation
     * if present.
     *
     * @return the provider name
     */
    String name();

    /**
     * Checks if this provider is currently healthy and ready to serve requests.
     *
     * <p>Unhealthy providers may be skipped during default provider selection.
     *
     * @return true if healthy and ready
     */
    default boolean isHealthy() {
        return true;
    }

    /**
     * Returns the priority of this provider for default selection.
     *
     * <p>Higher values indicate higher priority. If a {@link Provider}
     * annotation is present, its priority value takes precedence.
     *
     * @return the priority (default: 0)
     */
    default int priority() {
        Provider annotation = getClass().getAnnotation(Provider.class);
        return annotation != null ? annotation.priority() : 0;
    }

    /**
     * Returns a description of this provider.
     *
     * @return the description
     */
    default String description() {
        Provider annotation = getClass().getAnnotation(Provider.class);
        return annotation != null ? annotation.description() : "";
    }

    /**
     * Called when the provider is initialized.
     *
     * <p>Override to perform setup tasks like loading configuration
     * or establishing connections.
     */
    default void initialize() {
        // Default: no-op
    }

    /**
     * Called when the provider is being shut down.
     *
     * <p>Override to release resources.
     */
    default void shutdown() {
        // Default: no-op
    }
}
