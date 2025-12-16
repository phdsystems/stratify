package dev.engineeringlab.stratify.plugin.validator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleLayer;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult.Severity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NamingValidatorTest {

  private NamingValidator validator;

  @BeforeEach
  void setUp() {
    validator = new NamingValidator();
  }

  @Test
  void shouldPassForCorrectlyNamedModules() {
    List<ModuleInfo> modules =
        List.of(
            createModule("my-common", ModuleLayer.COMMON),
            createModule("my-spi", ModuleLayer.SPI),
            createModule("my-api", ModuleLayer.API),
            createModule("my-core", ModuleLayer.CORE),
            createModule("my-lib", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results).isEmpty();
  }

  @Test
  void shouldErrorWhenFacadeHasFacadeSuffix() {
    List<ModuleInfo> modules = List.of(createModule("my-facade", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.ruleId()).isEqualTo("NC-001");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("should NOT have -facade suffix");
            });
  }

  @Test
  void shouldErrorWhenApiLacksSuffix() {
    List<ModuleInfo> modules = List.of(createModule("my-module", ModuleLayer.API));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.ruleId()).isEqualTo("NC-002");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("must have -api suffix");
            });
  }

  @Test
  void shouldErrorWhenCoreLacksSuffix() {
    List<ModuleInfo> modules = List.of(createModule("my-module", ModuleLayer.CORE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.ruleId()).isEqualTo("NC-003");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("must have -core suffix");
            });
  }

  @Test
  void shouldErrorWhenCommonLacksSuffix() {
    List<ModuleInfo> modules = List.of(createModule("my-module", ModuleLayer.COMMON));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.ruleId()).isEqualTo("NC-004");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("must have -common suffix");
            });
  }

  @Test
  void shouldErrorWhenSpiLacksSuffix() {
    List<ModuleInfo> modules = List.of(createModule("my-module", ModuleLayer.SPI));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .hasSize(1)
        .first()
        .satisfies(
            r -> {
              assertThat(r.ruleId()).isEqualTo("NC-005");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("must have -spi suffix");
            });
  }

  @Test
  void shouldValidateMultipleModules() {
    List<ModuleInfo> modules =
        List.of(
            createModule("wrong-api-name", ModuleLayer.API),
            createModule("wrong-core-name", ModuleLayer.CORE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results).hasSize(2);
    assertThat(results)
        .extracting(ValidationResult::ruleId)
        .containsExactlyInAnyOrder("NC-002", "NC-003");
  }

  @Test
  void shouldHandleEmptyModuleList() {
    List<ValidationResult> results = validator.validate(List.of());

    assertThat(results).isEmpty();
  }

  private ModuleInfo createModule(String artifactId, ModuleLayer layer) {
    return new ModuleInfo(artifactId, "com.example", layer, Path.of("/tmp"), List.of(), null);
  }
}
