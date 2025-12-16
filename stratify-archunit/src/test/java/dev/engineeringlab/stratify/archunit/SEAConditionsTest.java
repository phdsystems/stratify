package dev.engineeringlab.stratify.archunit;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import dev.engineeringlab.stratify.archunit.testfixtures.api.ApiService;
import dev.engineeringlab.stratify.archunit.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.archunit.testfixtures.core.CoreImplementation;
import dev.engineeringlab.stratify.archunit.testfixtures.facade.TestFacade;
import dev.engineeringlab.stratify.archunit.testfixtures.spi.SpiInterface;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SEAConditionsTest {

  private static JavaClasses classes;

  @BeforeAll
  static void setUp() {
    classes =
        new ClassFileImporter().importPackages("dev.engineeringlab.stratify.archunit.testfixtures");
  }

  @Test
  void inCommonLayer_shouldMatchCommonClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inCommonLayer();
    JavaClass commonClass = classes.get(CommonClass.class);

    assertThat(predicate.test(commonClass)).isTrue();
  }

  @Test
  void inCommonLayer_shouldNotMatchOtherLayers() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inCommonLayer();
    JavaClass apiClass = classes.get(ApiService.class);

    assertThat(predicate.test(apiClass)).isFalse();
  }

  @Test
  void inSpiLayer_shouldMatchSpiClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inSpiLayer();
    JavaClass spiClass = classes.get(SpiInterface.class);

    assertThat(predicate.test(spiClass)).isTrue();
  }

  @Test
  void inApiLayer_shouldMatchApiClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inApiLayer();
    JavaClass apiClass = classes.get(ApiService.class);

    assertThat(predicate.test(apiClass)).isTrue();
  }

  @Test
  void inCoreLayer_shouldMatchCoreClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inCoreLayer();
    JavaClass coreClass = classes.get(CoreImplementation.class);

    assertThat(predicate.test(coreClass)).isTrue();
  }

  @Test
  void inFacadeLayer_shouldMatchFacadeClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.inFacadeLayer();
    JavaClass facadeClass = classes.get(TestFacade.class);

    assertThat(predicate.test(facadeClass)).isTrue();
  }

  @Test
  void isFacade_shouldMatchAnnotatedClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.isFacade();
    JavaClass facadeClass = classes.get(TestFacade.class);

    assertThat(predicate.test(facadeClass)).isTrue();
  }

  @Test
  void isFacade_shouldNotMatchNonFacadeClasses() {
    DescribedPredicate<JavaClass> predicate = SEAConditions.isFacade();
    JavaClass commonClass = classes.get(CommonClass.class);

    assertThat(predicate.test(commonClass)).isFalse();
  }

  @Test
  void predicatesShouldHaveDescriptions() {
    assertThat(SEAConditions.inCommonLayer().getDescription()).isNotBlank();
    assertThat(SEAConditions.inSpiLayer().getDescription()).isNotBlank();
    assertThat(SEAConditions.inApiLayer().getDescription()).isNotBlank();
    assertThat(SEAConditions.inCoreLayer().getDescription()).isNotBlank();
    assertThat(SEAConditions.inFacadeLayer().getDescription()).isNotBlank();
    assertThat(SEAConditions.isFacade().getDescription()).isNotBlank();
    assertThat(SEAConditions.isProvider().getDescription()).isNotBlank();
    assertThat(SEAConditions.isInternal().getDescription()).isNotBlank();
  }
}
