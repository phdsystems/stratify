package dev.engineeringlab.stratify.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LayerRulesTest {

  private static final String BASE_PACKAGE = "dev.engineeringlab.stratify.archunit.testfixtures";
  private static JavaClasses classes;

  @BeforeAll
  static void setUp() {
    classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
  }

  @Test
  void commonLayerIsolation_shouldPassForValidFixtures() {
    ArchRule rule = LayerRules.commonLayerIsolation(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void commonLayerIsolation_shouldHaveDescriptiveMessage() {
    ArchRule rule = LayerRules.commonLayerIsolation(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("L1 Common layer");
  }

  @Test
  void spiLayerDependsOnlyOnCommon_shouldPassForValidFixtures() {
    ArchRule rule = LayerRules.spiLayerDependsOnlyOnCommon(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void spiLayerDependsOnlyOnCommon_shouldHaveDescriptiveMessage() {
    ArchRule rule = LayerRules.spiLayerDependsOnlyOnCommon(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("L2 SPI layer");
  }

  @Test
  void apiLayerDependsOnlyOnCommonAndSpi_shouldPassForValidFixtures() {
    ArchRule rule = LayerRules.apiLayerDependsOnlyOnCommonAndSpi(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void apiLayerDependsOnlyOnCommonAndSpi_shouldHaveDescriptiveMessage() {
    ArchRule rule = LayerRules.apiLayerDependsOnlyOnCommonAndSpi(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("L3 API layer");
  }

  @Test
  void coreLayerDoesNotDependOnFacade_shouldPassForValidFixtures() {
    ArchRule rule = LayerRules.coreLayerDoesNotDependOnFacade(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void coreLayerDoesNotDependOnFacade_shouldHaveDescriptiveMessage() {
    ArchRule rule = LayerRules.coreLayerDoesNotDependOnFacade(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("L4 Core layer");
  }

  @Test
  void allLayerRules_shouldReturnRule() {
    ArchRule rule = LayerRules.allLayerRules(BASE_PACKAGE);

    assertThat(rule).isNotNull();
  }
}
