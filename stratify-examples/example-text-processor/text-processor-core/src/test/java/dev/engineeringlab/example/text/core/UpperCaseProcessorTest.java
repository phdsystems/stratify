package dev.engineeringlab.example.text.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UpperCaseProcessorTest {

  private UpperCaseProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new UpperCaseProcessor();
  }

  @Test
  void shouldConvertToUpperCase() {
    String result = processor.process("hello world");

    assertThat(result).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldHandleEmptyString() {
    String result = processor.process("");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleAlreadyUpperCase() {
    String result = processor.process("HELLO");

    assertThat(result).isEqualTo("HELLO");
  }

  @Test
  void shouldHandleMixedCase() {
    String result = processor.process("HeLLo WoRLd");

    assertThat(result).isEqualTo("HELLO WORLD");
  }

  @Test
  void shouldReturnCorrectType() {
    assertThat(processor.getType()).isEqualTo("uppercase");
  }
}
