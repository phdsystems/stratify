package dev.engineeringlab.stratify.plugin.validator;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Validates SEA module structure compliance.
 * <p>
 * Rules:
 * - MS-001: API module exists (ERROR)
 * - MS-002: Core module exists (ERROR)
 * - MS-003: Facade module exists (ERROR)
 * - MS-004: Common module exists (WARNING)
 * - MS-005: SPI module exists (WARNING)
 */
public class ModuleStructureValidator {
    private static final Logger log = LoggerFactory.getLogger(ModuleStructureValidator.class);

    /**
     * Validates that required SEA modules exist.
     *
     * @param modules list of discovered modules
     * @return list of validation results
     */
    public List<ValidationResult> validate(List<ModuleScanner.ModuleInfo> modules) {
        log.debug("Validating module structure for {} modules", modules.size());

        List<ValidationResult> results = new ArrayList<>();

        Set<ModuleScanner.ModuleLayer> foundLayers = EnumSet.noneOf(ModuleScanner.ModuleLayer.class);
        for (ModuleScanner.ModuleInfo module : modules) {
            foundLayers.add(module.layer());
        }

        // MS-001: API module exists
        if (!foundLayers.contains(ModuleScanner.ModuleLayer.API)) {
            results.add(ValidationResult.error(
                    "MS-001",
                    "API module is required but not found",
                    "project"
            ));
        }

        // MS-002: Core module exists
        if (!foundLayers.contains(ModuleScanner.ModuleLayer.CORE)) {
            results.add(ValidationResult.error(
                    "MS-002",
                    "Core module is required but not found",
                    "project"
            ));
        }

        // MS-003: Facade module exists
        if (!foundLayers.contains(ModuleScanner.ModuleLayer.FACADE)) {
            results.add(ValidationResult.error(
                    "MS-003",
                    "Facade module is required but not found",
                    "project"
            ));
        }

        // MS-004: Common module exists (WARNING)
        if (!foundLayers.contains(ModuleScanner.ModuleLayer.COMMON)) {
            results.add(ValidationResult.warning(
                    "MS-004",
                    "Common module is recommended but not found",
                    "project"
            ));
        }

        // MS-005: SPI module exists (WARNING)
        if (!foundLayers.contains(ModuleScanner.ModuleLayer.SPI)) {
            results.add(ValidationResult.warning(
                    "MS-005",
                    "SPI module is recommended but not found",
                    "project"
            ));
        }

        log.debug("Module structure validation completed with {} results", results.size());
        return results;
    }
}
