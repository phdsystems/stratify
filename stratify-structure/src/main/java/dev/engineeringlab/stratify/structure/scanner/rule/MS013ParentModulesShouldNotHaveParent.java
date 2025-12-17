package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-013: Parent Modules Should Not Have Parent.
 *
 * <p>Parent modules (-parent) should be standalone without declaring a parent POM to maintain a
 * flat module hierarchy.
 */
public class MS013ParentModulesShouldNotHaveParent extends AbstractStructureRule {

  public MS013ParentModulesShouldNotHaveParent() {
    super("MS-013");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Only applies to parent modules (artifactId ends with -parent)
    return module.getArtifactId() != null && module.getArtifactId().endsWith("-parent");
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.getPomModel() == null || module.getPomModel().getParent() == null) {
      return List.of();
    }

    String parentArtifactId = module.getPomModel().getParent().getArtifactId();
    return List.of(
        createViolation(
            module,
            "Parent module '"
                + module.getArtifactId()
                + "' declares parent '"
                + parentArtifactId
                + "'. Parent modules should be standalone to maintain flat hierarchy."));
  }
}
