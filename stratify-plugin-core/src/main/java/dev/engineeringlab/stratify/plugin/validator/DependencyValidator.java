package dev.engineeringlab.stratify.plugin.validator;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Validates SEA module dependency rules.
 * <p>
 * Rules:
 * - DP-001: Facade depends on Core (ERROR)
 * - DP-002: Core depends on API, SPI, Common (ERROR)
 * - DP-003: API does not depend on Core (ERROR)
 * - DP-004: No circular dependencies (ERROR)
 */
public class DependencyValidator {
    private static final Logger log = LoggerFactory.getLogger(DependencyValidator.class);

    /**
     * Validates module dependencies.
     *
     * @param modules list of discovered modules
     * @return list of validation results
     */
    public List<ValidationResult> validate(List<ModuleScanner.ModuleInfo> modules) {
        log.debug("Validating dependencies for {} modules", modules.size());

        List<ValidationResult> results = new ArrayList<>();

        // Create lookup maps
        Map<String, ModuleScanner.ModuleInfo> moduleMap = new HashMap<>();
        Map<ModuleScanner.ModuleLayer, List<ModuleScanner.ModuleInfo>> layerMap = new HashMap<>();

        for (ModuleScanner.ModuleInfo module : modules) {
            moduleMap.put(module.artifactId(), module);
            layerMap.computeIfAbsent(module.layer(), k -> new ArrayList<>()).add(module);
        }

        // Validate each rule
        validateFacadeDependsOnCore(layerMap, results);
        validateCoreDependsOnApiSpiCommon(layerMap, results);
        validateApiDoesNotDependOnCore(layerMap, moduleMap, results);
        validateNoCircularDependencies(modules, moduleMap, results);

        log.debug("Dependency validation completed with {} results", results.size());
        return results;
    }

    /**
     * DP-001: Facade depends on Core
     */
    private void validateFacadeDependsOnCore(
            Map<ModuleScanner.ModuleLayer, List<ModuleScanner.ModuleInfo>> layerMap,
            List<ValidationResult> results
    ) {
        List<ModuleScanner.ModuleInfo> facades = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.FACADE,
                List.of()
        );
        List<ModuleScanner.ModuleInfo> cores = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.CORE,
                List.of()
        );

        for (ModuleScanner.ModuleInfo facade : facades) {
            boolean dependsOnCore = false;

            for (ModuleScanner.ModuleInfo core : cores) {
                if (facade.hasDependency(core.artifactId())) {
                    dependsOnCore = true;
                    break;
                }
            }

            if (!dependsOnCore && !cores.isEmpty()) {
                results.add(ValidationResult.error(
                        "DP-001",
                        String.format("Facade module '%s' must depend on Core module", facade.artifactId()),
                        facade.getFullName()
                ));
            }
        }
    }

    /**
     * DP-002: Core depends on API, SPI, Common (at least API is required)
     */
    private void validateCoreDependsOnApiSpiCommon(
            Map<ModuleScanner.ModuleLayer, List<ModuleScanner.ModuleInfo>> layerMap,
            List<ValidationResult> results
    ) {
        List<ModuleScanner.ModuleInfo> cores = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.CORE,
                List.of()
        );
        List<ModuleScanner.ModuleInfo> apis = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.API,
                List.of()
        );

        for (ModuleScanner.ModuleInfo core : cores) {
            boolean dependsOnApi = false;

            for (ModuleScanner.ModuleInfo api : apis) {
                if (core.hasDependency(api.artifactId())) {
                    dependsOnApi = true;
                    break;
                }
            }

            if (!dependsOnApi && !apis.isEmpty()) {
                results.add(ValidationResult.error(
                        "DP-002",
                        String.format("Core module '%s' must depend on API module", core.artifactId()),
                        core.getFullName()
                ));
            }
        }
    }

    /**
     * DP-003: API does not depend on Core
     */
    private void validateApiDoesNotDependOnCore(
            Map<ModuleScanner.ModuleLayer, List<ModuleScanner.ModuleInfo>> layerMap,
            Map<String, ModuleScanner.ModuleInfo> moduleMap,
            List<ValidationResult> results
    ) {
        List<ModuleScanner.ModuleInfo> apis = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.API,
                List.of()
        );
        List<ModuleScanner.ModuleInfo> cores = layerMap.getOrDefault(
                ModuleScanner.ModuleLayer.CORE,
                List.of()
        );

        for (ModuleScanner.ModuleInfo api : apis) {
            for (ModuleScanner.ModuleInfo core : cores) {
                if (api.hasDependency(core.artifactId())) {
                    results.add(ValidationResult.error(
                            "DP-003",
                            String.format("API module '%s' must NOT depend on Core module '%s'",
                                    api.artifactId(), core.artifactId()),
                            api.getFullName()
                    ));
                }
            }
        }
    }

    /**
     * DP-004: No circular dependencies
     */
    private void validateNoCircularDependencies(
            List<ModuleScanner.ModuleInfo> modules,
            Map<String, ModuleScanner.ModuleInfo> moduleMap,
            List<ValidationResult> results
    ) {
        for (ModuleScanner.ModuleInfo module : modules) {
            Set<String> visited = new HashSet<>();
            Set<String> recursionStack = new HashSet<>();

            if (hasCircularDependency(module.artifactId(), moduleMap, visited, recursionStack)) {
                results.add(ValidationResult.error(
                        "DP-004",
                        String.format("Circular dependency detected involving module '%s'", module.artifactId()),
                        module.getFullName()
                ));
            }
        }
    }

    /**
     * Detects circular dependencies using DFS.
     */
    private boolean hasCircularDependency(
            String moduleId,
            Map<String, ModuleScanner.ModuleInfo> moduleMap,
            Set<String> visited,
            Set<String> recursionStack
    ) {
        if (recursionStack.contains(moduleId)) {
            return true; // Circular dependency found
        }

        if (visited.contains(moduleId)) {
            return false; // Already processed
        }

        visited.add(moduleId);
        recursionStack.add(moduleId);

        ModuleScanner.ModuleInfo module = moduleMap.get(moduleId);
        if (module != null) {
            for (String dependency : module.dependencies()) {
                if (moduleMap.containsKey(dependency)) {
                    if (hasCircularDependency(dependency, moduleMap, visited, recursionStack)) {
                        return true;
                    }
                }
            }
        }

        recursionStack.remove(moduleId);
        return false;
    }
}
