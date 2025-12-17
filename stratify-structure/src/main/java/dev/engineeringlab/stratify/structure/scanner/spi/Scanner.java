package dev.engineeringlab.stratify.structure.scanner.spi;

import java.util.List;

/**
 * Base interface for all scanners.
 *
 * <p>Scanners analyze targets (e.g., Maven modules, source code, configurations) and detect
 * violations of rules, conventions, or best practices.
 *
 * @param <T> the type of target being scanned (e.g., ModuleInfo, SourceFile)
 */
public interface Scanner<T> {

  /**
   * Scans the given target for violations.
   *
   * @param target the target to scan
   * @return list of violations (empty if compliant)
   */
  List<Violation> scan(T target);

  /**
   * Gets the scanner identifier (e.g., "NC-001", "MS-015").
   *
   * @return the scanner ID
   */
  String getScannerId();

  /**
   * Gets the scanner name.
   *
   * @return human-readable name
   */
  String getName();

  /**
   * Gets a human-readable description of what this scanner detects.
   *
   * @return the scanner description (defaults to scanner name)
   */
  default String getDescription() {
    return getName();
  }

  /**
   * Gets the scanner category.
   *
   * @return the category (e.g., DEPENDENCIES, STRUCTURE)
   */
  Category getCategory();

  /**
   * Gets the severity level of violations from this scanner.
   *
   * @return the severity level
   */
  Severity getSeverity();

  /**
   * Checks if this scanner is enabled.
   *
   * @return true if enabled (default: true)
   */
  default boolean isEnabled() {
    return true;
  }
}
