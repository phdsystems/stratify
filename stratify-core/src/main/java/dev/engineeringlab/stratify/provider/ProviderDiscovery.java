package dev.engineeringlab.stratify.provider;

import dev.engineeringlab.stratify.annotation.Provider;
import dev.engineeringlab.stratify.error.ErrorCode;
import dev.engineeringlab.stratify.error.StratifyException;
import dev.engineeringlab.stratify.registry.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Discovers and loads providers using ServiceLoader and annotation scanning.
 *
 * <p>ProviderDiscovery supports two discovery mechanisms:
 * <ul>
 *   <li>ServiceLoader-based discovery (standard Java SPI)</li>
 *   <li>Annotation-based filtering ({@link Provider} annotation)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * // Discover all implementations of TextGenerationProvider
 * List{@literal <}TextGenerationProvider{@literal >} providers =
 *     ProviderDiscovery.discover(TextGenerationProvider.class);
 *
 * // Load into a registry
 * ProviderRegistry{@literal <}TextGenerationProvider{@literal >} registry =
 *     ProviderDiscovery.loadRegistry(TextGenerationProvider.class);
 * </pre>
 */
public final class ProviderDiscovery {

    private static final Logger log = LoggerFactory.getLogger(ProviderDiscovery.class);

    private ProviderDiscovery() {
        // Utility class
    }

    /**
     * Discovers all providers of the given type using ServiceLoader.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @return list of discovered providers
     */
    public static <P> List<P> discover(Class<P> providerType) {
        return discover(providerType, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Discovers all providers using the specified class loader.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @param classLoader the class loader to use
     * @return list of discovered providers
     */
    public static <P> List<P> discover(Class<P> providerType, ClassLoader classLoader) {
        Objects.requireNonNull(providerType, "Provider type cannot be null");

        ServiceLoader<P> loader = ServiceLoader.load(providerType, classLoader);
        List<P> providers = new ArrayList<>();

        for (P provider : loader) {
            try {
                providers.add(provider);
                log.debug("Discovered provider: {} ({})",
                    getProviderName(provider), provider.getClass().getName());
            } catch (ServiceConfigurationError e) {
                log.warn("Failed to load provider of type {}: {}",
                    providerType.getName(), e.getMessage());
            }
        }

        log.info("Discovered {} providers of type {}", providers.size(), providerType.getName());
        return providers;
    }

    /**
     * Discovers providers and loads them into a ProviderRegistry.
     *
     * <p>Only providers with {@link Provider} annotation are included.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @return populated registry
     */
    public static <P> ProviderRegistry<P> loadRegistry(Class<P> providerType) {
        return loadRegistry(providerType, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Discovers providers and loads them into a registry using the specified class loader.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @param classLoader the class loader to use
     * @return populated registry
     */
    public static <P> ProviderRegistry<P> loadRegistry(Class<P> providerType, ClassLoader classLoader) {
        List<P> discovered = discover(providerType, classLoader);
        ProviderRegistry<P> registry = new ProviderRegistry<>();

        for (P provider : discovered) {
            Provider annotation = provider.getClass().getAnnotation(Provider.class);
            if (annotation != null) {
                try {
                    registry.register(annotation.name(), provider, annotation.priority());
                    log.debug("Registered provider: {} with priority {}",
                        annotation.name(), annotation.priority());
                } catch (StratifyException e) {
                    log.warn("Failed to register provider {}: {}",
                        annotation.name(), e.getMessage());
                }
            } else {
                log.debug("Skipping provider without @Provider annotation: {}",
                    provider.getClass().getName());
            }
        }

        return registry;
    }

    /**
     * Gets the first available provider of the given type.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @return the first provider, or empty if none found
     */
    public static <P> Optional<P> findFirst(Class<P> providerType) {
        List<P> providers = discover(providerType);
        return providers.isEmpty() ? Optional.empty() : Optional.of(providers.get(0));
    }

    /**
     * Gets a specific provider by name.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @param name the provider name
     * @return the provider, or empty if not found
     */
    public static <P> Optional<P> findByName(Class<P> providerType, String name) {
        return discover(providerType).stream()
            .filter(p -> name.equals(getProviderName(p)))
            .findFirst();
    }

    /**
     * Gets the highest priority provider.
     *
     * @param <P> the provider type
     * @param providerType the provider interface or class
     * @return the highest priority provider, or empty if none found
     */
    public static <P> Optional<P> findBestProvider(Class<P> providerType) {
        return discover(providerType).stream()
            .filter(p -> p.getClass().isAnnotationPresent(Provider.class))
            .max(Comparator.comparingInt(p ->
                p.getClass().getAnnotation(Provider.class).priority()));
    }

    private static String getProviderName(Object provider) {
        Provider annotation = provider.getClass().getAnnotation(Provider.class);
        if (annotation != null) {
            return annotation.name();
        }
        if (provider instanceof ProviderBase base) {
            return base.name();
        }
        return provider.getClass().getSimpleName();
    }
}
