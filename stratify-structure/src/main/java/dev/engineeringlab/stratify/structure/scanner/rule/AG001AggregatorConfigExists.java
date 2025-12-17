package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * AG-001: Aggregator Config Exists.
 *
 * <p>Pure aggregator modules MUST have a config/module.aggregator.yml file that declares all
 * expected child modules and their types.
 */
public class AG001AggregatorConfigExists extends AbstractStructureRule {

  public static final String CONFIG_FILE = "config/module.aggregator.yml";

  public AG001AggregatorConfigExists() {
    super("AG-001");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to pure aggregator modules (parent with no layer submodules)
    if (!module.isParent()) {
      return false;
    }
    // Must be pure aggregator: no api/core/facade/spi submodules
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    return !module.hasAnyLayerModules();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Path configPath = module.getBasePath().resolve(CONFIG_FILE);

    if (!Files.exists(configPath)) {
      return List.of(
          createViolation(
              module,
              String.format(
                  "Pure aggregator '%s' must have %s file. "
                      + "This file declares all expected child modules and their types.",
                  module.getArtifactId(), CONFIG_FILE),
              configPath.toString()));
    }

    return List.of();
  }
}
