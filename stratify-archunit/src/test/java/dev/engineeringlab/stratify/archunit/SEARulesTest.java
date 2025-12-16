package dev.engineeringlab.stratify.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SEARulesTest {

  private static final String BASE_PACKAGE = "dev.engineeringlab.stratify.archunit.testfixtures";
  private static JavaClasses classes;

  @BeforeAll
  static void setUp() {
    classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
  }

  @Test
  void commonLayerDependencies_shouldPassForValidFixtures() {
    ArchRule rule = SEARules.commonLayerDependencies(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void spiLayerDependencies_shouldPassForValidFixtures() {
    ArchRule rule = SEARules.spiLayerDependencies(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void apiLayerDependencies_shouldPassForValidFixtures() {
    ArchRule rule = SEARules.apiLayerDependencies(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void noCircularDependencies_shouldReturnRule() {
    ArchRule rule = SEARules.noCircularDependencies(BASE_PACKAGE);

    assertThat(rule).isNotNull();
    assertThat(rule.getDescription()).contains("free of circular dependencies");
  }

  @Test
  void facadeClassesShouldBeFinal_shouldPassForAnnotatedFinalClass() {
    ArchRule rule = SEARules.facadeClassesShouldBeFinal(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void layerDependencies_shouldReturnRule() {
    ArchRule rule = SEARules.layerDependencies(BASE_PACKAGE);

    assertThat(rule).isNotNull();
  }
}
