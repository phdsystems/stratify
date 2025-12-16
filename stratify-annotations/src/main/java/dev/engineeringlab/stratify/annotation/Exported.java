package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type as explicitly exported in the public API.
 *
 * <p>Types marked with {@code @Exported} are part of the module's stable public API. They are
 * intended to be used by consumers and will maintain backward compatibility within the same major
 * version.
 *
 * <p>This annotation is typically used on types in lower layers (COMMON, API) that are re-exported
 * through the FACADE layer.
 *
 * <p>Example usage:
 *
 * <pre>
 * {@literal @}Exported
 * public record TextGenerationRequest(
 *     String prompt,
 *     String model,
 *     Double temperature
 * ) {}
 * </pre>
 *
 * <p>Tooling can use this annotation to:
 *
 * <ul>
 *   <li>Generate API documentation
 *   <li>Validate that exported types follow naming conventions
 *   <li>Check backward compatibility between versions
 *   <li>Ensure exports are properly re-exported through facade
 * </ul>
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Exported {

  /**
   * Version when this type was first exported.
   *
   * @return the version string
   */
  String since() default "";

  /**
   * Stability level of this export.
   *
   * @return the stability level
   */
  Stability stability() default Stability.STABLE;

  /** Stability levels for exported types. */
  enum Stability {
    /** Fully stable, backward compatible within major version. */
    STABLE,

    /** API may change in minor versions. Use with caution. */
    BETA,

    /** Experimental API, may change or be removed at any time. */
    EXPERIMENTAL
  }
}
