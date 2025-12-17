package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-017: Pure Aggregator Must Use -aggregator Suffix.
 *
 * <p>Pure aggregator modules (no api/core/facade/spi) must have artifactId ending with -aggregator.
 * Parent aggregators use -parent suffix and have layer modules (api/core/facade/spi).
 */
public class MS017PureAggregatorSuffix extends AbstractStructureRule {

  public MS017PureAggregatorSuffix() {
    super("MS-017");
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
    String artifactId = module.getArtifactId();

    // Pure aggregators MUST end with -aggregator (not -parent)
    if (artifactId.endsWith("-aggregator")) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            "Pure aggregator module '"
                + artifactId
                + "' must end with -aggregator suffix. "
                + "The -parent suffix is reserved for parent aggregators that have layer modules (api/core/facade/spi)."));
  }
}
