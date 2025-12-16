package dev.engineeringlab.stratify.plugin.validator;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleLayer;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult.Severity;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModuleStructureValidatorTest {

  private ModuleStructureValidator validator;

  @BeforeEach
  void setUp() {
    validator = new ModuleStructureValidator();
  }

  @Test
  void shouldPassWithAllModules() {
    List<ModuleInfo> modules =
        List.of(
            createModule("my-common", ModuleLayer.COMMON),
            createModule("my-spi", ModuleLayer.SPI),
            createModule("my-api", ModuleLayer.API),
            createModule("my-core", ModuleLayer.CORE),
            createModule("my", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results).isEmpty();
  }

  @Test
  void shouldErrorWhenApiMissing() {
    List<ModuleInfo> modules =
        List.of(createModule("my-core", ModuleLayer.CORE), createModule("my", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.ruleId()).isEqualTo("MS-001");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("API");
            });
  }

  @Test
  void shouldErrorWhenCoreMissing() {
    List<ModuleInfo> modules =
        List.of(createModule("my-api", ModuleLayer.API), createModule("my", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.ruleId()).isEqualTo("MS-002");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("Core");
            });
  }

  @Test
  void shouldErrorWhenFacadeMissing() {
    List<ModuleInfo> modules =
        List.of(createModule("my-api", ModuleLayer.API), createModule("my-core", ModuleLayer.CORE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.ruleId()).isEqualTo("MS-003");
              assertThat(r.severity()).isEqualTo(Severity.ERROR);
              assertThat(r.message()).contains("Facade");
            });
  }

  @Test
  void shouldWarnWhenCommonMissing() {
    List<ModuleInfo> modules =
        List.of(
            createModule("my-api", ModuleLayer.API),
            createModule("my-core", ModuleLayer.CORE),
            createModule("my-spi", ModuleLayer.SPI),
            createModule("my", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.ruleId()).isEqualTo("MS-004");
              assertThat(r.severity()).isEqualTo(Severity.WARNING);
              assertThat(r.message()).contains("Common");
            });
  }

  @Test
  void shouldWarnWhenSpiMissing() {
    List<ModuleInfo> modules =
        List.of(
            createModule("my-api", ModuleLayer.API),
            createModule("my-core", ModuleLayer.CORE),
            createModule("my-common", ModuleLayer.COMMON),
            createModule("my", ModuleLayer.FACADE));

    List<ValidationResult> results = validator.validate(modules);

    assertThat(results)
        .anySatisfy(
            r -> {
              assertThat(r.ruleId()).isEqualTo("MS-005");
              assertThat(r.severity()).isEqualTo(Severity.WARNING);
              assertThat(r.message()).contains("SPI");
            });
  }

  @Test
  void shouldReportAllMissingModules() {
    List<ValidationResult> results = validator.validate(List.of());

    assertThat(results).hasSize(5);
    assertThat(results)
        .extracting(ValidationResult::ruleId)
        .containsExactlyInAnyOrder("MS-001", "MS-002", "MS-003", "MS-004", "MS-005");
  }

  private ModuleInfo createModule(String artifactId, ModuleLayer layer) {
    return new ModuleInfo(artifactId, "com.example", layer, Path.of("/tmp"), List.of(), null);
  }
}
