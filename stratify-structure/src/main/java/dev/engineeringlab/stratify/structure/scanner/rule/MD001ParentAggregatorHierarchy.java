package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.ArrayList;
import java.util.List;

/**
 * MD-001: Parent Aggregator Hierarchy.
 *
 * <p>Enforces the parent aggregator hierarchy constraints:
 *
 * <ul>
 *   <li>Above a parent aggregator (-parent): can only be pure aggregator(s) (-aggregator)
 *   <li>Below a parent aggregator (-parent): must be leaf modules only (not other -parent modules)
 * </ul>
 *
 * <p>This ensures a clean modular design where:
 *
 * <ul>
 *   <li>Pure aggregators (-aggregator) group parent aggregators
 *   <li>Parent aggregators (-parent) group leaf modules (api/core/facade/spi)
 * </ul>
 */
public class MD001ParentAggregatorHierarchy extends AbstractStructureRule {

  public MD001ParentAggregatorHierarchy() {
    super("MD-001");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to parent aggregators (artifactId ends with -parent)
    return module.isParent()
        && module.getArtifactId() != null
        && module.getArtifactId().endsWith("-parent");
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();

    // Check 1: If has parent, it must be a pure aggregator (-aggregator suffix)
    if (module.getPomModel() != null && module.getPomModel().getParent() != null) {
      String parentArtifactId = module.getPomModel().getParent().getArtifactId();
      if (parentArtifactId != null && !parentArtifactId.endsWith("-aggregator")) {
        violations.add(
            createViolation(
                module,
                "Parent aggregator '"
                    + module.getArtifactId()
                    + "' has parent '"
                    + parentArtifactId
                    + "' which is not a pure aggregator. Above a parent aggregator, "
                    + "only pure aggregators (-aggregator suffix) are allowed."));
      }
    }

    // Check 2: Children must be leaf modules (not -parent)
    if (module.getSubModules() != null) {
      for (ModuleInfo.SubModuleInfo subModule : module.getSubModules().values()) {
        if (subModule.isExists() && subModule.getArtifactId() != null) {
          String childArtifactId = subModule.getArtifactId();
          if (childArtifactId.endsWith("-parent")) {
            violations.add(
                createViolation(
                    module,
                    "Parent aggregator '"
                        + module.getArtifactId()
                        + "' has child '"
                        + childArtifactId
                        + "' which is another parent aggregator. Below a parent aggregator, "
                        + "only leaf modules are allowed (api/core/facade/spi/common)."));
          }
        }
      }
    }

    return violations;
  }
}
