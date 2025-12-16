package dev.engineeringlab.stratify.registry;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RegistryBuilderTest {

  @Test
  void shouldBuildEmptyRegistry() {
    Registry<String> registry = RegistryBuilder.<String>create().build();

    assertThat(registry.isEmpty()).isTrue();
  }

  @Test
  void shouldRegisterProviders() {
    Registry<String> registry =
        RegistryBuilder.<String>create()
            .register("first", "value1")
            .register("second", "value2")
            .build();

    assertThat(registry.size()).isEqualTo(2);
    assertThat(registry.get("first")).contains("value1");
    assertThat(registry.get("second")).contains("value2");
  }

  @Test
  void shouldRegisterLazyProviders() {
    AtomicInteger callCount = new AtomicInteger(0);

    RegistryBuilder<String> builder =
        RegistryBuilder.<String>create()
            .registerLazy(
                "lazy",
                () -> {
                  callCount.incrementAndGet();
                  return "lazyValue";
                });

    assertThat(callCount.get()).isZero();

    Registry<String> registry = builder.build();

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(registry.get("lazy")).contains("lazyValue");
  }

  @Test
  void shouldConditionallyRegisterProviders() {
    Registry<String> registry =
        RegistryBuilder.<String>create()
            .registerIf(true, "included", "value1")
            .registerIf(false, "excluded", "value2")
            .build();

    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.contains("included")).isTrue();
    assertThat(registry.contains("excluded")).isFalse();
  }

  @Test
  void shouldConditionallyRegisterLazyProviders() {
    AtomicInteger callCount = new AtomicInteger(0);

    Registry<String> registry =
        RegistryBuilder.<String>create()
            .registerLazyIf(
                true,
                "included",
                () -> {
                  callCount.incrementAndGet();
                  return "value1";
                })
            .registerLazyIf(
                false,
                "excluded",
                () -> {
                  callCount.incrementAndGet();
                  return "value2";
                })
            .build();

    assertThat(callCount.get()).isEqualTo(1);
    assertThat(registry.contains("included")).isTrue();
    assertThat(registry.contains("excluded")).isFalse();
  }

  @Test
  void shouldPreserveRegistrationOrder() {
    Registry<String> registry =
        RegistryBuilder.<String>create()
            .register("third", "3")
            .register("first", "1")
            .register("second", "2")
            .build();

    assertThat(registry.names()).containsExactly("third", "first", "second");
  }

  @Test
  void shouldSupportFluentChaining() {
    Registry<Integer> registry =
        RegistryBuilder.<Integer>create()
            .register("one", 1)
            .register("two", 2)
            .registerIf(true, "three", 3)
            .registerLazy("four", () -> 4)
            .build();

    assertThat(registry.size()).isEqualTo(4);
  }
}
