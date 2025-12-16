package dev.engineeringlab.example.text.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.example.text.common.exception.ProcessException;
import dev.engineeringlab.example.text.common.model.ProcessRequest;
import dev.engineeringlab.example.text.common.model.ProcessResult;
import dev.engineeringlab.example.text.spi.TextProcessor;
import dev.engineeringlab.stratify.registry.ProviderRegistry;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultTextServiceTest {

  private DefaultTextService service;
  private ProviderRegistry<TextProcessor> registry;

  @BeforeEach
  void setUp() {
    registry = new ProviderRegistry<>();
    registry.register("uppercase", new UpperCaseProcessor());
    registry.register("lowercase", new LowerCaseProcessor());
    service = new DefaultTextService(registry);
  }

  @Test
  void shouldProcessWithUpperCase() {
    ProcessRequest request = new ProcessRequest("hello", "uppercase");

    ProcessResult result = service.process(request);

    assertThat(result.processedText()).isEqualTo("HELLO");
    assertThat(result.originalText()).isEqualTo("hello");
    assertThat(result.processorUsed()).isEqualTo("uppercase");
  }

  @Test
  void shouldProcessWithLowerCase() {
    ProcessRequest request = new ProcessRequest("HELLO", "lowercase");

    ProcessResult result = service.process(request);

    assertThat(result.processedText()).isEqualTo("hello");
    assertThat(result.originalText()).isEqualTo("HELLO");
    assertThat(result.processorUsed()).isEqualTo("lowercase");
  }

  @Test
  void shouldThrowForUnknownProcessorType() {
    ProcessRequest request = new ProcessRequest("hello", "unknown");

    assertThatThrownBy(() -> service.process(request))
        .isInstanceOf(ProcessException.class)
        .hasMessageContaining("unknown");
  }

  @Test
  void shouldListAvailableProcessors() {
    List<String> processors = service.getAvailableProcessors();

    assertThat(processors).containsExactlyInAnyOrder("uppercase", "lowercase");
  }

  @Test
  void shouldReturnEmptyListWhenNoProcessors() {
    ProviderRegistry<TextProcessor> emptyRegistry = new ProviderRegistry<>();
    DefaultTextService emptyService = new DefaultTextService(emptyRegistry);

    List<String> processors = emptyService.getAvailableProcessors();

    assertThat(processors).isEmpty();
  }
}
