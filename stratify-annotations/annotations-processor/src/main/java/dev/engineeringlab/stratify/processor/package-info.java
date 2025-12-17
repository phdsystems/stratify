/**
 * Compile-time annotation processors for Stratify SEA framework.
 *
 * <p>This package contains annotation processors that auto-generate META-INF/services files for
 * ServiceLoader discovery of provider implementations.
 *
 * <h2>Available Processors</h2>
 *
 * <ul>
 *   <li>{@link dev.engineeringlab.stratify.processor.ProviderProcessor} - Processes
 *       {@code @Provider} annotations
 * </ul>
 *
 * <h2>How It Works</h2>
 *
 * <p>During compilation, the processors scan for annotated classes and generate {@code
 * META-INF/services/} files containing the fully qualified names of implementations. This enables
 * automatic discovery via {@link java.util.ServiceLoader}.
 *
 * @since 1.0.0
 */
package dev.engineeringlab.stratify.processor;
