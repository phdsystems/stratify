package dev.engineeringlab.stratify.plugin.validator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.stratify.plugin.validator.ValidationResult.Severity;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

  @Test
  void shouldCreateWithAllFields() {
    ValidationResult result =
        new ValidationResult("MS-001", Severity.ERROR, "Test message", "/path/to/module");

    assertThat(result.ruleId()).isEqualTo("MS-001");
    assertThat(result.severity()).isEqualTo(Severity.ERROR);
    assertThat(result.message()).isEqualTo("Test message");
    assertThat(result.location()).isEqualTo("/path/to/module");
  }

  @Test
  void shouldCreateWithNullLocation() {
    ValidationResult result =
        new ValidationResult("MS-001", Severity.WARNING, "Test message", null);

    assertThat(result.location()).isNull();
  }

  @Test
  void shouldCreateWithOfFactoryMethod() {
    ValidationResult result = ValidationResult.of("NC-002", Severity.INFO, "Info message");

    assertThat(result.ruleId()).isEqualTo("NC-002");
    assertThat(result.severity()).isEqualTo(Severity.INFO);
    assertThat(result.message()).isEqualTo("Info message");
    assertThat(result.location()).isNull();
  }

  @Test
  void shouldCreateErrorResult() {
    ValidationResult result = ValidationResult.error("DP-001", "Dependency violation", "my-module");

    assertThat(result.severity()).isEqualTo(Severity.ERROR);
    assertThat(result.ruleId()).isEqualTo("DP-001");
    assertThat(result.message()).isEqualTo("Dependency violation");
    assertThat(result.location()).isEqualTo("my-module");
  }

  @Test
  void shouldCreateWarningResult() {
    ValidationResult result = ValidationResult.warning("LV-002", "Layer warning", "api-module");

    assertThat(result.severity()).isEqualTo(Severity.WARNING);
    assertThat(result.ruleId()).isEqualTo("LV-002");
  }

  @Test
  void shouldCreateInfoResult() {
    ValidationResult result = ValidationResult.info("JP-001", "JPMS info", "core-module");

    assertThat(result.severity()).isEqualTo(Severity.INFO);
    assertThat(result.ruleId()).isEqualTo("JP-001");
  }

  @Test
  void shouldRejectNullRuleId() {
    assertThatThrownBy(() -> new ValidationResult(null, Severity.ERROR, "message", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ruleId");
  }

  @Test
  void shouldRejectBlankRuleId() {
    assertThatThrownBy(() -> new ValidationResult("   ", Severity.ERROR, "message", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ruleId");
  }

  @Test
  void shouldRejectNullSeverity() {
    assertThatThrownBy(() -> new ValidationResult("MS-001", null, "message", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("severity");
  }

  @Test
  void shouldRejectNullMessage() {
    assertThatThrownBy(() -> new ValidationResult("MS-001", Severity.ERROR, null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  void shouldRejectBlankMessage() {
    assertThatThrownBy(() -> new ValidationResult("MS-001", Severity.ERROR, "   ", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("message");
  }

  @Test
  void severityShouldHaveThreeLevels() {
    assertThat(Severity.values()).containsExactly(Severity.ERROR, Severity.WARNING, Severity.INFO);
  }
}
