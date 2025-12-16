package dev.engineeringlab.stratify.archunit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;

/**
 * Pre-built ArchUnit rules for SEA dependency constraints.
 *
 * <p>Enforces proper dependency relationships between SEA layers:
 *
 * <ul>
 *   <li>No circular dependencies between layers or modules
 *   <li>Proper abstraction layers are not bypassed
 *   <li>Internal packages are not accessed externally
 * </ul>
 */
public final class DependencyRules {

  private DependencyRules() {}

  /** No circular dependencies between top-level modules. */
  public static ArchRule noCyclicDependencies(String basePackage) {
    return SlicesRuleDefinition.slices()
        .matching(basePackage + ".(*)..")
        .should()
        .beFreeOfCycles()
        .as("Modules should be free of cyclic dependencies")
        .allowEmptyShould(true);
  }

  /** No direct dependencies from API to implementation/core. */
  public static ArchRule apiShouldNotDependOnImplementation(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(basePackage + "..core..", basePackage + "..impl..")
        .as("API should not depend on implementation details")
        .allowEmptyShould(true);
  }

  /** Internal packages should not be accessed from outside their module. */
  public static ArchRule internalPackagesNotAccessedExternally(String basePackage) {
    return noClasses()
        .that()
        .resideOutsideOfPackage(basePackage + "..internal..")
        .should()
        .dependOnClassesThat()
        .resideInAPackage(basePackage + "..internal..")
        .as("Internal packages should not be accessed externally")
        .allowEmptyShould(true);
  }

  /** Model classes should not depend on services or repositories. */
  public static ArchRule modelsShouldNotDependOnServices(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..model..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(basePackage + "..service..", basePackage + "..repository..")
        .as("Models should not depend on services or repositories")
        .allowEmptyShould(true);
  }

  /** Exception classes should only depend on other exceptions or common types. */
  public static ArchRule exceptionsShouldBeIndependent(String basePackage) {
    return noClasses()
        .that()
        .resideInAPackage(basePackage + "..exception..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            basePackage + "..service..",
            basePackage + "..repository..",
            basePackage + "..core..",
            basePackage + "..impl..")
        .as("Exceptions should be independent of services and implementations")
        .allowEmptyShould(true);
  }
}
