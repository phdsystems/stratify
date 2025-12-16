/**
 * Annotations for the Stratified Encapsulation Architecture (SEA).
 *
 * <p>This package provides compile-time and runtime annotations for marking
 * and validating SEA module layers and components.
 *
 * <h2>Core Annotations</h2>
 * <ul>
 *   <li>{@link dev.engineeringlab.stratify.annotation.SEAModule} - Marks a module's layer</li>
 *   <li>{@link dev.engineeringlab.stratify.annotation.Layer} - Enum of the 5 SEA layers</li>
 *   <li>{@link dev.engineeringlab.stratify.annotation.Provider} - Marks SPI provider implementations</li>
 *   <li>{@link dev.engineeringlab.stratify.annotation.Facade} - Marks facade entry points</li>
 *   <li>{@link dev.engineeringlab.stratify.annotation.Internal} - Marks internal implementation details</li>
 *   <li>{@link dev.engineeringlab.stratify.annotation.Exported} - Marks explicitly exported types</li>
 * </ul>
 *
 * <h2>SEA Layer Hierarchy</h2>
 * <pre>
 * L5: FACADE  - Consumer entry point (ONLY externally visible)
 * L4: CORE    - Implementation (exports nothing directly)
 * L3: API     - Consumer contracts
 * L2: SPI     - Extension points (provider interfaces)
 * L1: COMMON  - Foundation (DTOs, models, exceptions)
 * </pre>
 *
 * @see <a href="https://github.com/phdsystems/stratify">Stratify Documentation</a>
 */
package dev.engineeringlab.stratify.annotation;
