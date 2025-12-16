package dev.engineeringlab.stratify.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import dev.engineeringlab.stratify.annotation.Layer;
import dev.engineeringlab.stratify.annotation.Provider;

/**
 * Helper conditions for building SEA (Stratified Encapsulation Architecture) ArchUnit rules.
 *
 * <p>This class provides reusable predicates and conditions for identifying:
 * <ul>
 *   <li>Classes belonging to specific SEA layers</li>
 *   <li>Provider implementations</li>
 *   <li>Facade classes that serve as public entry points</li>
 * </ul>
 *
 * <p>These conditions are building blocks for creating comprehensive architectural rules
 * that enforce SEA principles.
 *
 * @see SEARules
 * @see LayerRules
 * @see DependencyRules
 */
public final class SEAConditions {

    private SEAConditions() {
        throw new AssertionError("Utility class should not be instantiated");
    }

    /**
     * Creates a predicate that matches classes in the specified SEA layer.
     *
     * <p>The layer is determined by the module suffix in the package name.
     * For example, a class in package {@code com.example.mymodule.common} belongs to the COMMON layer.
     *
     * <p>Package naming conventions:
     * <ul>
     *   <li>COMMON (L1): {@code *.common.*}</li>
     *   <li>SPI (L2): {@code *.spi.*}</li>
     *   <li>API (L3): {@code *.api.*}</li>
     *   <li>CORE (L4): {@code *.core.*}</li>
     *   <li>FACADE (L5): {@code *.facade.*}</li>
     * </ul>
     *
     * @param layer the SEA layer to match
     * @return a predicate that identifies classes in the specified layer
     */
    public static DescribedPredicate<JavaClass> isInLayer(Layer layer) {
        String layerSuffix = layer.suffix();
        return DescribedPredicate.describe(
            "is in " + layer.name() + " layer",
            javaClass -> {
                String packageName = javaClass.getPackageName();
                // Match patterns: *.{suffix}.* or *.{suffix} or *-{suffix}.*
                return packageName.contains("." + layerSuffix + ".") ||
                       packageName.endsWith("." + layerSuffix) ||
                       packageName.contains("-" + layerSuffix + ".") ||
                       packageName.endsWith("-" + layerSuffix);
            }
        );
    }

    /**
     * Creates a predicate that matches classes annotated with {@link Provider}.
     *
     * <p>Provider classes are SPI implementations that can be discovered and loaded
     * at runtime. They should follow specific naming and structural conventions.
     *
     * @return a predicate that identifies provider classes
     */
    public static DescribedPredicate<JavaClass> hasProviderAnnotation() {
        return DescribedPredicate.describe(
            "has @Provider annotation",
            javaClass -> javaClass.isAnnotatedWith(Provider.class)
        );
    }

    /**
     * Creates a predicate that matches facade classes.
     *
     * <p>Facade classes are the public entry points in the FACADE layer (L5).
     * They serve as the only externally visible API surface and should aggregate
     * functionality from lower layers.
     *
     * <p>A class is considered a facade if:
     * <ul>
     *   <li>It is in the FACADE layer (L5)</li>
     *   <li>It is a public class</li>
     *   <li>Its name ends with "Facade" or it is in a package ending with "facade"</li>
     * </ul>
     *
     * @return a predicate that identifies facade classes
     */
    public static DescribedPredicate<JavaClass> isFacadeClass() {
        return DescribedPredicate.describe(
            "is a facade class",
            javaClass -> {
                // Must be in facade layer
                if (!isInLayer(Layer.FACADE).test(javaClass)) {
                    return false;
                }

                // Must be public
                if (!javaClass.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                    return false;
                }

                // Name should end with "Facade" or be in facade package
                String simpleName = javaClass.getSimpleName();
                String packageName = javaClass.getPackageName();

                return simpleName.endsWith("Facade") ||
                       packageName.endsWith(".facade") ||
                       packageName.endsWith("-facade");
            }
        );
    }

