package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MS-026: Pure Aggregator Must Not Have Dependency Management.
 *
 * <p>Pure aggregator modules (artifactId ending with -aggregator) should ONLY have a {@code
 * <modules>} section in their pom.xml. They should NOT have {@code <dependencyManagement>} as this
 * is the responsibility of parent modules (-parent suffix).
 *
 * <p>Pure aggregators are meant to group and organize modules, not to manage dependencies.
 * Dependency management belongs in parent modules that have layer submodules (api/core/facade/spi).
 */
public class MS026PureAggregatorNoDepManagement extends AbstractStructureRule {

  public MS026PureAggregatorNoDepManagement() {
    super("MS-026");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to pure aggregator modules (with pom packaging and modules section)
    if (!module.isParent()) {
      return false;
    }
    // Check if it's a pure aggregator (no layer submodules)
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    return !module.hasAnyLayerModules();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Path pomFile = module.getBasePath().resolve("pom.xml");

    if (!Files.exists(pomFile)) {
      // Should not happen, but handle gracefully
      return List.of();
    }

    try {
      String pomContent = Files.readString(pomFile);

      // Check if pom.xml contains <dependencyManagement> section
      if (pomContent.contains("<dependencyManagement>")) {
        return List.of(
            createViolation(
                module,
                "Pure aggregator module '"
                    + module.getArtifactId()
                    + "' must not have <dependencyManagement> section. "
                    + "Pure aggregators should ONLY have a <modules> section. "
                    + "Dependency management belongs in parent modules (-parent suffix)."));
      }

      return List.of();

    } catch (IOException e) {
      // If we can't read the pom.xml, skip validation
      return List.of();
    }
  }
}
