package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a facade entry point.
 *
 * <p>Facade classes are the primary public API surface of an SEA module. They provide simplified,
 * opinionated access to the module's functionality while hiding internal complexity.
 *
 * <p>Facade classes should be:
 *
 * <ul>
 *   <li>Final (to prevent extension)
 *   <li>Contain only static methods or act as factories
 *   <li>Re-export necessary types from lower layers
 *   <li>Provide convenience methods for common use cases
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * {@literal @}Facade
 * public final class LLM {
 *
 *     private LLM() {} // Prevent instantiation
 *
 *     public static Mono{@literal <}String{@literal >} generate(String prompt) {
 *         return getDefaultProvider().generate(prompt);
 *     }
 *
 *     public static LLMClient using(String providerName) {
 *         return ProviderRegistry.get(providerName);
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Facade {

  /**
   * Optional name for this facade.
   *
   * <p>If not specified, the class simple name is used.
   *
   * @return the facade name
   */
  String name() default "";

  /**
   * Description of what this facade provides.
   *
   * @return the description
   */
  String description() default "";
}
