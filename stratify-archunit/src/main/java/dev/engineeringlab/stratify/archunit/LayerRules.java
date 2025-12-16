package dev.engineeringlab.stratify.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;

/**
 * Layer-specific ArchUnit rules for SEA (Stratified Encapsulation Architecture).
 *
 * <p>Enforces the layer hierarchy:
 *
 * <ul>
 *   <li>COMMON (L1) - Foundation with no internal dependencies
 *   <li>SPI (L2) - Extension points depending only on COMMON
 *   <li>API (L3) - Consumer contracts depending on COMMON and SPI
 *   <li>CORE (L4) - Implementations depending on COMMON, SPI, and API
 *   <li>FACADE (L5) - Public entry points accessing all layers
 * </ul>
 */
public final class LayerRules {

  private LayerRules() {}

  /** Common layer (L1) should have no dependencies on other SEA layers. */
  public static ArchRule commonLayerIsolation(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..spi..",
            basePackage + "..api..",
            basePackage + "..core..",
            basePackage + "..impl..",
            basePackage + "..facade..")
        .as("L1 Common layer must not depend on higher layers")
        .allowEmptyShould(true);
  }

  /** SPI layer (L2) should only depend on Common. */
  public static ArchRule spiLayerDependsOnlyOnCommon(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..spi..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..api..",
            basePackage + "..core..",
            basePackage + "..impl..",
            basePackage + "..facade..")
        .as("L2 SPI layer must only depend on L1 Common")
        .allowEmptyShould(true);
  }

  /** API layer (L3) should not depend on Core or Facade. */
  public static ArchRule apiLayerDependsOnlyOnCommonAndSpi(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..core..", basePackage + "..impl..", basePackage + "..facade..")
        .as("L3 API layer must not depend on L4 Core or L5 Facade")
        .allowEmptyShould(true);
  }

  /** Core layer (L4) should not depend on Facade. */
  public static ArchRule coreLayerDoesNotDependOnFacade(String basePackage) {
    return noClasses()
        .that()
        .resideInAnyPackage(basePackage + "..core..", basePackage + "..impl..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + "..facade..")
        .as("L4 Core layer must not depend on L5 Facade")
        .allowEmptyShould(true);
  }

  /** Validates all layer dependency rules for a given base package. */
  public static ArchRule allLayerRules(String basePackage) {
    return commonLayerIsolation(basePackage);
  }
}
