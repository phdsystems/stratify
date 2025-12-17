package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-011: Leaf Modules Must Have Parent.
 *
 * <p>Leaf modules (-api, -core, -facade, -spi, -common) must declare a parent POM to maintain
 * consistent dependency management and build settings.
 */
public class MS011LeafModulesMustHaveParent extends AbstractStructureRule {

  private static final List<String> LEAF_SUFFIXES =
      List.of("-api", "-core", "-facade", "-spi", "-common");

  public MS011LeafModulesMustHaveParent() {
    super("MS-011");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // This rule applies to leaf modules (non-parent modules with layer suffixes)
    if (module.isParent()) {
      return false;
    }
    String artifactId = module.getArtifactId();
    return LEAF_SUFFIXES.stream().anyMatch(artifactId::endsWith);
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    // Check if module has a parent declaration in its POM
    if (module.getPomModel() != null && module.getPomModel().getParent() != null) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            "Leaf module '"
                + module.getArtifactId()
                + "' must declare a parent POM. "
                + "Standalone leaf modules break the module hierarchy."));
  }
}
