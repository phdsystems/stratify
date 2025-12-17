package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-003: Facade module must exist.
 *
 * <p>All standard modules MUST have a Facade module that provides the unified entry point for
 * consumers.
 */
public class MS003FacadeModuleExists extends AbstractStructureRule {

  public MS003FacadeModuleExists() {
    super("MS-003");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    if (!module.isParent()) {
      return false;
    }
    if (isPureAggregator(module)) {
      return false;
    }
    // Standalone modules don't need a facade module
    return !module.isStandaloneModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.hasFacadeModule()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            String.format(
                "Facade module '%s-facade' must exist. All standard modules require a Facade module "
                    + "as the unified entry point for consumers.",
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
