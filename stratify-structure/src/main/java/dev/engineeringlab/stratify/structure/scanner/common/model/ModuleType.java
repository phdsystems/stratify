package dev.engineeringlab.stratify.structure.scanner.common.model;

/** Types of Maven modules in the architecture. */
public enum ModuleType {
  /** Parent/aggregator module with submodules. */
  PARENT("parent", null),

  /** API module containing interfaces and contracts. */
  API("api", "-api"),

  /** Core/implementation module containing business logic. */
  CORE("core", "-core"),

  /** Facade module providing consumer-facing API. */
  FACADE("facade", "-facade"),

  /** SPI module for service provider interfaces. */
  SPI("spi", "-spi"),

  /** Common module for shared types. */
  COMMON("common", "-common"),

  /** Unknown or non-standard module type. */
  UNKNOWN("unknown", null);

  private final String name;
  private final String suffix;

  ModuleType(String nameParam, String suffixParam) {
    this.name = nameParam;
    this.suffix = suffixParam;
  }

  /** Gets the module type name. */
  public String getName() {
    return name;
  }

  /** Gets the typical suffix for this module type (e.g., "-api"). */
  public String getSuffix() {
    return suffix;
  }

  /** Checks if a module name matches this type's suffix. */
  public boolean matches(String moduleName) {
    if (suffix == null || moduleName == null) {
      return false;
    }
    return moduleName.toLowerCase().endsWith(suffix);
  }

  /** Detects module type from module name. */
  public static ModuleType fromModuleName(String moduleName) {
    if (moduleName == null) {
      return UNKNOWN;
    }

    String lowerName = moduleName.toLowerCase();

    if (API.matches(lowerName)) {
      return API;
    } else if (CORE.matches(lowerName)) {
      return CORE;
    } else if (FACADE.matches(lowerName)) {
      return FACADE;
    } else if (SPI.matches(lowerName)) {
      return SPI;
    } else if (COMMON.matches(lowerName)) {
      return COMMON;
    }

    return UNKNOWN;
  }

  /** Checks if this module type should have minimal code (interfaces only). */
  public boolean isMinimalCode() {
    return this == API || this == FACADE || this == SPI;
  }

  /** Checks if this module type should have substantial implementation code. */
  public boolean hasImplementationCode() {
    return this == CORE || this == PARENT;
  }
}
