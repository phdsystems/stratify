package dev.engineeringlab.stratify.annotation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class LayerTest {

  @Test
  void shouldHaveFiveLayers() {
    assertThat(Layer.values()).hasSize(5);
  }

  @ParameterizedTest
  @CsvSource({"COMMON, 1", "SPI, 2", "API, 3", "CORE, 4", "FACADE, 5"})
  void shouldReturnCorrectLevel(Layer layer, int expectedLevel) {
    assertThat(layer.level()).isEqualTo(expectedLevel);
  }

  @ParameterizedTest
  @CsvSource({"COMMON, common", "SPI, spi", "API, api", "CORE, core", "FACADE, facade"})
  void shouldReturnCorrectSuffix(Layer layer, String expectedSuffix) {
    assertThat(layer.suffix()).isEqualTo(expectedSuffix);
  }

  @Test
  void facadeCanDependOnAllOtherLayers() {
    assertThat(Layer.FACADE.canDependOn(Layer.CORE)).isTrue();
    assertThat(Layer.FACADE.canDependOn(Layer.API)).isTrue();
    assertThat(Layer.FACADE.canDependOn(Layer.SPI)).isTrue();
    assertThat(Layer.FACADE.canDependOn(Layer.COMMON)).isTrue();
  }

  @Test
  void coreCanDependOnLowerLayers() {
    assertThat(Layer.CORE.canDependOn(Layer.FACADE)).isFalse();
    assertThat(Layer.CORE.canDependOn(Layer.API)).isTrue();
    assertThat(Layer.CORE.canDependOn(Layer.SPI)).isTrue();
    assertThat(Layer.CORE.canDependOn(Layer.COMMON)).isTrue();
  }

  @Test
  void apiCanDependOnLowerLayers() {
    assertThat(Layer.API.canDependOn(Layer.FACADE)).isFalse();
    assertThat(Layer.API.canDependOn(Layer.CORE)).isFalse();
    assertThat(Layer.API.canDependOn(Layer.SPI)).isTrue();
    assertThat(Layer.API.canDependOn(Layer.COMMON)).isTrue();
  }

  @Test
  void spiCanOnlyDependOnCommon() {
    assertThat(Layer.SPI.canDependOn(Layer.FACADE)).isFalse();
    assertThat(Layer.SPI.canDependOn(Layer.CORE)).isFalse();
    assertThat(Layer.SPI.canDependOn(Layer.API)).isFalse();
    assertThat(Layer.SPI.canDependOn(Layer.COMMON)).isTrue();
  }

  @Test
  void commonCannotDependOnAnyLayer() {
    assertThat(Layer.COMMON.canDependOn(Layer.FACADE)).isFalse();
    assertThat(Layer.COMMON.canDependOn(Layer.CORE)).isFalse();
    assertThat(Layer.COMMON.canDependOn(Layer.API)).isFalse();
    assertThat(Layer.COMMON.canDependOn(Layer.SPI)).isFalse();
  }

  @ParameterizedTest
  @EnumSource(Layer.class)
  void layerCannotDependOnItself(Layer layer) {
    assertThat(layer.canDependOn(layer)).isFalse();
  }

  @ParameterizedTest
  @CsvSource({"common, COMMON", "spi, SPI", "api, API", "core, CORE", "facade, FACADE"})
  void shouldFindLayerFromSuffix(String suffix, Layer expected) {
    assertThat(Layer.fromSuffix(suffix)).isEqualTo(expected);
  }

  @ParameterizedTest
  @CsvSource({"COMMON, COMMON", "SPI, SPI", "API, API", "CORE, CORE", "FACADE, FACADE"})
  void shouldFindLayerFromSuffixCaseInsensitive(String suffix, Layer expected) {
    assertThat(Layer.fromSuffix(suffix)).isEqualTo(expected);
  }

  @Test
  void shouldReturnNullForUnknownSuffix() {
    assertThat(Layer.fromSuffix("unknown")).isNull();
    assertThat(Layer.fromSuffix("")).isNull();
  }
}
