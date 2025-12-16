package dev.engineeringlab.stratify.archunit;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.engineeringlab.stratify.annotation.Layer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Main entry point for SEA (Stratified Encapsulation Architecture) ArchUnit rules.
 *
 * <p>This class provides a comprehensive set of pre-built ArchUnit rules for validating
 * SEA architecture compliance in tests. Use these rules to enforce:
 * <ul>
 *   <li>Layer dependency hierarchy (L1 to L5 flow)</li>
 *   <li>Facade encapsulation principles</li>
 *   <li>Provider implementation conventions</li>
 *   <li>No circular dependencies</li>
 *   <li>Proper visibility and access control</li>
 * </ul>
 *
 * <p>Example usage in JUnit 5 tests:
 * <pre>
 * import com.tngtech.archunit.core.domain.JavaClasses;
 * import com.tngtech.archunit.core.importer.ClassFileImporter;
 * import com.tngtech.archunit.junit.AnalyzeClasses;
 * import com.tngtech.archunit.junit.ArchTest;
 * import dev.engineeringlab.stratify.archunit.SEARules;
 *
 * {@literal @}AnalyzeClasses(packages = "com.example.myapp")
 * public class ArchitectureTest {
 *
 *     {@literal @}ArchTest
 *     static final ArchRule layerDependencies = SEARules.layerDependencies();
 *
 *     {@literal @}ArchTest
 *     static final ArchRule facadeEncapsulation = SEARules.facadeEncapsulation();
 *
 *     {@literal @}ArchTest
 *     static final ArchRule providerConventions = SEARules.providerConventions();
 *
 *     {@literal @}ArchTest
 *     static final ArchRule noCircularDeps = SEARules.noCircularDependencies();
 * }
 * </pre>
 *
 * <p>All rules use {@code allowEmptyShould(true)} to gracefully handle projects
 * where certain architectural elements don't exist yet.
 *
 * @see LayerRules
 * @see DependencyRules
 * @see SEAConditions
 */
public final class SEARules {

    private SEARules() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Enforces the strict layer dependency hierarchy: L1 to L2 to L3 to L4 to L5.
     *
     * <p>This is the core SEA rule that ensures each layer only depends on layers below it:
     * <pre>
     *     FACADE (L5) - Can depend on: CORE, API, SPI, COMMON
     *        │
     *     CORE (L4) - Can depend on: API, SPI, COMMON
     *        │
     *     API (L3) - Can depend on: SPI, COMMON
     *        │
     *     SPI (L2) - Can depend on: COMMON
     *        │
     *     COMMON (L1) - No internal dependencies
     * </pre>
     *
     * <p>This rule prevents:
     * <ul>
     *   <li>Lower layers depending on higher layers</li>
     *   <li>Skipping layers (e.g., CORE depending directly on COMMON, bypassing SPI/API)</li>
     *   <li>Circular dependencies between layers</li>
     * </ul>
     *
     * <p>Example violations:
     * <pre>
     * // WRONG: API depending on CORE
     * package com.example.api;
     * import com.example.core.UserServiceImpl; // Violation!
     *
     * // WRONG: SPI depending on API
     * package com.example.spi;
     * import com.example.api.UserService; // Violation!
     *
     * // CORRECT: API depending on SPI and COMMON
     * package com.example.api;
     * import com.example.spi.UserProvider;
     * import com.example.common.User;
     * </pre>
     *
     * @return an ArchRule enforcing the SEA layer hierarchy
     */
    public static ArchRule layerDependencies() {
        return LayerRules.commonLayerRules()
            .and(LayerRules.spiLayerRules())
            .and(LayerRules.apiLayerRules())
            .and(LayerRules.coreLayerRules())
            .and(DependencyRules.noCoreInApi())
            .and(DependencyRules.noFacadeInApi())
            .and(DependencyRules.noFacadeInCore())
            .and(DependencyRules.spiDependsOnlyOnCommon())
            .and(DependencyRules.commonIsFoundation())
            .because("SEA layer dependency hierarchy must flow upward: " +
                    "COMMON (L1) <- SPI (L2) <- API (L3) <- CORE (L4) <- FACADE (L5)");
    }

