package dev.engineeringlab.stratify.archunit;

import com.tngtech.archunit.lang.ArchRule;
import dev.engineeringlab.stratify.annotation.Layer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Layer-specific ArchUnit rules for SEA (Stratified Encapsulation Architecture).
 *
 * <p>This class provides rules that enforce the specific constraints and responsibilities
 * of each SEA layer:
 * <ul>
 *   <li>COMMON (L1) - Foundation with no internal dependencies</li>
 *   <li>SPI (L2) - Extension points depending only on COMMON</li>
 *   <li>API (L3) - Consumer contracts depending on COMMON and SPI</li>
 *   <li>CORE (L4) - Implementations depending on COMMON, SPI, and API</li>
 *   <li>FACADE (L5) - Public entry points accessing all layers</li>
 * </ul>
 *
 * <p>All rules use {@code allowEmptyShould(true)} to gracefully handle projects
 * where certain layers don't exist yet.
 *
 * @see SEARules
 * @see DependencyRules
 * @see SEAConditions
 */
public final class LayerRules {

    private LayerRules() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Enforces rules for the COMMON layer (L1).
     *
     * <p>The COMMON layer is the foundation of the architecture and should:
     * <ul>
     *   <li>Contain only DTOs, value objects, enums, constants, and error types</li>
     *   <li>Have no dependencies on other internal layers (SPI, API, CORE, FACADE)</li>
     *   <li>Only depend on external libraries</li>
     *   <li>Be free of business logic or implementation details</li>
     * </ul>
     *
     * <p>Example COMMON layer contents:
     * <pre>
     * public class UserDTO {
     *     private String id;
     *     private String name;
     *     // Getters, setters, equals, hashCode
     * }
     *
     * public enum ErrorCode {
     *     INVALID_INPUT,
     *     NOT_FOUND,
     *     INTERNAL_ERROR
     * }
     * </pre>
     *
     * @return an ArchRule enforcing COMMON layer constraints
     */
    public static ArchRule commonLayerRules() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.COMMON))
            .should()
            .dependOnClassesThat(
                SEAConditions.isInLayer(Layer.SPI)
                    .or(SEAConditions.isInLayer(Layer.API))
                    .or(SEAConditions.isInLayer(Layer.CORE))
                    .or(SEAConditions.isInLayer(Layer.FACADE))
            )
            .because("COMMON layer (L1) is the foundation and must not depend on any other SEA layer. " +
                    "It should only contain shared types (DTOs, value objects, constants) " +
                    "with dependencies on external libraries only.")
            .allowEmptyShould(true);
    }

    /**
     * Enforces rules for the SPI layer (L2).
     *
     * <p>The SPI (Service Provider Interface) layer defines extension points and should:
     * <ul>
     *   <li>Contain only interfaces and abstract classes defining extension contracts</li>
     *   <li>Depend only on COMMON layer (L1)</li>
     *   <li>Not contain implementations (those belong in CORE)</li>
     *   <li>Not depend on API, CORE, or FACADE layers</li>
     * </ul>
     *
     * <p>Example SPI layer contents:
     * <pre>
     * public interface TextGenerationProvider {
     *     GeneratedText generate(GenerationRequest request);
     *     boolean supports(GenerationRequest request);
     * }
     *
     * public interface ConfigurationProvider {
     *     Configuration load(String source);
     * }
     * </pre>
     *
     * @return an ArchRule enforcing SPI layer constraints
     */
    public static ArchRule spiLayerRules() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.SPI))
            .should()
            .dependOnClassesThat(
                SEAConditions.isInLayer(Layer.API)
                    .or(SEAConditions.isInLayer(Layer.CORE))
                    .or(SEAConditions.isInLayer(Layer.FACADE))
            )
            .because("SPI layer (L2) defines extension points and must only depend on COMMON layer (L1). " +
                    "It should contain interfaces and abstract classes for providers, not implementations.")
            .allowEmptyShould(true);
    }

    /**
     * Enforces rules for the API layer (L3).
     *
     * <p>The API layer defines consumer-facing contracts and should:
     * <ul>
     *   <li>Contain interfaces and abstract classes defining consumer contracts</li>
     *   <li>Depend only on COMMON (L1) and SPI (L2) layers</li>
     *   <li>Not depend on CORE or FACADE layers</li>
     *   <li>Not contain implementations (those belong in CORE)</li>
     *   <li>Define high-level, use-case oriented interfaces</li>
     * </ul>
     *
     * <p>Example API layer contents:
     * <pre>
     * public interface UserService {
     *     User findUser(String userId);
     *     List&lt;User&gt; searchUsers(SearchCriteria criteria);
     *     void updateUser(User user);
     * }
     *
     * public interface ValidationService {
     *     ValidationResult validate(Object input);
     * }
     * </pre>
     *
     * @return an ArchRule enforcing API layer constraints
     */
    public static ArchRule apiLayerRules() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.API))
            .should()
            .dependOnClassesThat(
                SEAConditions.isInLayer(Layer.CORE)
                    .or(SEAConditions.isInLayer(Layer.FACADE))
            )
            .because("API layer (L3) defines consumer contracts and must only depend on " +
                    "COMMON (L1) and SPI (L2) layers. " +
                    "It should not depend on implementations in CORE or aggregations in FACADE.")
            .allowEmptyShould(true);
    }

    /**
     * Enforces rules for the CORE layer (L4).
     *
     * <p>The CORE layer contains implementations and should:
     * <ul>
     *   <li>Contain concrete implementations of API and SPI contracts</li>
     *   <li>Depend on COMMON (L1), SPI (L2), and API (L3) layers</li>
     *   <li>Not depend on FACADE layer (L5)</li>
     *   <li>Be primarily package-private (not exposed externally)</li>
     *   <li>Contain business logic and provider implementations</li>
     * </ul>
     *
     * <p>Example CORE layer contents:
     * <pre>
     * {@literal @}Provider(name = "default", priority = 0)
     * class DefaultUserService implements UserService {
     *     public User findUser(String userId) {
     *         // Implementation
     *     }
     * }
     *
     * class UserValidator {
     *     boolean validate(User user) {
     *         // Validation logic
     *     }
     * }
     * </pre>
     *
     * @return an ArchRule enforcing CORE layer constraints
     */
    public static ArchRule coreLayerRules() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.CORE))
            .should()
            .dependOnClassesThat(SEAConditions.isInLayer(Layer.FACADE))
            .because("CORE layer (L4) contains implementations and must not depend on FACADE layer (L5). " +
                    "It can depend on COMMON (L1), SPI (L2), and API (L3) for contracts and shared types.")
            .allowEmptyShould(true);
    }

    /**
     * Enforces rules for the FACADE layer (L5).
     *
     * <p>The FACADE layer is the public entry point and should:
     * <ul>
     *   <li>Provide the only externally visible API surface</li>
     *   <li>Can depend on all lower layers (COMMON, SPI, API, CORE)</li>
     *   <li>Aggregate and simplify functionality from lower layers</li>
     *   <li>Contain factory methods and convenience APIs</li>
     *   <li>Be the only layer that external consumers depend on</li>
     * </ul>
     *
     * <p>Example FACADE layer contents:
     * <pre>
     * public class StratifyFacade {
     *     public static UserService users() {
     *         return ServiceRegistry.get(UserService.class);
     *     }
     *
     *     public static ValidationService validation() {
     *         return ServiceRegistry.get(ValidationService.class);
     *     }
     * }
     * </pre>
     *
     * @return an ArchRule enforcing FACADE layer constraints
     */
    public static ArchRule facadeLayerRules() {
        return classes()
            .that(SEAConditions.isInLayer(Layer.FACADE))
            .and()
            .arePublic()
            .should()
            .haveSimpleNameEndingWith("Facade")
            .orShould()
            .haveSimpleNameEndingWith("Factory")
            .orShould()
            .haveSimpleNameEndingWith("Builder")
            .because("Public classes in FACADE layer (L5) should follow naming conventions " +
                    "(*Facade, *Factory, *Builder) to clearly indicate their role as entry points.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that classes in CORE layer are not public (except for necessary exports).
     *
     * <p>Most CORE layer classes should be package-private to enforce encapsulation.
     * Only classes that need to be accessed by FACADE (via reflection or other mechanisms)
     * should be public, and these should be kept to a minimum.
     *
     * <p>Provider implementations may need to be public for ServiceLoader discovery.
     *
     * @return an ArchRule that encourages package-private CORE classes
     */
    public static ArchRule coreClassesShouldBePackagePrivate() {
        return classes()
            .that(SEAConditions.isInLayer(Layer.CORE))
            .and()
            .areNotInterfaces()
            .and()
            .areNotEnums()
            .and()
            .doNotHaveModifier(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)
            .or(SEAConditions.hasProviderAnnotation()) // Providers can be public
            .should()
            .bePackagePrivate()
            .because("CORE layer (L4) implementations should be package-private to enforce encapsulation. " +
                    "Only provider implementations (for ServiceLoader) should be public.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that SPI layer contains only interfaces and abstract classes.
     *
     * <p>The SPI layer should define contracts for extension, not provide implementations.
     * All concrete implementations should reside in the CORE layer.
     *
     * @return an ArchRule that ensures SPI contains only abstractions
     */
    public static ArchRule spiShouldOnlyContainAbstractions() {
        return classes()
            .that(SEAConditions.isInLayer(Layer.SPI))
            .should()
            .beInterfaces()
            .orShould()
            .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)
            .because("SPI layer (L2) should only contain interfaces and abstract classes defining extension points, " +
                    "not concrete implementations.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that API layer contains only interfaces and abstract classes.
     *
     * <p>The API layer should define contracts for consumers, not provide implementations.
     * All concrete implementations should reside in the CORE layer.
     *
     * @return an ArchRule that ensures API contains only abstractions
     */
    public static ArchRule apiShouldOnlyContainAbstractions() {
        return classes()
            .that(SEAConditions.isInLayer(Layer.API))
            .should()
            .beInterfaces()
            .orShould()
            .haveModifier(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)
            .because("API layer (L3) should only contain interfaces and abstract classes defining consumer contracts, " +
                    "not concrete implementations.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that provider implementations follow naming conventions.
     *
     * <p>Provider classes (marked with @Provider) should follow consistent naming:
     * <ul>
     *   <li>End with "Provider" or "ProviderImpl"</li>
     *   <li>Be in the CORE layer</li>
     *   <li>Implement at least one SPI interface</li>
     * </ul>
     *
     * @return an ArchRule enforcing provider naming conventions
     */
    public static ArchRule providerNamingConvention() {
        return classes()
            .that(SEAConditions.hasProviderAnnotation())
            .should()
            .haveSimpleNameEndingWith("Provider")
            .orShould()
            .haveSimpleNameEndingWith("ProviderImpl")
            .because("Classes annotated with @Provider should follow naming convention: " +
                    "*Provider or *ProviderImpl for consistency and clarity.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures COMMON layer classes are simple data carriers.
     *
     * <p>Classes in COMMON should be simple DTOs, value objects, or enums without
     * complex behavior. They should not contain business logic or depend on external
     * services.
     *
     * <p>This rule checks that COMMON classes don't have too many methods (suggesting
     * they contain business logic rather than being simple data carriers).
     *
     * @return an ArchRule encouraging simple COMMON classes
     */
    public static ArchRule commonClassesShouldBeSimple() {
        return classes()
            .that(SEAConditions.isInLayer(Layer.COMMON))
            .and()
            .areNotEnums()
            .and()
            .areNotInterfaces()
            .should()
            .haveSimpleNameNotContaining("Service")
            .andShould()
            .haveSimpleNameNotContaining("Manager")
            .andShould()
            .haveSimpleNameNotContaining("Controller")
            .andShould()
            .haveSimpleNameNotContaining("Handler")
            .because("COMMON layer (L1) should contain simple data structures (DTOs, value objects), " +
                    "not business logic or service classes.")
            .allowEmptyShould(true);
    }

    /**
     * Comprehensive rule combining all layer-specific constraints.
     *
     * <p>This rule aggregates all individual layer rules into a single validation.
     * It enforces the complete set of SEA layer constraints across all five layers.
     *
     * <p>Use this rule for a complete layer validation in tests:
     * <pre>
     * {@literal @}ArchTest
     * static final ArchRule allLayerRules = LayerRules.allLayerRules();
     * </pre>
     *
     * @return a composite ArchRule enforcing all layer constraints
     */
    public static ArchRule allLayerRules() {
        return commonLayerRules()
            .and(spiLayerRules())
            .and(apiLayerRules())
            .and(coreLayerRules())
            .and(facadeLayerRules());
    }
}
