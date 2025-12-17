package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-015: Aggregator Modules Should Not Have Dependencies.
 *
 * <p>Aggregator modules should not declare dependencies, only dependencyManagement. Direct
 * dependencies in aggregators are inherited by all submodules.
 */
public class MS015AggregatorNoDependencies extends AbstractStructureRule {

  public MS015AggregatorNoDependencies() {
    super("MS-015");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.getPomModel() == null) {
      return List.of();
    }

    var dependencies = module.getPomModel().getDependencies();
    if (dependencies == null || dependencies.isEmpty()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            "Aggregator module '"
                + module.getArtifactId()
                + "' has "
                + dependencies.size()
                + " direct dependencies. Use dependencyManagement instead."));
  }
}
