package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-001: API module must exist.
 *
 * <p>All standard modules MUST have an API module that contains the public contracts (interfaces,
 * DTOs, exceptions).
 */
public class MS001ApiModuleExists extends AbstractStructureRule {

  public MS001ApiModuleExists() {
    super("MS-001");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Only applies to parent modules that are NOT pure aggregators or standalone modules
    if (!module.isParent()) {
      return false;
    }
    // Pure aggregators (no api/core/facade/spi) don't need their own API module
    if (isPureAggregator(module)) {
      return false;
    }
    // Standalone modules (core-only, common-only, etc.) don't need an API module
    return !module.isStandaloneModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.hasApiModule()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            String.format(
                "API module '%s-api' must exist. All standard modules require an API module "
                    + "for public contracts (interfaces, DTOs, exceptions).",
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
