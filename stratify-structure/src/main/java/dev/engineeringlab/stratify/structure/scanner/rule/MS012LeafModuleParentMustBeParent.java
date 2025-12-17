package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-012: Leaf Module Parent Must Be Parent Module.
 *
 * <p>Leaf modules (-api, -core, -facade, -spi, -common) must have a parent whose artifactId ends
 * with -parent.
 */
public class MS012LeafModuleParentMustBeParent extends AbstractStructureRule {

  private static final List<String> LEAF_SUFFIXES =
      List.of("-api", "-core", "-facade", "-spi", "-common");

  public MS012LeafModuleParentMustBeParent() {
    super("MS-012");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    if (module.isParent()) {
      return false;
    }
    String artifactId = module.getArtifactId();
    return LEAF_SUFFIXES.stream().anyMatch(artifactId::endsWith);
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.getPomModel() == null || module.getPomModel().getParent() == null) {
      return List.of(); // MS-011 will catch this
    }

    String parentArtifactId = module.getPomModel().getParent().getArtifactId();
    if (parentArtifactId != null && parentArtifactId.endsWith("-parent")) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            "Leaf module '"
                + module.getArtifactId()
                + "' has parent '"
                + parentArtifactId
                + "' which does not end with -parent. Leaf modules should inherit from proper parent modules."));
  }
}
