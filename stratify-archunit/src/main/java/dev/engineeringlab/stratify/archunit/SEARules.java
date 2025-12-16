package dev.engineeringlab.stratify.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Main entry point for SEA (Stratified Encapsulation Architecture) ArchUnit rules.
 *
 * <p>Provides pre-built rules for validating SEA architecture compliance.
 *
 * <p>Example usage:
 *
 * <pre>
 * {@literal @}AnalyzeClasses(packages = "com.example.myapp")
 * public class ArchitectureTest {
 *     {@literal @}ArchTest
 *     static final ArchRule layerDeps = SEARules.layerDependencies("com.example");
 * }
 * </pre>
 */
public final class SEARules {

  private SEARules() {}

  /** Rule: Classes in Common layer should not depend on higher layers. */
  public static ArchRule commonLayerDependencies(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..common..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..spi..",
            basePackage + "..api..",
            basePackage + "..core..",
            basePackage + "..impl..")
        .as("Common layer should not depend on SPI, API, Core, or Impl layers")
        .allowEmptyShould(true);
  }

  /** Rule: Classes in SPI layer should only depend on Common. */
  public static ArchRule spiLayerDependencies(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..spi..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..api..", basePackage + "..core..", basePackage + "..impl..")
        .as("SPI layer should not depend on API, Core, or Impl layers")
        .allowEmptyShould(true);
  }

  /** Rule: Classes in API layer should only depend on Common and SPI. */
  public static ArchRule apiLayerDependencies(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(basePackage + "..core..", basePackage + "..impl..")
        .as("API layer should not depend on Core or Impl layers")
        .allowEmptyShould(true);
  }

  /** Rule: No circular dependencies between slices. */
  public static ArchRule noCircularDependencies(String basePackage) {
    return SlicesRuleDefinition.slices()
        .matching(basePackage + ".(*)..")
        .should()
        .beFreeOfCycles()
        .as("Modules should be free of circular dependencies")
        .allowEmptyShould(true);
  }

  /** Rule: Facade classes should be final. */
  public static ArchRule facadeClassesShouldBeFinal(String basePackage) {
    return classes()
        .that()
        .haveSimpleNameEndingWith("Facade")
        .or()
        .areAnnotatedWith("dev.engineeringlab.stratify.annotation.Facade")
        .should()
        .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.FINAL)
        .as("Facade classes should be final")
        .allowEmptyShould(true);
  }

  /** Rule: Provider implementations should implement a provider interface. */
  public static ArchRule providersShouldImplementInterface() {
    return classes()
        .that()
        .areAnnotatedWith("dev.engineeringlab.stratify.annotation.Provider")
        .should()
        .implement(
            com.tngtech.archunit.base.DescribedPredicate.describe(
                "an interface", javaClass -> javaClass.getAllRawInterfaces().size() > 0))
        .as("Provider implementations should implement at least one interface")
        .allowEmptyShould(true);
  }

  /** Rule: Internal classes should not be accessed from outside their package hierarchy. */
  public static ArchRule internalClassesNotAccessedExternally(String basePackage) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(basePackage + "..internal..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + "..internal..")
        .as("Internal classes should not be accessed from outside")
        .allowEmptyShould(true);
  }

  /** Combined rule checking all layer dependencies. */
  public static ArchRule layerDependencies(String basePackage) {
    return commonLayerDependencies(basePackage);
  }
}
