package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * AG-005: Aggregator Maven Wrapper Exists.
 *
 * <p>Pure aggregator modules MUST have Maven wrapper files (mvnw, mvnw.cmd, and .mvn directory) to
 * ensure consistent, reproducible builds across different environments. This removes the dependency
 * on having Maven installed on the system.
 *
 * <p>Required files:
 *
 * <ul>
 *   <li>mvnw - Unix/macOS shell script
 *   <li>mvnw.cmd - Windows batch script
 *   <li>.mvn/wrapper/maven-wrapper.properties - Wrapper configuration
 * </ul>
 *
 * <p>Note: maven-wrapper.jar is optional when using distributionType=only-script.
 */
public class AG005AggregatorMavenWrapperExists extends AbstractStructureRule {

  public static final String MVNW = "mvnw";
  public static final String MVNW_CMD = "mvnw.cmd";
  public static final String MAVEN_WRAPPER_DIR = ".mvn/wrapper";
  public static final String MAVEN_WRAPPER_PROPERTIES = ".mvn/wrapper/maven-wrapper.properties";

  public AG005AggregatorMavenWrapperExists() {
    super("AG-005");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to pure aggregator modules (parent with no layer submodules)
    if (!module.isParent()) {
      return false;
    }
    // Must be pure aggregator: no api/core/facade/spi submodules
    // Uses hasAnyLayerModules() which checks both standard naming and moduleOrder
    return !module.hasAnyLayerModules();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path basePath = module.getBasePath();

    // Check for mvnw
    if (!Files.exists(basePath.resolve(MVNW))) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Pure aggregator '%s' must have Maven wrapper script '%s'. "
                      + "Maven wrapper ensures reproducible builds without requiring Maven installation.",
                  module.getArtifactId(), MVNW),
              basePath.resolve(MVNW).toString()));
    }

    // Check for mvnw.cmd
    if (!Files.exists(basePath.resolve(MVNW_CMD))) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Pure aggregator '%s' must have Maven wrapper batch script '%s' for Windows support.",
                  module.getArtifactId(), MVNW_CMD),
              basePath.resolve(MVNW_CMD).toString()));
    }

    // Check for .mvn/wrapper directory and properties
    if (!Files.exists(basePath.resolve(MAVEN_WRAPPER_DIR))) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Pure aggregator '%s' must have Maven wrapper directory '%s' with maven-wrapper.properties.",
                  module.getArtifactId(), MAVEN_WRAPPER_DIR),
              basePath.resolve(MAVEN_WRAPPER_DIR).toString()));
    } else {
      // Check for maven-wrapper.properties (required)
      if (!Files.exists(basePath.resolve(MAVEN_WRAPPER_PROPERTIES))) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Pure aggregator '%s' must have Maven wrapper properties '%s'.",
                    module.getArtifactId(), MAVEN_WRAPPER_PROPERTIES),
                basePath.resolve(MAVEN_WRAPPER_PROPERTIES).toString()));
      }
      // Note: maven-wrapper.jar is optional when using distributionType=only-script
    }

    return violations;
  }
}
