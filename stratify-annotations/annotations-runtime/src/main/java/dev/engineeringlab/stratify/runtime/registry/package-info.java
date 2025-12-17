/**
 * Runtime service discovery and registry utilities.
 *
 * <p>This package provides the runtime infrastructure for discovering and accessing service
 * implementations via Java's {@link java.util.ServiceLoader} mechanism.
 *
 * <h2>Key Classes</h2>
 *
 * <ul>
 *   <li>{@link dev.engineeringlab.stratify.runtime.registry.ServiceDiscovery} - Core service
 *       discovery with priority-based selection
 *   <li>{@link dev.engineeringlab.stratify.runtime.registry.Services} - Simplified facade for
 *       service lookup
 * </ul>
 *
 * @since 1.0.0
 */
package dev.engineeringlab.stratify.runtime.registry;
