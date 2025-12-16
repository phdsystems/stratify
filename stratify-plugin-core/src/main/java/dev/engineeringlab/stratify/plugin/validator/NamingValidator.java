package dev.engineeringlab.stratify.plugin.validator;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates SEA module naming conventions.
 *
 * <p>Rules: - NC-001: Facade artifactId has NO -facade suffix (ERROR) - NC-002: API artifactId has
 * -api suffix (ERROR) - NC-003: Core artifactId has -core suffix (ERROR) - NC-004: Common
 * artifactId has -common suffix (ERROR) - NC-005: SPI artifactId has -spi suffix (ERROR)
 */
public class NamingValidator {
  private static final Logger log = LoggerFactory.getLogger(NamingValidator.class);

  /**
   * Validates module naming conventions.
   *
   * @param modules list of discovered modules
   * @return list of validation results
   */
  public List<ValidationResult> validate(List<ModuleScanner.ModuleInfo> modules) {
    log.debug("Validating naming conventions for {} modules", modules.size());

    List<ValidationResult> results = new ArrayList<>();

    for (ModuleScanner.ModuleInfo module : modules) {
      validateModuleNaming(module, results);
    }

    log.debug("Naming validation completed with {} results", results.size());
    return results;
  }

  private void validateModuleNaming(
      ModuleScanner.ModuleInfo module, List<ValidationResult> results) {
    String artifactId = module.artifactId();
    ModuleScanner.ModuleLayer layer = module.layer();

    switch (layer) {
      case FACADE:
        // NC-001: Facade should NOT have -facade suffix
        if (artifactId.endsWith("-facade")) {
          results.add(
              ValidationResult.error(
                  "NC-001",
                  String.format("Facade module '%s' should NOT have -facade suffix", artifactId),
                  module.getFullName()));
        }
        break;

      case API:
        // NC-002: API must have -api suffix
        if (!artifactId.endsWith("-api")) {
          results.add(
              ValidationResult.error(
                  "NC-002",
                  String.format("API module '%s' must have -api suffix", artifactId),
                  module.getFullName()));
        }
        break;

      case CORE:
        // NC-003: Core must have -core suffix
        if (!artifactId.endsWith("-core")) {
          results.add(
              ValidationResult.error(
                  "NC-003",
                  String.format("Core module '%s' must have -core suffix", artifactId),
                  module.getFullName()));
        }
        break;

      case COMMON:
        // NC-004: Common must have -common suffix
        if (!artifactId.endsWith("-common")) {
          results.add(
              ValidationResult.error(
                  "NC-004",
                  String.format("Common module '%s' must have -common suffix", artifactId),
                  module.getFullName()));
        }
        break;

      case SPI:
        // NC-005: SPI must have -spi suffix
        if (!artifactId.endsWith("-spi")) {
          results.add(
              ValidationResult.error(
                  "NC-005",
                  String.format("SPI module '%s' must have -spi suffix", artifactId),
                  module.getFullName()));
        }
        break;
    }
  }
}
