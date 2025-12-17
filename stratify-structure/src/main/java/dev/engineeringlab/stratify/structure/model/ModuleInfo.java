package dev.engineeringlab.stratify.structure.model;

import java.nio.file.Path;

/**
 * Represents metadata about a stratified module's structure.
 *
 * <p>A ModuleInfo captures information about which submodules are present in a stratified module
 * and provides helper methods to check compliance with various architectural patterns.
 *
 * <p>For SEA-4 compliance, a module must:
 *
 * <ul>
 *   <li>Have an API submodule
 *   <li>Have a core submodule
 *   <li>NOT have a common submodule (SEA-4 rule)
 * </ul>
 *
 * @param baseName the base name of the module (e.g., "text-processor")
 * @param path the file system path to the module directory
 * @param hasApi whether the module has an api submodule
 * @param hasCore whether the module has a core submodule
 * @param hasFacade whether the module has a facade submodule
 * @param hasSpi whether the module has an spi submodule
 * @param hasCommon whether the module has a common submodule (should be false for SEA-4)
 * @param hasUtil whether the module has a util submodule
 */
public record ModuleInfo(
    String baseName,
    Path path,
    boolean hasApi,
    boolean hasCore,
    boolean hasFacade,
    boolean hasSpi,
    boolean hasCommon,
    boolean hasUtil) {
  /** Creates a module info with compact constructor validation. */
  public ModuleInfo {
    if (baseName == null || baseName.isBlank()) {
      throw new IllegalArgumentException("baseName cannot be null or blank");
    }
    if (path == null) {
      throw new IllegalArgumentException("path cannot be null");
    }
  }

  /**
   * Creates a builder for constructing ModuleInfo instances.
   *
   * @param baseName the base name of the module
   * @param path the path to the module
   * @return a new builder instance
   */
  public static Builder builder(String baseName, Path path) {
    return new Builder(baseName, path);
  }

  /**
   * Checks if the module has all required submodules for a complete stratified module.
   *
   * <p>A complete module has both api and core submodules at minimum.
   *
   * @return true if the module has api and core submodules
   */
  public boolean isComplete() {
    return hasApi && hasCore;
  }

  /**
   * Checks if the module is compliant with SEA-4 architectural rules.
   *
   * <p>SEA-4 compliance requires:
   *
   * <ul>
   *   <li>The module must be complete (has api and core)
   *   <li>The module must NOT have a common submodule
   * </ul>
   *
   * <p>The presence of facade, spi, or util submodules does not affect SEA-4 compliance.
   *
   * @return true if the module is SEA-4 compliant
   */
  public boolean isSea4Compliant() {
    return isComplete() && !hasCommon;
  }

  /**
   * Checks if the module has any submodules at all.
   *
   * @return true if at least one submodule exists
   */
  public boolean hasAnySubmodules() {
    return hasApi || hasCore || hasFacade || hasSpi || hasCommon || hasUtil;
  }

  /**
   * Checks if the module has a facade submodule.
   *
   * <p>Facade is typically used for simplified external APIs.
   *
   * @return true if the module has a facade submodule
   */
  public boolean hasFacadePattern() {
    return hasFacade;
  }

  /**
   * Checks if the module has an SPI submodule.
   *
   * <p>SPI (Service Provider Interface) is used for extensibility.
   *
   * @return true if the module has an spi submodule
   */
  public boolean hasSpiPattern() {
    return hasSpi;
  }

  /**
   * Returns the count of present submodules.
   *
   * @return the number of submodules that exist
   */
  public int submoduleCount() {
    int count = 0;
    if (hasApi) count++;
    if (hasCore) count++;
    if (hasFacade) count++;
    if (hasSpi) count++;
    if (hasCommon) count++;
    if (hasUtil) count++;
    return count;
  }

  /** Builder class for creating ModuleInfo instances. */
  public static class Builder {
    private final String baseName;
    private final Path path;
    private boolean hasApi;
    private boolean hasCore;
    private boolean hasFacade;
    private boolean hasSpi;
    private boolean hasCommon;
    private boolean hasUtil;

    private Builder(String baseName, Path path) {
      this.baseName = baseName;
      this.path = path;
    }

    /**
     * Sets whether the module has an api submodule.
     *
     * @param hasApi true if api submodule exists
     * @return this builder
     */
    public Builder hasApi(boolean hasApi) {
      this.hasApi = hasApi;
      return this;
    }

    /**
     * Sets whether the module has a core submodule.
     *
     * @param hasCore true if core submodule exists
     * @return this builder
     */
    public Builder hasCore(boolean hasCore) {
      this.hasCore = hasCore;
      return this;
    }

    /**
     * Sets whether the module has a facade submodule.
     *
     * @param hasFacade true if facade submodule exists
     * @return this builder
     */
    public Builder hasFacade(boolean hasFacade) {
      this.hasFacade = hasFacade;
      return this;
    }

    /**
     * Sets whether the module has an spi submodule.
     *
     * @param hasSpi true if spi submodule exists
     * @return this builder
     */
    public Builder hasSpi(boolean hasSpi) {
      this.hasSpi = hasSpi;
      return this;
    }

    /**
     * Sets whether the module has a common submodule.
     *
     * @param hasCommon true if common submodule exists
     * @return this builder
     */
    public Builder hasCommon(boolean hasCommon) {
      this.hasCommon = hasCommon;
      return this;
    }

    /**
     * Sets whether the module has a util submodule.
     *
     * @param hasUtil true if util submodule exists
     * @return this builder
     */
    public Builder hasUtil(boolean hasUtil) {
      this.hasUtil = hasUtil;
      return this;
    }

    /**
     * Builds the ModuleInfo instance.
     *
     * @return a new ModuleInfo instance
     */
    public ModuleInfo build() {
      return new ModuleInfo(baseName, path, hasApi, hasCore, hasFacade, hasSpi, hasCommon, hasUtil);
    }
  }
}
