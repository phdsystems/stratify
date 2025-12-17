package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as infrastructure plumbing (factories, registries, configuration).
 *
 * <p>Components provide supporting functionality but do not contain business logic.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Component(name = "model-registry", description = "Stores model metadata")
 * public class ModelRegistry {
 *     private final Map<String, ModelInfo> models = new ConcurrentHashMap<>();
 *
 *     public void register(String id, ModelInfo model) { models.put(id, model); }
 *     public ModelInfo get(String id) { return models.get(id); }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Component {

  /**
   * Unique name for this component.
   *
   * @return component name
   */
  String name() default "";

  /**
   * Human-readable description.
   *
   * @return component description
   */
  String description() default "";

  /**
   * Instance lifecycle scope: "singleton" (default) or "prototype".
   *
   * @return component scope
   */
  String scope() default "singleton";
}
