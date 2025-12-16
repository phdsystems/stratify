package dev.engineeringlab.example.text.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LowerCaseProcessorTest {

  private LowerCaseProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new LowerCaseProcessor();
  }

  @Test
  void shouldConvertToLowerCase() {
    String result = processor.process("HELLO WORLD");

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  void shouldHandleEmptyString() {
    String result = processor.process("");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleAlreadyLowerCase() {
    String result = processor.process("hello");

    assertThat(result).isEqualTo("hello");
  }

  @Test
  void shouldHandleMixedCase() {
    String result = processor.process("HeLLo WoRLd");

    assertThat(result).isEqualTo("hello world");
  }

  @Test
  void shouldReturnCorrectType() {
    assertThat(processor.getType()).isEqualTo("lowercase");
  }
}
