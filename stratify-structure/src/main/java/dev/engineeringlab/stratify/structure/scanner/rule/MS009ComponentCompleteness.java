package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MS-009: Component completeness.
 *
 * <p>Modules must form complete components. A component is a set of related modules sharing a
 * common base name:
 *
 * <ul>
 *   <li>{base}-api - Public API (required)
 *   <li>{base}-core - Implementation (required)
 *   <li>{base}-common - Shared constants, enums, utilities (optional)
 *   <li>{base}-spi - Service Provider Interface (optional)
 *   <li>{base}-facade - Aggregating facade (optional)
 * </ul>
 *
 * <p>This rule detects orphan modules (e.g., -api without -core).
 */
public class MS009ComponentCompleteness extends AbstractStructureRule {

  private static final Pattern LAYER_PATTERN =
      Pattern.compile("^(.+)-(api|core|common|spi|facade)$");

  public MS009ComponentCompleteness() {
    super("MS-009");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Map<String, ModuleInfo.SubModuleInfo> subModules = module.getSubModules();
    if (subModules == null || subModules.isEmpty()) {
      return List.of();
    }

    // Group modules by their base name (component name)
    Map<String, Set<String>> componentLayers = new HashMap<>();

    for (ModuleInfo.SubModuleInfo subModule : subModules.values()) {
      if (!subModule.isExists()) {
        continue;
      }

      String artifactId = subModule.getArtifactId();
      if (artifactId == null) {
        continue;
      }

      Matcher matcher = LAYER_PATTERN.matcher(artifactId);
      if (matcher.matches()) {
        String baseName = matcher.group(1);
        String layer = matcher.group(2);
        componentLayers.computeIfAbsent(baseName, k -> new HashSet<>()).add(layer);
      }
    }

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
      boolean hasCommon = layers.contains("common");
      boolean hasSpi = layers.contains("spi");
      boolean hasFacade = layers.contains("facade");

      if (hasApi && !hasCore) {
        incompleteComponents.add(
            componentName + "-api exists but " + componentName + "-core is missing");
      }
      if (hasCore && !hasApi) {
        incompleteComponents.add(
            componentName + "-core exists but " + componentName + "-api is missing");
      }
      if (hasCommon && !hasApi) {
        incompleteComponents.add(
            componentName + "-common exists but " + componentName + "-api is missing");
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
            module,
            String.format(
                "Incomplete components found: %s. Each component must have at least {base}-api and {base}-core modules.",
                String.join("; ", incompleteComponents)),
            module.getBasePath().toString()));
  }
}
