package dev.engineeringlab.stratify.structure.scanner.spi;

/** Categories for grouping related scanners. */
public enum Category {
  /** Dependency management scanners. */
  DEPENDENCIES,

  /** Module/package structure scanners. */
  STRUCTURE,

  /** Naming convention scanners. */
  NAMING,

  /** Code quality scanners. */
  QUALITY,

  /** Security-related scanners. */
  SECURITY,

  /** Performance-related scanners. */
  PERFORMANCE,

  /** Configuration scanners. */
  CONFIGURATION
}