    /**
     * Enforces facade encapsulation: only facade classes are public entry points.
     *
     * <p>This rule ensures that:
     * <ul>
     *   <li>FACADE layer classes serve as the only public API surface</li>
     *   <li>CORE layer implementations are hidden from consumers</li>
     *   <li>SPI interfaces are not directly exposed in FACADE public methods</li>
     *   <li>Consumers interact through high-level API abstractions</li>
     * </ul>
     *
     * <p>Example of proper facade encapsulation:
     * <pre>
     * // FACADE layer - Public entry point
     * public class StratifyFacade {
     *     // Exposes API-level abstraction, not SPI
     *     public UserService getUserService() {
     *         return ServiceFactory.create(UserService.class);
     *     }
     * }
     *
     * // API layer - Consumer contract
     * public interface UserService {
     *     User findUser(String id);
     * }
     *
     * // CORE layer - Hidden implementation
     * class DefaultUserService implements UserService {
     *     public User findUser(String id) { ... }
     * }
     * </pre>
     *
     * @return an ArchRule enforcing facade encapsulation principles
     */
    public static ArchRule facadeEncapsulation() {
        return LayerRules.facadeLayerRules()
            .and(DependencyRules.noSpiInFacade())
            .and(LayerRules.coreClassesShouldBePackagePrivate())
            .because("FACADE layer (L5) must serve as the only public entry point, " +
                    "encapsulating CORE implementations and not directly exposing SPI interfaces.");
    }

    /**
     * Enforces provider implementation conventions.
     *
     * <p>This rule ensures that classes marked with {@code @Provider} annotation:
     * <ul>
     *   <li>Follow naming conventions (*Provider or *ProviderImpl)</li>
     *   <li>Are located in the CORE layer (L4)</li>
     *   <li>Are properly structured and discoverable</li>
     * </ul>
     *
     * <p>Example of proper provider implementation:
     * <pre>
     * // In CORE layer
     * {@literal @}Provider(
     *     name = "openai",
     *     priority = 10,
     *     description = "OpenAI GPT provider"
     * )
     * public class OpenAIProvider implements TextGenerationProvider {
     *     {@literal @}Override
     *     public GeneratedText generate(GenerationRequest request) {
     *         // Implementation
     *     }
     * }
     * </pre>
     *
     * @return an ArchRule enforcing provider conventions
     */
    public static ArchRule providerConventions() {
        return LayerRules.providerNamingConvention()
            .and(classes()
                .that(SEAConditions.hasProviderAnnotation())
                .should(SEAConditions.isInLayer(Layer.CORE))
                .because("Provider implementations must be in CORE layer (L4)"))
            .allowEmptyShould(true);
    }

    /**
     * Detects and prevents circular dependencies between layers and modules.
     *
     * <p>This rule ensures there are no circular dependencies that would violate
     * the strict layering principle. It checks for cycles:
     * <ul>
     *   <li>Between different layers (prevented by layer hierarchy)</li>
     *   <li>Between different packages within the same layer</li>
     *   <li>Between different modules in the application</li>
     * </ul>
     *
     * <p>Example violation:
     * <pre>
     * // Package A depends on Package B
     * package com.example.core.users;
     * import com.example.core.orders.OrderService;
     *
     * // Package B depends on Package A - CIRCULAR!
     * package com.example.core.orders;
     * import com.example.core.users.UserService;
     * </pre>
     *
     * <p>Circular dependencies indicate design problems and should be resolved by:
     * <ul>
     *   <li>Extracting shared interfaces to a lower layer</li>
     *   <li>Using events or callbacks instead of direct calls</li>
     *   <li>Refactoring to eliminate the circular dependency</li>
     * </ul>
     *
     * @return an ArchRule that detects circular dependencies
     */
    public static ArchRule noCircularDependencies() {
        return DependencyRules.noCircularDependenciesBetweenLayers()
            .and(SlicesRuleDefinition.slices()
                .matching("(**).(common|spi|api|core|facade).(**)")
                .should()
                .beFreeOfCycles()
                .because("Circular dependencies violate SEA principles and create tight coupling"))
            .allowEmptyShould(true);
    }

    /**
     * Enforces that SPI layer contains only interfaces and abstract classes.
     *
     * <p>The SPI layer should define extension points, not provide implementations.
     * All concrete implementations belong in the CORE layer.
     *
     * @return an ArchRule ensuring SPI contains only abstractions
     */
    public static ArchRule spiOnlyAbstractions() {
        return LayerRules.spiShouldOnlyContainAbstractions();
    }

    /**
     * Enforces that API layer contains only interfaces and abstract classes.
     *
     * <p>The API layer should define consumer contracts, not provide implementations.
     * All concrete implementations belong in the CORE layer.
     *
     * @return an ArchRule ensuring API contains only abstractions
     */
    public static ArchRule apiOnlyAbstractions() {
        return LayerRules.apiShouldOnlyContainAbstractions();
    }

