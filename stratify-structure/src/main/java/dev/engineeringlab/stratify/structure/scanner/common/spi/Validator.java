package dev.engineeringlab.stratify.structure.scanner.common.spi;

import dev.engineeringlab.stratify.structure.scanner.common.config.ComplianceConfig;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import java.util.List;

/**
 * Base interface for all validators.
 *
 * <p>Validators are responsible for checking specific compliance rules against a module and
 * returning any violations found.
 */
public interface Validator {

  /**
   * Gets the name of this validator.
   *
   * @return the validator name
   */
  String getName();

  /**
   * Validates a module against the compliance rules.
   *
   * @param moduleInfo the module information
   * @param config the compliance configuration
   * @return list of violations found (empty if compliant)
   */
  List<Violation> validate(ModuleInfo moduleInfo, ComplianceConfig config);

  /**
   * Gets the priority of this validator.
   *
   * <p>Lower values run first. Default is 100.
   *
   * @return the priority
   */
  default int getPriority() {
    return 100;
  }

  /**
   * Checks if this validator is applicable to the given module.
   *
   * @param moduleInfo the module information
   * @return true if this validator should run for the module
   */
  default boolean isApplicable(ModuleInfo moduleInfo) {
    return true;
  }
}
