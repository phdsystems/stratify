package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a package or module as belonging to a specific SEA layer.
 *
 * <p>This annotation is used to declare the architectural role of a module
 * within the Stratified Encapsulation Architecture. It enables build-time
 * and runtime validation of layer dependencies.
 *
 * <p>Example usage on a package-info.java:
 * <pre>
 * {@literal @}SEAModule(layer = Layer.FACADE)
 * package com.example.mymodule;
 *
 * import dev.engineeringlab.stratify.annotation.SEAModule;
 * import dev.engineeringlab.stratify.annotation.Layer;
 * </pre>
 *
 * <p>Example usage on a module class:
 * <pre>
 * {@literal @}SEAModule(layer = Layer.CORE, name = "my-module-core")
 * public class MyModuleCore {
 *     // Module marker class
 * }
 * </pre>
 */
@Target({ElementType.PACKAGE, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SEAModule {

    /**
     * The SEA layer this module belongs to.
     *
     * @return the layer designation
     */
    Layer layer();

    /**
     * Optional module name for identification.
     * <p>If not specified, the name is derived from the package or class name.
     *
     * @return the module name
     */
    String name() default "";

    /**
     * Optional description of the module's purpose.
     *
     * @return the module description
     */
    String description() default "";
}
