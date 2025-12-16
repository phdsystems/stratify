package dev.engineeringlab.stratify.registry;

import dev.engineeringlab.stratify.error.ErrorCode;
import dev.engineeringlab.stratify.error.StratifyException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Generic registry for managing named providers.
 *
 * <p>A Registry provides thread-safe storage and retrieval of provider instances by name, with
 * support for priority-based selection and filtering.
 *
 * <p>Example usage:
 *
 * <pre>
 * Registry{@literal <}TextGenerationProvider{@literal >} registry = new Registry{@literal <}{@literal >}();
 * registry.register("openai", new OpenAIProvider());
 * registry.register("anthropic", new AnthropicProvider());
 *
 * TextGenerationProvider provider = registry.get("openai")
 *     .orElseThrow(() -&gt; new RuntimeException("Provider not found"));
 * </pre>
 *
 * @param <P> the provider type
 */
public class Registry<P> {

  private final Map<String, P> providers;
  private final List<String> ordered;

  /** Creates an empty registry. */
  public Registry() {
    this.providers = new ConcurrentHashMap<>();
    this.ordered = Collections.synchronizedList(new ArrayList<>());
  }

  /**
   * Registers a provider with the given name.
   *
   * @param name the unique provider name
   * @param provider the provider instance
   * @return this registry for chaining
   * @throws StratifyException if a provider with this name already exists
   */
  public Registry<P> register(String name, P provider) {
    Objects.requireNonNull(name, "Provider name cannot be null");
    Objects.requireNonNull(provider, "Provider cannot be null");

    if (name.isBlank()) {
      throw new StratifyException(ErrorCode.INVALID_PROVIDER_NAME, "Provider name cannot be blank");
    }

    if (providers.containsKey(name)) {
      throw new StratifyException(ErrorCode.PROVIDER_ALREADY_REGISTERED, name);
    }

    providers.put(name, provider);
    ordered.add(name);
    return this;
  }

  /**
   * Registers a provider, replacing any existing provider with the same name.
   *
   * @param name the provider name
   * @param provider the provider instance
   * @return the previously registered provider, or empty if none
   */
  public Optional<P> registerOrReplace(String name, P provider) {
    Objects.requireNonNull(name, "Provider name cannot be null");
    Objects.requireNonNull(provider, "Provider cannot be null");

    P previous = providers.put(name, provider);
    if (previous == null) {
      ordered.add(name);
    }
    return Optional.ofNullable(previous);
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
   * @throws StratifyException if no provider with this name exists
   */
  public P getOrThrow(String name) {
    return get(name).orElseThrow(() -> new StratifyException(ErrorCode.PROVIDER_NOT_FOUND, name));
  }

  /**
   * Finds providers matching a predicate.
   *
   * @param predicate the filter predicate
   * @return list of matching providers
   */
  public List<P> find(Predicate<P> predicate) {
    return providers.values().stream().filter(predicate).toList();
  }

  /**
   * Finds the first provider matching a predicate.
   *
   * @param predicate the filter predicate
   * @return the first matching provider, or empty
   */
  public Optional<P> findFirst(Predicate<P> predicate) {
    return providers.values().stream().filter(predicate).findFirst();
  }

  /**
   * Gets all providers in registration order.
   *
   * @return unmodifiable list of providers
   */
  public List<P> getAll() {
    return ordered.stream().map(providers::get).filter(Objects::nonNull).toList();
  }

  /**
   * Gets all provider names in registration order.
   *
   * @return unmodifiable list of names
   */
  public List<String> names() {
    return List.copyOf(ordered);
  }

  /**
   * Returns a stream of all providers.
   *
   * @return stream of providers
   */
  public Stream<P> stream() {
    return getAll().stream();
  }

  /**
   * Removes a provider by name.
   *
   * @param name the provider name
   * @return the removed provider, or empty if not found
   */
  public Optional<P> remove(String name) {
    P removed = providers.remove(name);
    if (removed != null) {
      ordered.remove(name);
    }
    return Optional.ofNullable(removed);
  }

  /**
   * Checks if a provider with the given name exists.
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
   * @return true if no providers registered
   */
  public boolean isEmpty() {
    return providers.isEmpty();
  }

  /** Removes all providers from the registry. */
  public void clear() {
    providers.clear();
    ordered.clear();
  }

  @Override
  public String toString() {
    return "Registry{providers=" + ordered + "}";
  }
}
