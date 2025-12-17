package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-010: *-common module must be first in parent modules list.
 *
 * <p>If a *-common module exists, it must be listed first in the parent POM's {@code <modules>}
 * section to ensure proper build order.
 */
public class MS010CommonModuleFirst extends AbstractStructureRule {

  public MS010CommonModuleFirst() {
    super("MS-010");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<String> modules = module.getModuleOrder();
    if (modules == null || modules.isEmpty()) {
      return List.of();
    }

    // Find *-common module
    String commonModule = null;
    int commonIndex = -1;
    for (int i = 0; i < modules.size(); i++) {
      String mod = modules.get(i);
      if (mod != null && mod.endsWith("-common")) {
        commonModule = mod;
        commonIndex = i;
        break;
      }
    }

    // No common module - nothing to validate
    if (commonModule == null) {
      return List.of();
    }

    // Check if common module is first
    if (commonIndex == 0) {
      return List.of();
    }

    String firstModule = modules.get(0);
    return List.of(
        createViolation(
            module,
            String.format(
                "Module '%s' must be listed first in <modules>, but '%s' is first. "
                    + "The *-common module contains shared constants and utilities that other modules depend on.",
                commonModule, firstModule),
            module.getBasePath().resolve("pom.xml").toString()));
  }
}
