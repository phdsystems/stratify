package dev.engineeringlab.stratify.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.stratify.error.ErrorCode;
import dev.engineeringlab.stratify.error.StratifyException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistryTest {

  private Registry<String> registry;

  @BeforeEach
  void setUp() {
    registry = new Registry<>();
  }

  @Test
  void shouldStartEmpty() {
    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.size()).isZero();
  }

  @Test
  void shouldRegisterProvider() {
    registry.register("test", "value");

    assertThat(registry.isEmpty()).isFalse();
    assertThat(registry.size()).isEqualTo(1);
    assertThat(registry.contains("test")).isTrue();
  }

  @Test
  void shouldGetRegisteredProvider() {
    registry.register("test", "value");

    Optional<String> result = registry.get("test");

    assertThat(result).isPresent().contains("value");
  }

  @Test
  void shouldReturnEmptyForUnknownProvider() {
    registry.register("test", "value");

    assertThat(registry.get("unknown")).isEmpty();
  }

  @Test
  void shouldGetOrThrowForExistingProvider() {
    registry.register("test", "value");

    String result = registry.getOrThrow("test");

    assertThat(result).isEqualTo("value");
  }

  @Test
  void shouldThrowForUnknownProviderOnGetOrThrow() {
    assertThatThrownBy(() -> registry.getOrThrow("unknown"))
        .isInstanceOf(StratifyException.class)
        .satisfies(
            ex -> {
              StratifyException se = (StratifyException) ex;
              assertThat(se.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_FOUND);
            });
  }

  @Test
  void shouldThrowWhenRegisteringDuplicateName() {
    registry.register("test", "value1");

    assertThatThrownBy(() -> registry.register("test", "value2"))
        .isInstanceOf(StratifyException.class)
        .satisfies(
            ex -> {
              StratifyException se = (StratifyException) ex;
              assertThat(se.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_ALREADY_REGISTERED);
            });
  }

  @Test
  void shouldRejectNullName() {
    assertThatThrownBy(() -> registry.register(null, "value"))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectNullProvider() {
    assertThatThrownBy(() -> registry.register("test", null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void shouldRejectBlankName() {
    assertThatThrownBy(() -> registry.register("   ", "value"))
        .isInstanceOf(StratifyException.class)
        .satisfies(
            ex -> {
              StratifyException se = (StratifyException) ex;
              assertThat(se.getErrorCode()).isEqualTo(ErrorCode.INVALID_PROVIDER_NAME);
            });
  }

  @Test
  void shouldRegisterOrReplaceNewProvider() {
    Optional<String> previous = registry.registerOrReplace("test", "value");

    assertThat(previous).isEmpty();
    assertThat(registry.get("test")).contains("value");
  }

  @Test
  void shouldRegisterOrReplaceExistingProvider() {
    registry.register("test", "value1");

    Optional<String> previous = registry.registerOrReplace("test", "value2");

    assertThat(previous).contains("value1");
    assertThat(registry.get("test")).contains("value2");
  }

  @Test
  void shouldRemoveProvider() {
    registry.register("test", "value");

    Optional<String> removed = registry.remove("test");

    assertThat(removed).contains("value");
    assertThat(registry.contains("test")).isFalse();
  }

  @Test
  void shouldReturnEmptyWhenRemovingUnknownProvider() {
    assertThat(registry.remove("unknown")).isEmpty();
  }

  @Test
  void shouldPreserveRegistrationOrder() {
    registry.register("first", "1");
    registry.register("second", "2");
    registry.register("third", "3");

    List<String> names = registry.names();
    List<String> providers = registry.getAll();

    assertThat(names).containsExactly("first", "second", "third");
    assertThat(providers).containsExactly("1", "2", "3");
  }

  @Test
  void shouldFindProvidersByPredicate() {
    registry.register("starts-with-a", "apple");
    registry.register("starts-with-b", "banana");
    registry.register("starts-with-a-2", "avocado");

    List<String> found = registry.find(s -> s.startsWith("a"));

    assertThat(found).containsExactlyInAnyOrder("apple", "avocado");
  }

  @Test
  void shouldFindFirstProviderByPredicate() {
    registry.register("first", "apple");
    registry.register("second", "banana");

    Optional<String> found = registry.findFirst(s -> s.length() > 4);

    assertThat(found).isPresent().contains("apple");
  }

  @Test
  void shouldReturnEmptyWhenNoProviderMatchesPredicate() {
    registry.register("test", "value");

    assertThat(registry.findFirst(s -> s.startsWith("x"))).isEmpty();
    assertThat(registry.find(s -> s.startsWith("x"))).isEmpty();
  }

  @Test
  void shouldStreamProviders() {
    registry.register("a", "1");
    registry.register("b", "2");

    long count = registry.stream().count();

    assertThat(count).isEqualTo(2);
  }

  @Test
  void shouldClearAllProviders() {
    registry.register("a", "1");
    registry.register("b", "2");

    registry.clear();

    assertThat(registry.isEmpty()).isTrue();
    assertThat(registry.size()).isZero();
    assertThat(registry.names()).isEmpty();
  }

  @Test
  void shouldHaveDescriptiveToString() {
    registry.register("test", "value");

    String str = registry.toString();

    assertThat(str).contains("test");
  }
}
