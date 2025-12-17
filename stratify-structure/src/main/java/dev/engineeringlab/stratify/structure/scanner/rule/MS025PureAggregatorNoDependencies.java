package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-025: Pure Aggregator No Dependencies.
 *
 * <p>Pure aggregator modules (artifactId ending with -aggregator) should NOT have a dependencies
 * section in their pom.xml. They should ONLY have a modules section to group submodules. Pure
 * aggregators are meant to be truly empty - just providing module grouping.
 *
 * <p>This rule applies to:
 *
 * <ul>
 *   <li>Modules with artifactId ending with -aggregator suffix
 *   <li>Modules that are pure aggregators (no api/core/facade/spi submodules)
 * </ul>
 *
 * <p>Dependencies in pure aggregators indicate misplaced configuration. Any dependencies should be
 * moved to:
 *
 * <ul>
 *   <li>dependencyManagement section for version control
 *   <li>Specific submodules that actually need them
 * </ul>
 */
public class MS025PureAggregatorNoDependencies extends AbstractStructureRule {

  public MS025PureAggregatorNoDependencies() {
    super("MS-025");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent modules that are pure aggregators
    if (!module.isParent()) {
      return false;
    }

    // Check if it's a pure aggregator (no layer submodules)
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    return !module.hasAnyLayerModules();
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
            "Pure aggregator module '"
                + module.getArtifactId()
                + "' has "
                + dependencies.size()
                + " dependencies. Pure aggregators should have NO dependencies section - only <modules>. "
                + "Move dependencies to dependencyManagement or specific submodules."));
  }
}
