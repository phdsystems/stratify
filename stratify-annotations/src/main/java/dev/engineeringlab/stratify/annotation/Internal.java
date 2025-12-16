package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a type or member as internal implementation detail.
 *
 * <p>Types marked with {@code @Internal} are not part of the public API and may change or be
 * removed without notice. Consumers should not depend on these types directly.
 *
 * <p>This annotation serves as documentation and enables tooling to:
 *
 * <ul>
 *   <li>Generate warnings when internal types are used externally
 *   <li>Exclude internal types from public API documentation
 *   <li>Validate that internal types are not leaked through public APIs
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * {@literal @}Internal
 * public class InternalRequestProcessor {
 *     // Implementation details not for external use
 * }
 * </pre>
 *
 * <p>Note: For JPMS modules, use {@code exports ... to} for compile-time enforcement. This
 * annotation provides additional runtime metadata and tooling support.
 */
@Target({
  ElementType.TYPE,
  ElementType.METHOD,
  ElementType.FIELD,
  ElementType.CONSTRUCTOR,
  ElementType.PACKAGE
})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Internal {

  /**
   * Reason why this element is internal.
   *
   * @return the reason
   */
  String reason() default "";

  /**
   * Version since which this has been internal.
   *
   * @return the version string
   */
  String since() default "";
}
