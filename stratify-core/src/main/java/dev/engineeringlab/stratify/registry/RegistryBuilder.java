package dev.engineeringlab.stratify.registry;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Fluent builder for creating Registry instances.
 *
 * <p>Example usage:
 *
 * <pre>
 * Registry{@literal <}Provider{@literal >} registry = RegistryBuilder.{@literal <}Provider{@literal >}create()
 *     .register("openai", new OpenAIProvider())
 *     .register("anthropic", new AnthropicProvider())
 *     .registerLazy("ollama", OllamaProvider::new)
 *     .build();
 * </pre>
 *
 * @param <P> the provider type
 */
public class RegistryBuilder<P> {

  private final List<Entry<P>> entries;

  private record Entry<P>(String name, Supplier<P> supplier) {}

  private RegistryBuilder() {
    this.entries = new ArrayList<>();
  }

  /**
   * Creates a new builder.
   *
   * @param <P> the provider type
   * @return a new builder instance
   */
  public static <P> RegistryBuilder<P> create() {
    return new RegistryBuilder<>();
  }

  /**
   * Registers a provider instance.
   *
   * @param name the provider name
   * @param provider the provider instance
   * @return this builder for chaining
   */
  public RegistryBuilder<P> register(String name, P provider) {
    entries.add(new Entry<>(name, () -> provider));
    return this;
  }

  /**
   * Registers a lazily-created provider.
   *
   * <p>The supplier is called when {@link #build()} is invoked.
   *
   * @param name the provider name
   * @param supplier the provider supplier
   * @return this builder for chaining
   */
  public RegistryBuilder<P> registerLazy(String name, Supplier<P> supplier) {
    entries.add(new Entry<>(name, supplier));
    return this;
  }

  /**
   * Conditionally registers a provider.
   *
   * @param condition whether to register
   * @param name the provider name
   * @param provider the provider instance
   * @return this builder for chaining
   */
  public RegistryBuilder<P> registerIf(boolean condition, String name, P provider) {
    if (condition) {
      register(name, provider);
    }
    return this;
  }

  /**
   * Conditionally registers a lazily-created provider.
   *
   * @param condition whether to register
   * @param name the provider name
   * @param supplier the provider supplier
   * @return this builder for chaining
   */
  public RegistryBuilder<P> registerLazyIf(boolean condition, String name, Supplier<P> supplier) {
    if (condition) {
      registerLazy(name, supplier);
    }
    return this;
  }

  /**
   * Builds the registry with all registered providers.
   *
   * @return the populated registry
   */
  public Registry<P> build() {
    Registry<P> registry = new Registry<>();
    for (Entry<P> entry : entries) {
      registry.register(entry.name(), entry.supplier().get());
    }
    return registry;
  }
}
