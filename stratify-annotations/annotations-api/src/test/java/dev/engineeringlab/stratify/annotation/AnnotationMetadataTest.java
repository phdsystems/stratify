package dev.engineeringlab.stratify.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Test;

class AnnotationMetadataTest {

  @Test
  void seaModuleShouldHaveRuntimeRetention() {
    Retention retention = SEAModule.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void seaModuleShouldTargetPackageAndType() {
    Target target = SEAModule.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).containsExactlyInAnyOrder(ElementType.PACKAGE, ElementType.TYPE);
  }

  @Test
  void providerShouldHaveRuntimeRetention() {
    Retention retention = Provider.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void providerShouldTargetType() {
    Target target = Provider.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).containsExactly(ElementType.TYPE);
  }

  @Test
  void facadeShouldHaveRuntimeRetention() {
    Retention retention = Facade.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void facadeShouldTargetType() {
    Target target = Facade.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).containsExactly(ElementType.TYPE);
  }

  @Test
  void internalShouldHaveRuntimeRetention() {
    Retention retention = Internal.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void internalShouldTargetMultipleElements() {
    Target target = Internal.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value())
        .containsExactlyInAnyOrder(
            ElementType.TYPE,
            ElementType.METHOD,
            ElementType.FIELD,
            ElementType.CONSTRUCTOR,
            ElementType.PACKAGE);
  }

  @Test
  void exportedShouldHaveRuntimeRetention() {
    Retention retention = Exported.class.getAnnotation(Retention.class);
    assertThat(retention).isNotNull();
    assertThat(retention.value()).isEqualTo(RetentionPolicy.RUNTIME);
  }

  @Test
  void exportedShouldTargetTypeAndMethod() {
    Target target = Exported.class.getAnnotation(Target.class);
    assertThat(target).isNotNull();
    assertThat(target.value()).containsExactlyInAnyOrder(ElementType.TYPE, ElementType.METHOD);
  }
}
