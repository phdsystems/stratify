package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * PA-001: Parent Config Exists.
 *
 * <p>Parent aggregator modules MUST have a config/module.parent.yml file that declares all expected
 * layer modules (api, spi, core, facade, common) and any nested submodules.
 */
public class PA001ParentConfigExists extends AbstractStructureRule {

  public static final String CONFIG_FILE = "config/module.parent.yml";

  public PA001ParentConfigExists() {
    super("PA-001");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent aggregator modules (has layer submodules)
    if (!module.isParent()) {
      return false;
    }
    // Must be parent aggregator: has at least one layer submodule
    // Check if has any standard layer modules
    return module.hasApiModule()
        || module.hasCoreModule()
        || module.hasFacadeModule()
        || module.hasSpiModule()
        || module.hasCommonModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Path configPath = module.getBasePath().resolve(CONFIG_FILE);

    if (!Files.exists(configPath)) {
      return List.of(
          createViolation(
              module,
              String.format(
                  "Parent aggregator '%s' must have %s file. "
                      + "This file declares all expected layer modules and submodules.",
                  module.getArtifactId(), CONFIG_FILE),
              configPath.toString()));
    }

    return List.of();
  }
}
