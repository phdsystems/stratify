package dev.engineeringlab.stratify.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ErrorCodeTest {

  @Test
  void registryErrorsShouldHave1xxCodes() {
    assertThat(ErrorCode.PROVIDER_NOT_FOUND.code()).isBetween(100, 199);
    assertThat(ErrorCode.PROVIDER_ALREADY_REGISTERED.code()).isBetween(100, 199);
    assertThat(ErrorCode.REGISTRY_EMPTY.code()).isBetween(100, 199);
    assertThat(ErrorCode.INVALID_PROVIDER_NAME.code()).isBetween(100, 199);
  }

  @Test
  void discoveryErrorsShouldHave2xxCodes() {
    assertThat(ErrorCode.DISCOVERY_FAILED.code()).isBetween(200, 299);
    assertThat(ErrorCode.INSTANTIATION_FAILED.code()).isBetween(200, 299);
    assertThat(ErrorCode.NO_DEFAULT_CONSTRUCTOR.code()).isBetween(200, 299);
  }

  @Test
  void configErrorsShouldHave3xxCodes() {
    assertThat(ErrorCode.CONFIG_NOT_FOUND.code()).isBetween(300, 399);
    assertThat(ErrorCode.CONFIG_INVALID.code()).isBetween(300, 399);
    assertThat(ErrorCode.CONFIG_LOAD_FAILED.code()).isBetween(300, 399);
  }

  @Test
  void validationErrorsShouldHave4xxCodes() {
    assertThat(ErrorCode.LAYER_VIOLATION.code()).isBetween(400, 499);
    assertThat(ErrorCode.CIRCULAR_DEPENDENCY.code()).isBetween(400, 499);
    assertThat(ErrorCode.INVALID_MODULE_STRUCTURE.code()).isBetween(400, 499);
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void allCodesShouldHaveMessage(ErrorCode code) {
    assertThat(code.message()).isNotBlank();
  }

  @ParameterizedTest
  @EnumSource(ErrorCode.class)
  void toStringShouldIncludeCodeAndMessage(ErrorCode code) {
    String str = code.toString();

    assertThat(str).contains("STRATIFY-");
    assertThat(str).contains(code.message());
  }

  @Test
  void toStringShouldPadCodeToThreeDigits() {
    assertThat(ErrorCode.PROVIDER_NOT_FOUND.toString()).startsWith("STRATIFY-100:");
    assertThat(ErrorCode.UNKNOWN_ERROR.toString()).startsWith("STRATIFY-999:");
  }
}
