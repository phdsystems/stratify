package dev.engineeringlab.stratify.structure.rule.impl;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that checks component completeness in stratified architecture.
 *
 * <p>Modules must form complete components. A component is a set of related modules sharing a
 * common base name:
 *
 * <ul>
 *   <li>{base}-api - Public API (required)
 *   <li>{base}-core - Implementation (required)
 *   <li>{base}-spi - Service Provider Interface (optional)
 *   <li>{base}-facade - Aggregating facade (optional)
 * </ul>
 *
 * <p>This rule detects orphan modules (e.g., -api without -core, or -core without -api). Each
 * component must have at least both API and core modules to be considered complete.
 *
 * <p>Note: The "common" layer is explicitly excluded from this pattern as per SEA-4 architectural
 * decisions. Components should not use {base}-common modules.
 *
 * <h2>Example Violations</h2>
 *
 * <p>Given a module structure:
 *
 * <pre>
 * my-component/
 *   user-api/
 *     pom.xml
 *   user-core/
 *     pom.xml
 *   product-api/
 *     pom.xml
 * </pre>
 *
 * <p>This rule would report a violation for the "product" component because it has product-api but
 * is missing product-core.
 *
 * <p>Ported from MS-009 in the MS-Engine architecture scanner.
 */
public class ComponentCompleteness extends AbstractStructureRule {

  private static final Pattern LAYER_PATTERN = Pattern.compile("^(.+)-(api|core|spi|facade)$");

  /**
   * Creates a ComponentCompleteness rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public ComponentCompleteness() {
    super("SEA-105", "structure-rules");
  }

  /**
   * Creates a ComponentCompleteness rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public ComponentCompleteness(RuleLoader ruleLoader) {
    super("SEA-105", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to parent directories that contain multiple submodules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that match our layer pattern
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> LAYER_PATTERN.matcher(name).matches());
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  protected List<StructureViolation> doValidate(Path path) {
    try (Stream<Path> entries = Files.list(path)) {
      // Group modules by their base name (component name)
      Map<String, Set<String>> componentLayers = new HashMap<>();

      entries
          .filter(Files::isDirectory)
          .forEach(
              modulePath -> {
                String moduleName = modulePath.getFileName().toString();
                Matcher matcher = LAYER_PATTERN.matcher(moduleName);

                if (matcher.matches()) {
                  String baseName = matcher.group(1);
                  String layer = matcher.group(2);

                  // Verify the module has a pom.xml to ensure it's a valid Maven module
                  if (Files.exists(modulePath.resolve("pom.xml"))) {
                    componentLayers.computeIfAbsent(baseName, k -> new HashSet<>()).add(layer);
                  }
                }
              });

      if (componentLayers.isEmpty()) {
        return List.of();
      }

      // Validate each component has required layers
      List<String> incompleteComponents = new ArrayList<>();

      for (Map.Entry<String, Set<String>> entry : componentLayers.entrySet()) {
        String componentName = entry.getKey();
        Set<String> layers = entry.getValue();

        boolean hasApi = layers.contains("api");
        boolean hasCore = layers.contains("core");
        boolean hasSpi = layers.contains("spi");
        boolean hasFacade = layers.contains("facade");

        // Check for incomplete components
        if (hasApi && !hasCore) {
          incompleteComponents.add(
              componentName + "-api exists but " + componentName + "-core is missing");
        }
        if (hasCore && !hasApi) {
          incompleteComponents.add(
              componentName + "-core exists but " + componentName + "-api is missing");
        }
        if (hasSpi && !hasApi) {
          incompleteComponents.add(
              componentName + "-spi exists but " + componentName + "-api is missing");
        }
        if (hasFacade && !hasApi) {
          incompleteComponents.add(
              componentName + "-facade exists but " + componentName + "-api is missing");
        }
      }

      if (incompleteComponents.isEmpty()) {
        return List.of();
      }

      return List.of(
          createViolation(
              path.getFileName().toString(),
              String.format(
                  "Incomplete components found: %s. Each component must have at least {base}-api and {base}-core modules.",
                  String.join("; ", incompleteComponents)),
              path.toString()));

    } catch (IOException e) {
      // If we can't read the directory, we can't validate it
      return List.of();
    }
  }
}