    /**
     * Creates a predicate that matches classes that should be package-private.
     *
     * <p>In SEA, most implementation classes should be package-private to enforce
     * encapsulation. Only facade classes and explicitly exported interfaces should be public.
     *
     * @return a predicate that identifies classes that should not be public
     */
    public static DescribedPredicate<JavaClass> shouldBePackagePrivate() {
        return DescribedPredicate.describe(
            "should be package-private",
            javaClass -> {
                // Facade classes can be public
                if (isFacadeClass().test(javaClass)) {
                    return false;
                }

                // Interfaces in API and SPI layers can be public
                if (javaClass.isInterface()) {
                    return !isInLayer(Layer.API).test(javaClass) &&
                           !isInLayer(Layer.SPI).test(javaClass);
                }

                // All other classes should be package-private
                return true;
            }
        );
    }

    /**
     * Creates a predicate that matches classes that can be accessed from outside their module.
     *
     * <p>In SEA, only these classes should be accessible externally:
     * <ul>
     *   <li>Facade classes (L5) - main entry points</li>
     *   <li>API interfaces (L3) - consumer contracts</li>
     *   <li>SPI interfaces (L2) - extension points</li>
     *   <li>Common types (L1) - shared data structures</li>
     * </ul>
     *
     * @return a predicate that identifies externally accessible classes
     */
    public static DescribedPredicate<JavaClass> isExternallyAccessible() {
        return DescribedPredicate.describe(
            "is externally accessible",
            javaClass -> {
                // Facade classes are always accessible
                if (isFacadeClass().test(javaClass)) {
                    return true;
                }

                // Public interfaces in API, SPI, and COMMON are accessible
                if (javaClass.isInterface() &&
                    javaClass.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                    return isInLayer(Layer.API).test(javaClass) ||
                           isInLayer(Layer.SPI).test(javaClass) ||
                           isInLayer(Layer.COMMON).test(javaClass);
                }

                // Public classes in COMMON (DTOs, value objects) are accessible
                if (isInLayer(Layer.COMMON).test(javaClass) &&
                    javaClass.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.PUBLIC)) {
                    return true;
                }

                return false;
            }
        );
    }

    /**
     * Creates a predicate that matches provider implementation classes.
     *
     * <p>Provider implementations should:
     * <ul>
     *   <li>Be in the CORE layer (L4)</li>
     *   <li>Have the @Provider annotation</li>
     *   <li>Implement at least one SPI interface</li>
     *   <li>Follow naming convention: *Provider</li>
     * </ul>
     *
     * @return a predicate that identifies provider implementation classes
     */
    public static DescribedPredicate<JavaClass> isProviderImplementation() {
        return DescribedPredicate.describe(
            "is a provider implementation",
            javaClass -> {
                // Must have @Provider annotation
                if (!hasProviderAnnotation().test(javaClass)) {
                    return false;
                }

                // Should be in CORE layer
                if (!isInLayer(Layer.CORE).test(javaClass)) {
                    return false;
                }

                // Should follow naming convention
                String simpleName = javaClass.getSimpleName();
                return simpleName.endsWith("Provider") || simpleName.endsWith("ProviderImpl");
            }
        );
    }

    /**
     * Creates a predicate that matches classes in a layer that are allowed to depend on another layer.
     *
     * <p>Based on SEA dependency rules:
     * <ul>
     *   <li>COMMON (L1) - can depend on external libraries only</li>
     *   <li>SPI (L2) - can depend on COMMON</li>
     *   <li>API (L3) - can depend on COMMON, SPI</li>
     *   <li>CORE (L4) - can depend on COMMON, SPI, API</li>
     *   <li>FACADE (L5) - can depend on all layers</li>
     * </ul>
     *
     * @param fromLayer the source layer
     * @param toLayer the target layer
     * @return a predicate indicating if the dependency is allowed
     */
    public static DescribedPredicate<JavaClass> canDependOn(Layer fromLayer, Layer toLayer) {
        return DescribedPredicate.describe(
            fromLayer.name() + " can depend on " + toLayer.name(),
            javaClass -> fromLayer.canDependOn(toLayer)
        );
    }
}
