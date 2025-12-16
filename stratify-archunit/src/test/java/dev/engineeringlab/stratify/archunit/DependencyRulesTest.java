package dev.engineeringlab.stratify.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DependencyRulesTest {

  private static final String BASE_PACKAGE = "dev.engineeringlab.stratify.archunit.testfixtures";
  private static JavaClasses classes;

  @BeforeAll
  static void setUp() {
    classes = new ClassFileImporter().importPackages(BASE_PACKAGE);
  }

  @Test
  void noCyclicDependencies_shouldReturnRule() {
    ArchRule rule = DependencyRules.noCyclicDependencies(BASE_PACKAGE);

    assertThat(rule).isNotNull();
    assertThat(rule.getDescription()).contains("cyclic dependencies");
  }

  @Test
  void apiShouldNotDependOnImplementation_shouldPassForValidFixtures() {
    ArchRule rule = DependencyRules.apiShouldNotDependOnImplementation(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void apiShouldNotDependOnImplementation_shouldHaveDescriptiveMessage() {
    ArchRule rule = DependencyRules.apiShouldNotDependOnImplementation(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("API");
    assertThat(rule.getDescription()).contains("implementation");
  }

  @Test
  void internalPackagesNotAccessedExternally_shouldPassForValidFixtures() {
    ArchRule rule = DependencyRules.internalPackagesNotAccessedExternally(BASE_PACKAGE);

    EvaluationResult result = rule.evaluate(classes);

    assertThat(result.hasViolation()).isFalse();
  }

  @Test
  void internalPackagesNotAccessedExternally_shouldHaveDescriptiveMessage() {
    ArchRule rule = DependencyRules.internalPackagesNotAccessedExternally(BASE_PACKAGE);

    assertThat(rule.getDescription()).contains("Internal");
  }

  @Test
  void modelsShouldNotDependOnServices_shouldReturnRule() {
    ArchRule rule = DependencyRules.modelsShouldNotDependOnServices(BASE_PACKAGE);

    assertThat(rule).isNotNull();
    assertThat(rule.getDescription()).contains("Models");
  }

  @Test
  void exceptionsShouldBeIndependent_shouldReturnRule() {
    ArchRule rule = DependencyRules.exceptionsShouldBeIndependent(BASE_PACKAGE);

    assertThat(rule).isNotNull();
    assertThat(rule.getDescription()).contains("Exceptions");
  }
}
