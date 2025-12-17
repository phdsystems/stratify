package dev.engineeringlab.stratify.rules;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.rules.Rules.Rule;
import dev.engineeringlab.stratify.rules.testfixtures.api.ApiService;
import dev.engineeringlab.stratify.rules.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.rules.testfixtures.core.CoreImplementation;
import dev.engineeringlab.stratify.rules.testfixtures.facade.TestFacade;
import dev.engineeringlab.stratify.rules.testfixtures.spi.SpiInterface;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RulesTest {

  private static final String BASE_PACKAGE = "dev.engineeringlab.stratify.rules.testfixtures";
  private static Set<Class<?>> classes;

  @BeforeAll
  static void setUp() {
    classes =
        Set.of(
            CommonClass.class,
            SpiInterface.class,
            ApiService.class,
            CoreImplementation.class,
            TestFacade.class);
  }

  @Test
  void commonLayer_shouldPassForValidFixtures() {
    Rule rule = Rules.commonLayer(BASE_PACKAGE);

    List<Violation> violations = rule.check(classes);

    assertThat(violations).isEmpty();
  }

  @Test
  void spiLayer_shouldPassForValidFixtures() {
    Rule rule = Rules.spiLayer(BASE_PACKAGE);

    List<Violation> violations = rule.check(classes);

    assertThat(violations).isEmpty();
  }

  @Test
  void apiLayer_shouldPassForValidFixtures() {
    Rule rule = Rules.apiLayer(BASE_PACKAGE);

    List<Violation> violations = rule.check(classes);

    assertThat(violations).isEmpty();
  }

  @Test
  void noCycles_shouldReturnEmptyForValidFixtures() {
    Rule rule = Rules.noCycles(BASE_PACKAGE);

    List<Violation> violations = rule.check(classes);

    assertThat(violations).isEmpty();
  }

  @Test
  void facadesFinal_shouldPassForAnnotatedFinalClass() {
    Rule rule = Rules.facadesFinal(BASE_PACKAGE);

    List<Violation> violations = rule.check(classes);

    assertThat(violations).isEmpty();
  }

  @Test
  void all_shouldReturnAllRules() {
    List<Rule> rules = Rules.all(BASE_PACKAGE);

    assertThat(rules).hasSize(7);
  }
}
