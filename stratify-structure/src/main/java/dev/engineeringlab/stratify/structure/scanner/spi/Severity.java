package dev.engineeringlab.stratify.structure.scanner.spi;

/** Severity levels for violations. */
public enum Severity {
  /** Informational - no action required. */
  INFO,

  /** Warning - should be addressed but not blocking. */
  WARNING,

  /** Error - must be fixed, blocks compliance. */
  ERROR,

  /** Critical - severe issue requiring immediate attention. */
  CRITICAL
}
