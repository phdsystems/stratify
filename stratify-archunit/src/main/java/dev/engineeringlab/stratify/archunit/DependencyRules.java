package dev.engineeringlab.stratify.archunit;

import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import dev.engineeringlab.stratify.annotation.Layer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Pre-built ArchUnit rules for SEA dependency constraints.
 *
 * <p>This class provides rules that enforce proper dependency relationships between
 * SEA layers, preventing architectural violations such as:
 * <ul>
 *   <li>API layer importing from CORE layer (skipping abstraction)</li>
 *   <li>FACADE layer directly exposing SPI interfaces (breaking encapsulation)</li>
 *   <li>COMMON layer having internal dependencies (violating foundation principle)</li>
 *   <li>Circular dependencies between layers or modules</li>
 * </ul>
 *
 * <p>All rules use {@code allowEmptyShould(true)} to gracefully handle projects
 * where certain layers or modules don't exist yet.
 *
 * @see SEARules
 * @see LayerRules
 * @see SEAConditions
 */
public final class DependencyRules {

    private DependencyRules() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Ensures that API layer classes do not import from CORE layer.
     *
     * <p>The API layer (L3) defines consumer contracts and should only depend on
     * COMMON (L1) and SPI (L2) layers. Importing from CORE would create tight
     * coupling and violate the layered architecture.
     *
     * <p>Violation example:
     * <pre>
     * // In API layer - WRONG
     * import com.example.core.UserServiceImpl; // Direct dependency on implementation
     *
     * // In API layer - CORRECT
     * public interface UserService {
     *     User findUser(String id);
     * }
     * </pre>
     *
     * @return an ArchRule that forbids API-to-CORE dependencies
     */
    public static ArchRule noCoreInApi() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.API))
            .should()
            .dependOnClassesThat(SEAConditions.isInLayer(Layer.CORE))
            .because("API layer (L3) must not depend on CORE layer (L4). " +
                    "API defines consumer contracts and should only depend on COMMON (L1) and SPI (L2).")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that FACADE layer does not directly expose SPI interfaces.
     *
     * <p>The FACADE layer (L5) is the public API surface. While it can depend on SPI
     * internally, it should not expose SPI types in its public interface. This prevents
     * consumers from depending on extension points directly.
     *
     * <p>Violation example:
     * <pre>
     * // In FACADE layer - WRONG
     * public class ServiceFacade {
     *     public TextGenerationProvider getProvider() { ... } // Exposes SPI type
     * }
     *
     * // In FACADE layer - CORRECT
     * public class ServiceFacade {
     *     public GeneratedText generate(String prompt) { ... } // Uses API types
     * }
     * </pre>
     *
     * <p>Note: This rule checks for SPI types in public method signatures and public fields.
     *
     * @return an ArchRule that forbids exposing SPI types in FACADE public interface
     */
    public static ArchRule noSpiInFacade() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.FACADE))
            .and()
            .arePublic()
            .should()
            .dependOnClassesThat(SEAConditions.isInLayer(Layer.SPI))
            .because("FACADE layer (L5) should not expose SPI layer (L2) types in its public API. " +
                    "Consumers should interact through high-level API (L3) abstractions.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that COMMON layer has no dependencies on other internal layers.
     *
     * <p>The COMMON layer (L1) is the foundation of the architecture and should only
     * depend on external libraries (e.g., Java standard library, third-party utilities).
     * It must not depend on any other SEA layer (SPI, API, CORE, FACADE).
     *
     * <p>Violation example:
     * <pre>
     * // In COMMON layer - WRONG
     * import com.example.spi.ValidationProvider; // Depends on SPI
     *
     * public class UserDTO {
     *     private ValidationProvider validator; // Should not reference SPI
     * }
     *
     * // In COMMON layer - CORRECT
     * public class UserDTO {
     *     private String id;
     *     private String name;
     *     // Plain data structure with no layer dependencies
     * }
     * </pre>
     *
     * @return an ArchRule that forbids COMMON layer dependencies on other SEA layers
     */
    public static ArchRule commonIsFoundation() {
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
                    "It should only contain shared types with external library dependencies.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that SPI layer only depends on COMMON layer.
     *
     * <p>The SPI layer (L2) defines extension points and should only depend on
     * shared types from COMMON (L1). It must not depend on API, CORE, or FACADE layers.
     *
     * <p>Violation example:
     * <pre>
     * // In SPI layer - WRONG
     * import com.example.api.UserService; // Depends on API
     *
     * public interface UserProvider {
     *     UserService createService(); // Returns API type
     * }
     *
     * // In SPI layer - CORRECT
     * import com.example.common.User; // Depends on COMMON
     *
     * public interface UserProvider {
     *     User loadUser(String id); // Returns COMMON type
     * }
     * </pre>
     *
     * @return an ArchRule that forbids SPI layer dependencies on API, CORE, or FACADE
     */
    public static ArchRule spiDependsOnlyOnCommon() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.SPI))
            .should()
            .dependOnClassesThat(
                SEAConditions.isInLayer(Layer.API)
                    .or(SEAConditions.isInLayer(Layer.CORE))
                    .or(SEAConditions.isInLayer(Layer.FACADE))
            )
            .because("SPI layer (L2) must only depend on COMMON layer (L1). " +
                    "It defines extension points and should not depend on higher layers.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that API layer does not depend on FACADE layer.
     *
     * <p>The API layer (L3) defines consumer contracts and should not depend on the
     * FACADE layer (L5). The FACADE aggregates and exposes API functionality, creating
     * a one-way dependency from FACADE to API, not vice versa.
     *
     * <p>This prevents circular dependencies and maintains proper layering.
     *
     * @return an ArchRule that forbids API-to-FACADE dependencies
     */
    public static ArchRule noFacadeInApi() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.API))
            .should()
            .dependOnClassesThat(SEAConditions.isInLayer(Layer.FACADE))
            .because("API layer (L3) must not depend on FACADE layer (L5). " +
                    "FACADE aggregates API functionality, creating a one-way dependency.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that CORE layer does not depend on FACADE layer.
     *
     * <p>The CORE layer (L4) contains implementations and should not depend on the
     * FACADE layer (L5). FACADE serves as the consumer-facing aggregation layer
     * that depends on CORE, not the other way around.
     *
     * @return an ArchRule that forbids CORE-to-FACADE dependencies
     */
    public static ArchRule noFacadeInCore() {
        return noClasses()
            .that(SEAConditions.isInLayer(Layer.CORE))
            .should()
            .dependOnClassesThat(SEAConditions.isInLayer(Layer.FACADE))
            .because("CORE layer (L4) must not depend on FACADE layer (L5). " +
                    "FACADE aggregates CORE functionality for consumers.")
            .allowEmptyShould(true);
    }

    /**
     * Detects circular dependencies between layer slices.
     *
     * <p>This rule ensures there are no circular dependencies between different parts
     * of the same layer. For example, if two packages in the API layer depend on each
     * other, it indicates a design problem.
     *
     * <p>The rule slices the codebase by layer packages and ensures no cycles exist.
     *
     * @return an ArchRule that detects circular dependencies within and between layers
     */
    public static ArchRule noCircularDependenciesBetweenLayers() {
        return SlicesRuleDefinition.slices()
            .matching("(**).(common|spi|api|core|facade).(*)")
            .should()
            .beFreeOfCycles()
            .because("Circular dependencies between layers violate SEA principles and make the code harder to understand and maintain.")
            .allowEmptyShould(true);
    }

    /**
     * Ensures proper upward-only dependencies in the layer hierarchy.
     *
     * <p>This comprehensive rule enforces that each layer only depends on layers below it:
     * <ul>
     *   <li>COMMON (L1) - no internal layer dependencies</li>
     *   <li>SPI (L2) - can only depend on COMMON</li>
     *   <li>API (L3) - can depend on COMMON, SPI</li>
     *   <li>CORE (L4) - can depend on COMMON, SPI, API</li>
     *   <li>FACADE (L5) - can depend on all layers</li>
     * </ul>
     *
     * <p>Any dependency that violates this hierarchy is forbidden.
     *
     * @return an ArchRule that enforces upward-only layer dependencies
     */
    public static ArchRule layerDependencyHierarchy() {
        return classes()
            .should()
            .onlyDependOnClassesThat(
                SEAConditions.canDependOn(Layer.FACADE, Layer.COMMON)
                    .or(SEAConditions.canDependOn(Layer.FACADE, Layer.SPI))
                    .or(SEAConditions.canDependOn(Layer.FACADE, Layer.API))
                    .or(SEAConditions.canDependOn(Layer.FACADE, Layer.CORE))
                    .or(SEAConditions.canDependOn(Layer.CORE, Layer.COMMON))
                    .or(SEAConditions.canDependOn(Layer.CORE, Layer.SPI))
                    .or(SEAConditions.canDependOn(Layer.CORE, Layer.API))
                    .or(SEAConditions.canDependOn(Layer.API, Layer.COMMON))
                    .or(SEAConditions.canDependOn(Layer.API, Layer.SPI))
                    .or(SEAConditions.canDependOn(Layer.SPI, Layer.COMMON))
                    // Allow dependencies on Java standard library and external libraries
                    .or(new com.tngtech.archunit.base.DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass>("is external library") {
                        @Override
                        public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                            String packageName = javaClass.getPackageName();
                            return packageName.startsWith("java.") ||
                                   packageName.startsWith("javax.") ||
                                   packageName.startsWith("jakarta.") ||
                                   !packageName.contains(".common.") &&
                                   !packageName.contains(".spi.") &&
                                   !packageName.contains(".api.") &&
                                   !packageName.contains(".core.") &&
                                   !packageName.contains(".facade.");
                        }
                    })
            )
            .because("Each SEA layer must only depend on layers below it in the hierarchy: " +
                    "COMMON (L1) <- SPI (L2) <- API (L3) <- CORE (L4) <- FACADE (L5)")
            .allowEmptyShould(true);
    }

    /**
     * Ensures that provider implementations in CORE only implement interfaces from SPI.
     *
     * <p>Provider classes (marked with @Provider annotation) should implement SPI interfaces,
     * not API interfaces. This maintains proper separation between extension points (SPI)
     * and consumer contracts (API).
     *
     * @return an ArchRule that ensures providers implement SPI interfaces
     */
    public static ArchRule providersImplementSpiInterfaces() {
        return classes()
            .that(SEAConditions.hasProviderAnnotation())
            .and(SEAConditions.isInLayer(Layer.CORE))
            .should()
            .implement(new com.tngtech.archunit.core.domain.properties.HasType.Predicates.rawType(
                SEAConditions.isInLayer(Layer.SPI)
            ))
            .because("Provider implementations should implement SPI (L2) interfaces, " +
                    "maintaining separation between extension points and consumer contracts.")
            .allowEmptyShould(true);
    }
}
