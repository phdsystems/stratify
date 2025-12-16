package dev.engineeringlab.stratify.annotation;

/**
 * Defines the five layers of the Stratified Encapsulation Architecture (SEA).
 *
 * <p>The layers form a strict dependency hierarchy:
 * <pre>
 *     FACADE (L5) - Consumer entry point
 *        │
 *     CORE (L4) - Implementation
 *        │
 *     API (L3) - Consumer contracts
 *        │
 *     SPI (L2) - Extension points
 *        │
 *     COMMON (L1) - Foundation
 * </pre>
 *
 * <p>Each layer can only depend on layers below it.
 */
public enum Layer {

    /**
     * L1: Foundation layer containing shared types.
     * <p>Contains: DTOs, value objects, error types, configuration structs, constants.
     * <p>Dependencies: External libraries only.
     */
    COMMON(1, "common"),

    /**
     * L2: Service Provider Interface layer for extension points.
     * <p>Contains: Provider interfaces, extension interfaces, hooks for customization.
     * <p>Dependencies: COMMON only.
     */
    SPI(2, "spi"),

    /**
     * L3: API layer defining consumer contracts.
     * <p>Contains: High-level interfaces for consumers, service interfaces.
     * <p>Dependencies: COMMON, SPI.
     */
    API(3, "api"),

    /**
     * L4: Core implementation layer.
     * <p>Contains: Concrete implementations, provider implementations, internal utilities.
     * <p>Dependencies: COMMON, SPI, API.
     * <p>Exports: Only to FACADE.
     */
    CORE(4, "core"),

    /**
     * L5: Facade layer - the only externally visible layer.
     * <p>Contains: Re-exports, factory functions, convenience APIs.
     * <p>Dependencies: All lower layers (aggregates everything).
     * <p>This is the ONLY layer consumers should depend on.
     */
    FACADE(5, "facade");

    private final int level;
    private final String suffix;

    Layer(int level, String suffix) {
        this.level = level;
        this.suffix = suffix;
    }

    /**
     * Returns the numeric level of this layer (1-5).
     *
     * @return the layer level
     */
    public int level() {
        return level;
    }

    /**
     * Returns the conventional module suffix for this layer.
     *
     * @return the suffix (e.g., "common", "spi", "api", "core", "facade")
     */
    public String suffix() {
        return suffix;
    }

    /**
     * Checks if this layer can depend on the specified layer.
     *
     * @param other the layer to check dependency against
     * @return true if this layer can depend on the other layer
     */
    public boolean canDependOn(Layer other) {
        return this.level > other.level;
    }

    /**
     * Finds a layer by its suffix.
     *
     * @param suffix the suffix to search for
     * @return the matching layer, or null if not found
     */
    public static Layer fromSuffix(String suffix) {
        for (Layer layer : values()) {
            if (layer.suffix.equalsIgnoreCase(suffix)) {
                return layer;
            }
        }
        return null;
    }
}
