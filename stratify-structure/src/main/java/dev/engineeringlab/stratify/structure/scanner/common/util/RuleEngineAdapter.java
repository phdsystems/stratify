package dev.engineeringlab.stratify.structure.scanner.common.util;

import dev.engineeringlab.stratify.structure.scanner.common.model.Severity;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import dev.engineeringlab.stratify.structure.scanner.common.model.ViolationCategory;

/**
 * Utility class for converting between scanner SPI types and scanner-common types.
 *
 * <p>This adapter provides bidirectional conversion between the canonical types in the scanner SPI
 * module and the scanner-specific types. It allows scanners to use the pluggable scanner engine
 * while maintaining their own violation format.
 *
 * <h2>Usage Example</h2>
 *
 * <pre>{@code
 * // Converting from scanner SPI violation to scanner violation
 * dev.engineeringlab.stratify.structure.scanner.spi.Violation scannerSpiViolation = ...;
 * Violation scannerViolation = RuleEngineAdapter.toScannerViolation(scannerSpiViolation);
 *
 * // Converting severity
 * dev.engineeringlab.stratify.structure.scanner.spi.Severity scannerSpiSeverity = ...;
 * Severity scannerSeverity = RuleEngineAdapter.toScannerSeverity(scannerSpiSeverity);
 * }</pre>
 *
 * <h2>Type Mappings</h2>
 *
 * <ul>
 *   <li>Severity: 1:1 mapping (INFO, WARNING, ERROR, CRITICAL)
 *   <li>Category: Many-to-one mapping (Category -> ViolationCategory)
 *   <li>Violation: Field mapping with some defaults
 * </ul>
 */
public final class RuleEngineAdapter {

  private RuleEngineAdapter() {
    // Utility class
  }

  /**
   * Converts a scanner SPI violation to a scanner violation.
   *
   * @param spiViolation the scanner SPI violation
   * @return the equivalent scanner violation
   */
  public static Violation toScannerViolation(
      dev.engineeringlab.stratify.structure.scanner.spi.Violation spiViolation) {
    if (spiViolation == null) {
      return null;
    }

    return Violation.builder()
        .ruleId(spiViolation.getScannerId())
        .category(toCategory(spiViolation.getCategory()))
        .severity(toScannerSeverity(spiViolation.getSeverity()))
        .description(spiViolation.getMessage())
        .location(spiViolation.getLocation())
        .fix(spiViolation.getFix())
        .reference(spiViolation.getReference())
        .passed(false)
        .build();
  }

  /**
   * Converts a scanner SPI severity to a scanner severity.
   *
   * @param severity the scanner SPI severity
   * @return the equivalent scanner severity
   */
  public static Severity toScannerSeverity(
      dev.engineeringlab.stratify.structure.scanner.spi.Severity severity) {
    if (severity == null) {
      return Severity.ERROR; // Default
    }

    return switch (severity) {
      case INFO -> Severity.INFO;
      case WARNING -> Severity.WARNING;
      case ERROR -> Severity.ERROR;
      case CRITICAL -> Severity.CRITICAL;
    };
  }

  /**
   * Converts a scanner severity to a scanner SPI severity.
   *
   * @param severity the scanner severity
   * @return the equivalent scanner SPI severity
   */
  public static dev.engineeringlab.stratify.structure.scanner.spi.Severity toScannerSpiSeverity(
      Severity severity) {
    if (severity == null) {
      return dev.engineeringlab.stratify.structure.scanner.spi.Severity.ERROR; // Default
    }

    return switch (severity) {
      case INFO -> dev.engineeringlab.stratify.structure.scanner.spi.Severity.INFO;
      case WARNING -> dev.engineeringlab.stratify.structure.scanner.spi.Severity.WARNING;
      case ERROR -> dev.engineeringlab.stratify.structure.scanner.spi.Severity.ERROR;
      case CRITICAL -> dev.engineeringlab.stratify.structure.scanner.spi.Severity.CRITICAL;
    };
  }

  /**
   * Converts a scanner SPI category to a scanner violation category.
   *
   * <p>The scanner SPI has fewer categories, so this is a many-to-one mapping. For more specific
   * categories, use the scanner's ViolationCategory directly.
   *
   * @param category the scanner SPI category
   * @return the equivalent scanner violation category
   */
  public static ViolationCategory toCategory(
      dev.engineeringlab.stratify.structure.scanner.spi.Category category) {
    if (category == null) {
      return ViolationCategory.CODE_QUALITY; // Default
    }

    return switch (category) {
      case DEPENDENCIES -> ViolationCategory.DEPENDENCIES;
      case STRUCTURE -> ViolationCategory.MODULE_STRUCTURE;
      case NAMING -> ViolationCategory.NAMING_CONVENTIONS;
      case QUALITY -> ViolationCategory.CODE_QUALITY;
      case SECURITY -> ViolationCategory.CODE_QUALITY;
      case PERFORMANCE -> ViolationCategory.CODE_QUALITY;
      case CONFIGURATION -> ViolationCategory.INFRASTRUCTURE;
    };
  }

  /**
   * Converts a scanner violation category to a scanner SPI category.
   *
   * <p>The scanner has more specific categories, so this is a many-to-one mapping where multiple
   * scanner categories map to the same scanner SPI category.
   *
   * @param category the scanner violation category
   * @return the equivalent scanner SPI category
   */
  public static dev.engineeringlab.stratify.structure.scanner.spi.Category toScannerSpiCategory(
      ViolationCategory category) {
    if (category == null) {
      return dev.engineeringlab.stratify.structure.scanner.spi.Category.QUALITY; // Default
    }

    return switch (category) {
      case DEPENDENCIES -> dev.engineeringlab.stratify.structure.scanner.spi.Category.DEPENDENCIES;
      case MODULE_STRUCTURE,
              PACKAGE_ORGANIZATION,
              TEST_STRUCTURE,
              SUBSYSTEM_ARCHITECTURE,
              MODULAR_DESIGN ->
          dev.engineeringlab.stratify.structure.scanner.spi.Category.STRUCTURE;
      case NAMING_CONVENTIONS -> dev.engineeringlab.stratify.structure.scanner.spi.Category.NAMING;
      case CODE_QUALITY, CODE_QUALITY_STANDARDS, STATIC_ANALYSIS, SPOTBUGS_EXCLUSIONS, TEST ->
          dev.engineeringlab.stratify.structure.scanner.spi.Category.QUALITY;
      case CLEAN_ARCHITECTURE, DESIGN_PATTERNS, SPI_PROVIDERS ->
          dev.engineeringlab.stratify.structure.scanner.spi.Category.STRUCTURE;
      case EXCEPTION_INFRASTRUCTURE, EXCEPTION_PROPAGATION ->
          dev.engineeringlab.stratify.structure.scanner.spi.Category.QUALITY;
      case INFRASTRUCTURE, BUILD_POLICY ->
          dev.engineeringlab.stratify.structure.scanner.spi.Category.CONFIGURATION;
    };
  }
}
