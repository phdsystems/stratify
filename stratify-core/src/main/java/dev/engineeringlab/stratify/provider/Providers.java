package dev.engineeringlab.stratify.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.StreamSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for discovering and accessing provider implementations via ServiceLoader.
 *
 * <p>Provides a facade for discovering providers registered via {@code META-INF/services}.
 * Automatically selects the highest-priority provider when multiple implementations are available.
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>{@code
 * // Get the highest-priority provider
 * TracerProvider provider = Providers.get(TracerProvider.class);
 *
 * // Get a specific provider by name
 * Optional<TracerProvider> otlp = Providers.get(TracerProvider.class, "opentelemetry");
 *
 * // Get all available providers
 * List<TracerProvider> all = Providers.getAll(TracerProvider.class);
 *
 * // Inject providers into an object's @Provider fields
 * Providers.inject(myObject);
 * }</pre>
 *
 * <h2>Priority</h2>
 *
 * <p>When multiple providers are available, the one with the highest priority is selected. Priority
 * is determined by:
 *
 * <ol>
 *   <li>The {@code priority()} method if the provider implements {@link PriorityProvider}
 *   <li>Otherwise, providers are returned in ServiceLoader discovery order
 * </ol>
 *
 * @since 0.2.0
 */
public final class Providers {

  private static final Logger log = LoggerFactory.getLogger(Providers.class);

  /** Cache of discovered providers by type. */
  private static final ConcurrentMap<Class<?>, List<?>> PROVIDER_CACHE = new ConcurrentHashMap<>();

  private Providers() {
    // Utility class
  }

  /**
   * Gets the highest-priority provider of the given type.
   *
   * @param providerType the provider interface class
   * @param <T> the provider type
   * @return the highest-priority provider
   * @throws IllegalStateException if no provider is found
   */
  public static <T> T get(Class<T> providerType) {
    List<T> providers = getAll(providerType);
    if (providers.isEmpty()) {
      throw new IllegalStateException(
          "No provider found for: "
              + providerType.getName()
              + ". Add a provider dependency to your classpath.");
    }
    return providers.get(0);
  }

  /**
   * Gets a specific provider by name.
   *
   * @param providerType the provider interface class
   * @param name the provider name
   * @param <T> the provider type
   * @return the provider if found
   */
  public static <T> Optional<T> get(Class<T> providerType, String name) {
    return getAll(providerType).stream().filter(p -> matchesName(p, name)).findFirst();
  }

  /**
   * Gets all available providers of the given type, sorted by priority (highest first).
   *
   * @param providerType the provider interface class
   * @param <T> the provider type
   * @return list of all providers, sorted by priority
   */
  @SuppressWarnings("unchecked")
  public static <T> List<T> getAll(Class<T> providerType) {
    return (List<T>)
        PROVIDER_CACHE.computeIfAbsent(
            providerType,
            type -> {
              log.debug("Discovering providers for: {}", type.getName());
              ServiceLoader<T> loader = ServiceLoader.load(providerType);
              List<T> providers =
                  StreamSupport.stream(loader.spliterator(), false)
                      .sorted(Comparator.comparingInt(Providers::getPriority).reversed())
                      .toList();
              log.debug("Found {} providers for {}", providers.size(), type.getSimpleName());
              return providers;
            });
  }

  /**
   * Injects providers into fields annotated with @Provider.
   *
   * @param target the object to inject providers into
   */
  public static void inject(Object target) {
    if (target == null) {
      return;
    }

    Class<?> clazz = target.getClass();
    for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
      if (field.isAnnotationPresent(dev.engineeringlab.stratify.annotation.Provider.class)) {
        try {
          field.setAccessible(true);
          Class<?> fieldType = field.getType();
          Object provider = get(fieldType);
          field.set(target, provider);
          log.debug(
              "Injected {} into {}.{}",
              provider.getClass().getSimpleName(),
              clazz.getSimpleName(),
              field.getName());
        } catch (Exception e) {
          log.warn("Failed to inject provider into field: {}", field.getName(), e);
        }
      }
    }
  }

  /** Clears the provider cache. Useful for testing. */
  public static void clearCache() {
    PROVIDER_CACHE.clear();
  }

  /**
   * Gets the priority of a provider.
   *
   * @param provider the provider
   * @return the priority (higher = more preferred)
   */
  private static int getPriority(Object provider) {
    if (provider instanceof PriorityProvider pp) {
      return pp.priority();
    }
    // Try reflection for getPriority() method
    try {
      var method = provider.getClass().getMethod("getPriority");
      return (int) method.invoke(provider);
    } catch (Exception e) {
      // No priority method, use default
      return 0;
    }
  }

  /**
   * Checks if a provider matches the given name.
   *
   * @param provider the provider
   * @param name the name to match
   * @return true if matches
   */
  private static boolean matchesName(Object provider, String name) {
    if (provider instanceof NamedProvider np) {
      return name.equalsIgnoreCase(np.getProviderName());
    }
    // Try reflection for getProviderName() method
    try {
      var method = provider.getClass().getMethod("getProviderName");
      String providerName = (String) method.invoke(provider);
      return name.equalsIgnoreCase(providerName);
    } catch (Exception e) {
      // No name method, check class name
      return provider.getClass().getSimpleName().toLowerCase().contains(name.toLowerCase());
    }
  }

  /** Interface for providers that declare their priority. */
  public interface PriorityProvider {
    /**
     * Returns the priority of this provider.
     *
     * @return priority (higher = more preferred)
     */
    int priority();
  }

  /** Interface for providers that declare their name. */
  public interface NamedProvider {
    /**
     * Returns the name of this provider.
     *
     * @return provider name
     */
    String getProviderName();
  }
}
