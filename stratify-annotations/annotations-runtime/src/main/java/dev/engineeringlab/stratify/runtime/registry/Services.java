package dev.engineeringlab.stratify.runtime.registry;

import java.util.List;
import java.util.Optional;

/**
 * Facade for service discovery.
 *
 * <p>Provides access to service implementations registered via ServiceLoader. Implementations are
 * automatically discovered from {@code META-INF/services/} files.
 *
 * <h2>Service Lookup</h2>
 *
 * <pre>{@code
 * // Get highest priority implementation
 * Cache cache = Services.get(Cache.class);
 *
 * // Get by name
 * Optional<Cache> redis = Services.get(Cache.class, "redis");
 *
 * // Get all implementations sorted by priority
 * List<Cache> all = Services.getAll(Cache.class);
 * }</pre>
 *
 * @since 1.0.0
 */
public final class Services {

  private Services() {
    // Utility class
  }

  /**
   * Gets the highest-priority implementation for the given type.
   *
   * @param type the interface class
   * @param <T> the type
   * @return the instance
   * @throws dev.engineeringlab.stratify.runtime.exception.ProviderException if no implementation is
   *     found
   */
  public static <T> T get(Class<T> type) {
    return ServiceDiscovery.get(type);
  }

  /**
   * Gets an implementation by name.
   *
   * @param type the interface class
   * @param name the implementation name
   * @param <T> the type
   * @return the instance if found
   */
  public static <T> Optional<T> get(Class<T> type, String name) {
    return ServiceDiscovery.get(type, name);
  }

  /**
   * Gets all implementations for the given type, sorted by priority.
   *
   * @param type the interface class
   * @param <T> the type
   * @return list of instances
   */
  public static <T> List<T> getAll(Class<T> type) {
    return ServiceDiscovery.getAll(type);
  }

  /**
   * Checks if an implementation exists for the given type.
   *
   * @param type the interface class
   * @return true if at least one implementation is registered
   */
  public static boolean exists(Class<?> type) {
    return ServiceDiscovery.exists(type);
  }

  /**
   * Resets the service registry.
   *
   * <p>Primarily useful for testing to reset state between tests.
   */
  public static void reset() {
    ServiceDiscovery.reset();
  }
}
