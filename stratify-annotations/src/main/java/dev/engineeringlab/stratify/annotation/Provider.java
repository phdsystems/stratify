package dev.engineeringlab.stratify.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an SPI provider implementation.
 *
 * <p>Providers are implementations of SPI (Service Provider Interface) contracts
 * that can be discovered and loaded at runtime. This annotation provides metadata
 * for provider discovery, selection, and management.
 *
 * <p>Example usage:
 * <pre>
 * {@literal @}Provider(
 *     name = "openai",
 *     priority = 10,
 *     description = "OpenAI GPT provider"
 * )
 * public class OpenAIProvider implements TextGenerationProvider {
 *     // Implementation
 * }
 * </pre>
 *
 * <p>Providers are typically discovered via ServiceLoader or annotation scanning
 * and selected based on priority (higher priority = preferred).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Provider {

    /**
     * Unique identifier for this provider.
     * <p>Used for explicit provider selection and configuration lookup.
     *
     * @return the provider name
     */
    String name();

    /**
     * Provider selection priority (higher = more preferred).
     * <p>When multiple providers are available, the one with highest priority
     * is selected as the default.
     *
     * @return the priority value (default: 0)
     */
    int priority() default 0;

    /**
     * Human-readable description of the provider.
     *
     * @return the description
     */
    String description() default "";

    /**
     * Whether this provider is enabled by default.
     * <p>Disabled providers must be explicitly enabled via configuration.
     *
     * @return true if enabled by default
     */
    boolean enabledByDefault() default true;

    /**
     * Optional tags for categorization and filtering.
     *
     * @return array of tags
     */
    String[] tags() default {};
}
