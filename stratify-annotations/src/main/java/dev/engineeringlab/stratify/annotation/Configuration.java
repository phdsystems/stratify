package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a configuration holder.
 *
 * <p>Configuration classes hold settings, properties, and parameters that control behavior of
 * services, providers, and components.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @Configuration(name = "llm-client", prefix = "llm.client")
 * public class LLMClientConfig {
 *     private String apiKey;
 *     private String baseUrl = "https://api.openai.com";
 *     private Duration timeout = Duration.ofSeconds(30);
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Configuration {

  /**
   * Unique name for this configuration.
   *
   * @return configuration name
   */
  String name() default "";

  /**
   * Human-readable description.
   *
   * @return configuration description
   */
  String description() default "";

  /**
   * Property prefix for binding (e.g., "llm.client").
   *
   * @return property prefix
   */
  String prefix() default "";
}
