package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-027: Parent Must Declare Dependency Management.
 *
 * <p>Parent modules (-parent suffix) must have a {@code <dependencyManagement>} section in their
 * pom.xml to manage common dependencies for child modules.
 *
 * <p>The {@code <dependencyManagement>} section centralizes version management and ensures
 * consistent dependency versions across all child modules. Without this section, each child module
 * must declare its own dependency versions, leading to version inconsistencies and potential
 * compatibility issues.
 */
public class MS027ParentMustHaveDepManagement extends AbstractStructureRule {

  public MS027ParentMustHaveDepManagement() {
    super("MS-027");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Only applies to parent modules (artifactId ends with -parent)
    // Skip aggregator modules (they don't need dependencyManagement)
    if (module.getArtifactId() == null) {
      return false;
    }
    if (module.getArtifactId().endsWith("-aggregator")) {
      return false;
    }
    return module.getArtifactId().endsWith("-parent");
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    if (module.getPomModel() == null) {
      return List.of();
    }

    // Check if dependencyManagement section exists
    if (module.getPomModel().getDependencyManagement() == null) {
      return List.of(
          createViolation(
              module,
              "Parent module '"
                  + module.getArtifactId()
                  + "' is missing <dependencyManagement> section. "
                  + "Parent modules are responsible for declaring common dependencies that children inherit."));
    }

    return List.of();
  }
}
