package dev.engineeringlab.stratify.registry;

import dev.engineeringlab.stratify.annotation.Provider;
import dev.engineeringlab.stratify.error.ErrorCode;
import dev.engineeringlab.stratify.error.StratifyException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Specialized registry for providers with priority-based selection.
 *
 * <p>ProviderRegistry extends the basic Registry functionality with:
 *
 * <ul>
 *   <li>Priority-based default selection
 *   <li>Automatic extraction of {@link Provider} annotation metadata
 *   <li>Health-aware provider selection
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * ProviderRegistry{@literal <}TextGenerationProvider{@literal >} registry = new ProviderRegistry{@literal <}{@literal >}();
 * registry.register(new OpenAIProvider());     // @Provider(name="openai", priority=10)
 * registry.register(new AnthropicProvider());  // @Provider(name="anthropic", priority=5)
 *
 * // Gets OpenAI (highest priority)
 * TextGenerationProvider defaultProvider = registry.getDefault().orElseThrow();
 *
 * // Gets specific provider
 * TextGenerationProvider anthropic = registry.get("anthropic").orElseThrow();
 * </pre>
 *
 * @param <P> the provider type
 */
public class ProviderRegistry<P> {

  private final Map<String, P> providers;
  private final Map<String, Integer> priorities;
  private final List<String> orderedByPriority;

  /** Creates an empty provider registry. */
  public ProviderRegistry() {
    this.providers = new ConcurrentHashMap<>();
    this.priorities = new ConcurrentHashMap<>();
    this.orderedByPriority = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Registers a provider using its {@link Provider} annotation for name and priority.
   *
   * @param provider the provider instance
   * @return this registry for chaining
   * @throws StratifyException if the provider lacks a @Provider annotation
   */
  public ProviderRegistry<P> register(P provider) {
    Provider annotation = provider.getClass().getAnnotation(Provider.class);
    if (annotation == null) {
      throw new StratifyException(
          ErrorCode.INVALID_PROVIDER_NAME,
          "Provider must have @Provider annotation: " + provider.getClass().getName());
    }
    return register(annotation.name(), provider, annotation.priority());
  }

  /**
   * Registers a provider with explicit name and default priority.
   *
   * @param name the provider name
   * @param provider the provider instance
   * @return this registry for chaining
   */
  public ProviderRegistry<P> register(String name, P provider) {
    return register(name, provider, 0);
  }

  /**
   * Registers a provider with explicit name and priority.
   *
   * @param name the provider name
   * @param provider the provider instance
   * @param priority the selection priority (higher = preferred)
   * @return this registry for chaining
   */
  public ProviderRegistry<P> register(String name, P provider, int priority) {
    Objects.requireNonNull(name, "Provider name cannot be null");
    Objects.requireNonNull(provider, "Provider cannot be null");

    if (name.isBlank()) {
      throw new StratifyException(ErrorCode.INVALID_PROVIDER_NAME, "Provider name cannot be blank");
    }

    if (providers.containsKey(name)) {
      throw new StratifyException(ErrorCode.PROVIDER_ALREADY_REGISTERED, name);
    }

    providers.put(name, provider);
    priorities.put(name, priority);
    insertByPriority(name, priority);
    return this;
  }

  private void insertByPriority(String name, int priority) {
    synchronized (orderedByPriority) {
      int insertIndex = 0;
      for (int i = 0; i < orderedByPriority.size(); i++) {
        String existing = orderedByPriority.get(i);
        if (priorities.getOrDefault(existing, 0) < priority) {
          insertIndex = i;
          break;
        }
        insertIndex = i + 1;
      }
      orderedByPriority.add(insertIndex, name);
    }
  }

  /**
   * Gets the default provider (highest priority).
   *
   * @return the default provider, or empty if registry is empty
   */
  public Optional<P> getDefault() {
    if (orderedByPriority.isEmpty()) {
      return Optional.empty();
    }
    return Optional.ofNullable(providers.get(orderedByPriority.get(0)));
  }

  /**
   * Gets the default provider, throwing if none available.
   *
   * @return the default provider
   * @throws StratifyException if registry is empty
   */
  public P getDefaultOrThrow() {
    return getDefault().orElseThrow(() -> new StratifyException(ErrorCode.REGISTRY_EMPTY));
  }

  /**
   * Gets a provider by name.
   *
   * @param name the provider name
   * @return the provider, or empty if not found
   */
  public Optional<P> get(String name) {
    return Optional.ofNullable(providers.get(name));
  }

  /**
   * Gets a provider by name, throwing if not found.
   *
   * @param name the provider name
   * @return the provider
   * @throws StratifyException if not found
   */
  public P getOrThrow(String name) {
    return get(name).orElseThrow(() -> new StratifyException(ErrorCode.PROVIDER_NOT_FOUND, name));
  }

  /**
   * Gets all providers sorted by priority (highest first).
   *
   * @return list of providers
   */
  public List<P> getAllByPriority() {
    return orderedByPriority.stream().map(providers::get).filter(Objects::nonNull).toList();
  }

  /**
   * Gets all provider names sorted by priority.
   *
   * @return list of names
   */
  public List<String> names() {
    return List.copyOf(orderedByPriority);
  }

  /**
   * Gets the priority of a provider.
   *
   * @param name the provider name
   * @return the priority, or empty if not found
   */
  public Optional<Integer> getPriority(String name) {
    return Optional.ofNullable(priorities.get(name));
  }

  /**
   * Checks if a provider exists.
   *
   * @param name the provider name
   * @return true if registered
   */
  public boolean contains(String name) {
    return providers.containsKey(name);
  }

  /**
   * Returns the number of registered providers.
   *
   * @return the count
   */
  public int size() {
    return providers.size();
  }

  /**
   * Checks if the registry is empty.
   *
   * @return true if empty
   */
  public boolean isEmpty() {
    return providers.isEmpty();
  }

  /**
   * Removes a provider.
   *
   * @param name the provider name
   * @return the removed provider, or empty if not found
   */
  public Optional<P> remove(String name) {
    P removed = providers.remove(name);
    if (removed != null) {
      priorities.remove(name);
      orderedByPriority.remove(name);
    }
    return Optional.ofNullable(removed);
  }

  /** Removes all providers. */
  public void clear() {
    providers.clear();
    priorities.clear();
    orderedByPriority.clear();
  }

  @Override
  public String toString() {
    return "ProviderRegistry{providers=" + orderedByPriority + "}";
  }
}
