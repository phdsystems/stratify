package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.Yaml;

/**
 * PA-004: Parent Layer Types Valid.
 *
 * <p>Layer modules declared in module.parent.yml must follow the naming pattern: {name}-api,
 * {name}-spi, {name}-core, {name}-facade, or {name}-common.
 */
public class PA004ParentLayerTypesValid extends AbstractStructureRule {

  private static final String CONFIG_FILE = "config/module.parent.yml";

  /** Valid layer pattern: any name followed by -api, -spi, -core, -facade, or -common */
  private static final Pattern VALID_LAYER_PATTERN =
      Pattern.compile("^.+-(api|spi|core|facade|common)$");

  private static final Set<String> VALID_LAYER_SUFFIXES =
      Set.of("api", "spi", "core", "facade", "common");

  public PA004ParentLayerTypesValid() {
    super("PA-004");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent aggregator modules with a config file
    if (!module.isParent()) {
      return false;
    }
    // Check if has any standard layer modules
    boolean hasLayers =
        module.hasApiModule()
            || module.hasCoreModule()
            || module.hasFacadeModule()
            || module.hasSpiModule()
            || module.hasCommonModule();
    if (!hasLayers) {
      return false;
    }
    return Files.exists(module.getBasePath().resolve(CONFIG_FILE));
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path configPath = module.getBasePath().resolve(CONFIG_FILE);

    try {
      Yaml yaml = new Yaml();
      String content = Files.readString(configPath);
      Map<String, Object> config = yaml.load(content);

      if (config == null) {
        return violations;
      }

      // Validate layers
      @SuppressWarnings("unchecked")
      List<String> layers = (List<String>) config.get("layers");
      if (layers != null) {
        for (String layer : layers) {
          if (!VALID_LAYER_PATTERN.matcher(layer).matches()) {
            violations.add(
                createViolation(
                    module,
                    String.format(
                        "Invalid layer name '%s'. "
                            + "Layer names must follow pattern {name}-(api|spi|core|facade|common). "
                            + "Valid suffixes: %s",
                        layer, VALID_LAYER_SUFFIXES),
                    configPath.toString()));
          }
        }
      }

      // Validate submodule layer references
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
      if (submodules != null) {
        for (Map<String, Object> submodule : submodules) {
          String submoduleName = (String) submodule.get("name");
          @SuppressWarnings("unchecked")
          List<String> submoduleLayers = (List<String>) submodule.get("layers");

          if (submoduleLayers != null) {
            for (String layer : submoduleLayers) {
              if (!VALID_LAYER_PATTERN.matcher(layer).matches()) {
                violations.add(
                    createViolation(
                        module,
                        String.format(
                            "Invalid layer name '%s' in submodule '%s'. "
                                + "Layer names must follow pattern {name}-(api|spi|core|facade|common).",
                            layer, submoduleName),
                        configPath.toString()));
              }
            }
          }
        }
      }

    } catch (Exception e) {
      violations.add(
          createViolation(
              module, "Failed to validate layer naming: " + e.getMessage(), configPath.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }
}
