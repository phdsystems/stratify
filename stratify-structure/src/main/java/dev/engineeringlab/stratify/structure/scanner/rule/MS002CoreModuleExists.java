package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-002: Core module must exist.
 *
 * <p>All standard modules MUST have a Core module that contains the implementation of API
 * contracts.
 */
public class MS002CoreModuleExists extends AbstractStructureRule {

  public MS002CoreModuleExists() {
    super("MS-002");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    if (!module.isParent()) {
      return false;
    }
    if (isPureAggregator(module)) {
      return false;
    }
    // Standalone modules that aren't core-based don't need a core module
    // (e.g., standalone common/commons/util/utils modules)
    return !module.isStandaloneCommonModule()
        && !module.isStandaloneCommonsModule()
        && !module.isStandaloneUtilModule()
        && !module.isStandaloneUtilsModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.hasCoreModule()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            String.format(
                "Core module '%s-core' must exist. All standard modules require a Core module "
                    + "for implementation.",
                module.getModuleName()),
            module.getBasePath().toString()));
  }

  private boolean isPureAggregator(ModuleInfo moduleInfo) {
    return !moduleInfo.hasApiModule()
        && !moduleInfo.hasCoreModule()
        && !moduleInfo.hasFacadeModule()
        && !moduleInfo.hasSpiModule();
  }
}
