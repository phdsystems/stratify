package dev.engineeringlab.stratify.error;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class StratifyExceptionTest {

  @Test
  void shouldCreateWithErrorCodeOnly() {
    StratifyException ex = new StratifyException(ErrorCode.PROVIDER_NOT_FOUND);

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_FOUND);
    assertThat(ex.getCode()).isEqualTo(100);
    assertThat(ex.getMessage()).contains("STRATIFY-100");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void shouldCreateWithErrorCodeAndDetail() {
    StratifyException ex = new StratifyException(ErrorCode.PROVIDER_NOT_FOUND, "myProvider");

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.PROVIDER_NOT_FOUND);
    assertThat(ex.getMessage()).contains("STRATIFY-100");
    assertThat(ex.getMessage()).contains("myProvider");
    assertThat(ex.getCause()).isNull();
  }

  @Test
  void shouldCreateWithErrorCodeAndCause() {
    RuntimeException cause = new RuntimeException("root cause");
    StratifyException ex = new StratifyException(ErrorCode.DISCOVERY_FAILED, cause);

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.DISCOVERY_FAILED);
    assertThat(ex.getMessage()).contains("STRATIFY-200");
    assertThat(ex.getCause()).isEqualTo(cause);
  }

  @Test
  void shouldCreateWithAllDetails() {
    RuntimeException cause = new RuntimeException("root cause");
    StratifyException ex =
        new StratifyException(ErrorCode.CONFIG_LOAD_FAILED, "config.properties", cause);

    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CONFIG_LOAD_FAILED);
    assertThat(ex.getCode()).isEqualTo(302);
    assertThat(ex.getMessage()).contains("STRATIFY-302");
    assertThat(ex.getMessage()).contains("config.properties");
    assertThat(ex.getCause()).isEqualTo(cause);
  }

  @Test
  void shouldBeRuntimeException() {
    StratifyException ex = new StratifyException(ErrorCode.UNKNOWN_ERROR);

    assertThat(ex).isInstanceOf(RuntimeException.class);
  }

  @Test
  void shouldIncludeErrorCodeInMessage() {
    StratifyException ex = new StratifyException(ErrorCode.LAYER_VIOLATION, "common -> facade");

    assertThat(ex.getMessage()).contains("STRATIFY-400");
    assertThat(ex.getMessage()).contains("Layer dependency violation");
    assertThat(ex.getMessage()).contains("common -> facade");
  }
}
