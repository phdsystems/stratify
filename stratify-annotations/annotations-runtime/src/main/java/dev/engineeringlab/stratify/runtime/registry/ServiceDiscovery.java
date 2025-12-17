package dev.engineeringlab.stratify.runtime.registry;

import dev.engineeringlab.stratify.annotation.Provider;
import dev.engineeringlab.stratify.runtime.exception.ProviderException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service discovery using Java's ServiceLoader mechanism.
 *
 * <p>This class manages service discovery and lookup. Services are automatically discovered via
 * Java's {@link ServiceLoader} mechanism when first accessed. No manual registration is required.
 *
 * <h2>Automatic Discovery</h2>
 *
 * <p>Services are automatically discovered from {@code META-INF/services/} files at runtime. Simply
 * create a service file with the service interface's fully qualified name containing the
 * implementation class names:
 *
 * <pre>
 * # META-INF/services/com.example.spi.CacheProvider
 * com.example.core.RedisCacheProvider
 * com.example.core.MemcachedCacheProvider
 * </pre>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * // Get highest priority service (auto-discovers via ServiceLoader)
 * MetricsProvider provider = ServiceDiscovery.get(MetricsProvider.class);
 *
 * // Get by name
 * Optional<MetricsProvider> micrometer = ServiceDiscovery.get(MetricsProvider.class, "micrometer");
 *
 * // Get all services sorted by priority
 * List<MetricsProvider> all = ServiceDiscovery.getAll(MetricsProvider.class);
 * }</pre>
 *
 * <h2>Service Priority</h2>
 *
 * <p>When multiple services are available, the one with highest {@link Provider#priority()} is
 * selected. Default priority is 0. Suggested priorities:
 *
 * <ul>
 *   <li>20+ Production services (cloud-managed, enterprise)
 *   <li>15-19 Production services (self-hosted, standard)
 *   <li>10-14 Development services (local, lightweight)
 *   <li>5-9 Testing services (mock, in-memory)
 *   <li>0-4 No-op or fallback services
 * </ul>
 *
 * @since 1.0.0
 */
public final class ServiceDiscovery {

  private static final Logger log = LoggerFactory.getLogger(ServiceDiscovery.class);

  /** Tracks which types have been loaded via ServiceLoader. */
  private static final Set<Class<?>> serviceLoaderInitialized = ConcurrentHashMap.newKeySet();

  /** Cache of instantiated providers by interface type. */
  private static final Map<Class<?>, List<?>> providerInstances = new ConcurrentHashMap<>();

  /** Cache of single highest-priority provider by interface type. */
  private static final Map<Class<?>, Object> singleProviders = new ConcurrentHashMap<>();

  private ServiceDiscovery() {
    // Utility class
  }

  /**
   * Gets the highest priority provider for the given type.
   *
   * @param type the provider interface class
   * @param <T> the provider type
   * @return the provider instance
   * @throws ProviderException if no provider is found
   */
  @SuppressWarnings("unchecked")
  public static <T> T get(Class<T> type) {
    return (T)
        singleProviders.computeIfAbsent(
            type,
            t -> {
              List<T> all = getAll(type);
              if (all.isEmpty()) {
                throw new ProviderException(
                    type,
                    "No provider found for: "
                        + type.getName()
                        + ". Ensure META-INF/services file exists with implementation.");
              }
              return all.get(0); // Highest priority (list is sorted descending)
            });
  }

  /**
   * Gets a provider by name.
   *
   * @param type the provider interface class
   * @param name the provider name (from {@link Provider#name()})
   * @param <T> the provider type
   * @return the provider if found
   */
  public static <T> Optional<T> get(Class<T> type, String name) {
    return getAll(type).stream().filter(p -> name.equals(getProviderName(p))).findFirst();
  }

  /**
   * Gets all providers for the given type, sorted by priority (highest first).
   *
   * <p>Providers are automatically discovered via {@link ServiceLoader}. The discovery happens
   * lazily on first access and results are cached.
   *
   * @param type the provider interface class
   * @param <T> the provider type
   * @return list of providers sorted by priority descending
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> getAll(Class<T> type) {
    return (List<T>) providerInstances.computeIfAbsent(type, t -> loadFromServiceLoader(type));
  }

  /**
   * Checks if a provider exists for the given type.
   *
   * <p>This will trigger ServiceLoader discovery if not already done.
   *
   * @param type the provider interface class
   * @return true if at least one provider is available
   */
  public static boolean exists(Class<?> type) {
    return !getAll(type).isEmpty();
  }

  /**
   * Clears all caches and allows re-discovery of providers.
   *
   * <p>Primarily useful for testing to reset state between tests.
   */
  public static void reset() {
    serviceLoaderInitialized.clear();
    providerInstances.clear();
    singleProviders.clear();
  }

  /**
   * Gets all discovered provider interfaces.
   *
   * <p>Note: This only returns interfaces that have already been queried via {@link #get} or {@link
   * #getAll}. ServiceLoader discovery is lazy.
   *
   * @return set of discovered interface types
   */
  public static Set<Class<?>> getRegisteredInterfaces() {
    return Set.copyOf(providerInstances.keySet());
  }

  /**
   * Loads all provider implementations for a type using ServiceLoader.
   *
   * @param type the provider interface class
   * @param <T> the provider type
   * @return list of providers sorted by priority descending
   */
  private static <T> List<T> loadFromServiceLoader(Class<T> type) {
    if (serviceLoaderInitialized.contains(type)) {
      log.debug("Reloading providers for {}", type.getName());
    } else {
      log.debug("Discovering providers for {} via ServiceLoader", type.getName());
      serviceLoaderInitialized.add(type);
    }

    List<T> providers = new ArrayList<>();
    ServiceLoader<T> loader = ServiceLoader.load(type);

    java.util.Iterator<T> iterator = loader.iterator();
    while (iterator.hasNext()) {
      try {
        T provider = iterator.next();
        if (log.isDebugEnabled()) {
          Provider ann = provider.getClass().getAnnotation(Provider.class);
          String name = ann != null ? ann.name() : "unknown";
          int priority = getPriority(provider);
          log.debug(
              "Discovered provider: {} (name={}, priority={}) for {}",
              provider.getClass().getName(),
              name,
              priority,
              type.getName());
        }
        providers.add(provider);
      } catch (java.util.ServiceConfigurationError e) {
        log.debug(
            "Skipping provider that failed to instantiate for {}: {}",
            type.getName(),
            e.getMessage());
      }
    }

    if (providers.isEmpty()) {
      log.debug("No providers found for {} in META-INF/services", type.getName());
    }

    // Sort by priority (highest first)
    providers.sort(Comparator.comparingInt(ServiceDiscovery::getPriority).reversed());

    return providers;
  }

  /** Gets the priority of a provider from its @Provider annotation. */
  private static int getPriority(Object provider) {
    Provider ann = provider.getClass().getAnnotation(Provider.class);
    if (ann != null) {
      return ann.priority();
    }
    // Check for meta-annotated provider annotations
    for (Annotation annotation : provider.getClass().getAnnotations()) {
      Provider metaProvider = annotation.annotationType().getAnnotation(Provider.class);
      if (metaProvider != null) {
        return metaProvider.priority();
      }
    }
    return 0;
  }

  /** Gets the name of a provider from its @Provider annotation. */
  private static String getProviderName(Object provider) {
    Provider ann = provider.getClass().getAnnotation(Provider.class);
    if (ann != null) {
      return ann.name();
    }
    // Check for meta-annotated provider annotations
    for (Annotation annotation : provider.getClass().getAnnotations()) {
      Provider metaProvider = annotation.annotationType().getAnnotation(Provider.class);
      if (metaProvider != null) {
        return metaProvider.name();
      }
    }
    return "";
  }
}