    /**
     * Enforces that COMMON layer contains only simple data structures.
     *
     * <p>The COMMON layer should contain DTOs, value objects, enums, and constants,
     * not business logic or complex services.
     *
     * @return an ArchRule ensuring COMMON contains simple types
     */
    public static ArchRule commonSimpleTypes() {
        return LayerRules.commonClassesShouldBeSimple();
    }

    /**
     * Comprehensive rule combining all SEA architectural constraints.
     *
     * <p>This rule aggregates all SEA rules into a single validation:
     * <ul>
     *   <li>Layer dependency hierarchy</li>
     *   <li>Facade encapsulation</li>
     *   <li>Provider conventions</li>
     *   <li>No circular dependencies</li>
     *   <li>Layer-specific constraints</li>
     * </ul>
     *
     * <p>Use this for comprehensive SEA validation in a single test:
     * <pre>
     * {@literal @}ArchTest
     * static final ArchRule allSeaRules = SEARules.all();
     * </pre>
     *
     * @return a composite ArchRule enforcing all SEA constraints
     */
    public static ArchRule all() {
        return layerDependencies()
            .and(facadeEncapsulation())
            .and(providerConventions())
            .and(noCircularDependencies())
            .and(spiOnlyAbstractions())
            .and(apiOnlyAbstractions())
            .and(commonSimpleTypes())
            .because("All SEA architectural principles must be enforced for proper stratified encapsulation");
    }

    /**
     * Validates that each layer exists and follows basic structural requirements.
     *
     * <p>This rule checks that:
     * <ul>
     *   <li>Each layer has appropriate package structure</li>
     *   <li>Classes are organized according to their layer responsibilities</li>
     *   <li>Naming conventions are followed</li>
     * </ul>
     *
     * @return an ArchRule validating layer structure
     */
    public static ArchRule layerStructure() {
        return classes()
            .that()
            .resideInAnyPackage("..common..", "..spi..", "..api..", "..core..", "..facade..")
            .should()
            .resideInAPackage("..common..")
            .orShould()
            .resideInAPackage("..spi..")
            .orShould()
            .resideInAPackage("..api..")
            .orShould()
            .resideInAPackage("..core..")
            .orShould()
            .resideInAPackage("..facade..")
            .because("All classes should be organized into one of the five SEA layers: " +
                    "COMMON, SPI, API, CORE, or FACADE")
            .allowEmptyShould(true);
    }

    /**
     * Enforces that CORE layer is not accessed directly from outside the module.
     *
     * <p>Only FACADE should be accessible to external consumers. CORE implementations
     * should be hidden and accessed only through FACADE entry points.
     *
     * @return an ArchRule preventing direct access to CORE from external code
     */
    public static ArchRule coreNotExternallyAccessible() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.CORE))
            .and()
            .arePublic()
            .and()
            .doNotHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)
            .and(SEAConditions.hasProviderAnnotation().negate())
            .should()
            .bePublic()
            .because("CORE layer (L4) implementations should not be directly accessible. " +
                    "Access should be through FACADE layer (L5) entry points. " +
                    "Exception: Provider classes can be public for ServiceLoader discovery.")
            .allowEmptyShould(true);
    }

    /**
     * Validates proper visibility modifiers across all layers.
     *
     * <p>This rule enforces that:
     * <ul>
     *   <li>FACADE classes are public (entry points)</li>
     *   <li>API and SPI interfaces are public (contracts)</li>
     *   <li>CORE implementations are package-private (encapsulated)</li>
     *   <li>COMMON types can be public (shared structures)</li>
     * </ul>
     *
     * @return an ArchRule enforcing proper visibility across layers
     */
    public static ArchRule properVisibility() {
        return classes()
            .that(SEAConditions.isFacadeClass())
            .should()
            .bePublic()
            .because("Facade classes must be public as they serve as entry points")
            .allowEmptyShould(true)
            .andShould()
            .haveSimpleNameEndingWith("Facade")
            .orShould()
            .haveSimpleNameEndingWith("Factory")
            .because("Facade classes should follow naming conventions");
    }

    /**
     * Minimal rule set for getting started with SEA validation.
     *
     * <p>This provides a subset of rules that are most critical for SEA compliance:
     * <ul>
     *   <li>Layer dependency hierarchy</li>
     *   <li>No circular dependencies</li>
     *   <li>Basic facade encapsulation</li>
     * </ul>
     *
     * <p>Use this when first introducing SEA rules to an existing codebase,
     * then gradually adopt more comprehensive rules.
     *
     * @return a minimal set of SEA rules for initial adoption
     */
    public static ArchRule minimal() {
        return layerDependencies()
            .and(noCircularDependencies())
            .because("Minimal SEA rules enforce layer hierarchy and prevent circular dependencies");
    }
}
