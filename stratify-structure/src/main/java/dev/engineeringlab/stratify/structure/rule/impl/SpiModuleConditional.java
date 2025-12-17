package dev.engineeringlab.stratify.structure.rule.impl;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that warns if an SPI (Service Provider Interface) module should exist.
 *
 * <p>In a stratified architecture, the SPI module ({base}-spi) is optional but recommended for
 * components that need to support extensibility through plugin mechanisms or alternative
 * implementations.
 *
 * <p>This rule provides warnings when it detects patterns that suggest an SPI module might be
 * beneficial. For example, if a component has multiple implementations or uses interfaces
 * extensively in its API, an SPI module might help separate the extension contract from the main
 * API.
 *
 * <h2>Example</h2>
 *
 * <p>Given a module structure:
 *
 * <pre>
 * my-component/
 *   my-component-api/
 *     pom.xml
 *   my-component-core/
 *     pom.xml
 *   my-component-impl1/
 *     pom.xml
 *   my-component-impl2/
 *     pom.xml
 * </pre>
 *
 * <p>This rule might suggest creating an SPI module to define extension points, since there appear
 * to be multiple implementations.
 */
public class SpiModuleConditional extends AbstractStructureRule {

  private static final Pattern LAYER_PATTERN = Pattern.compile("^(.+)-(api|core|spi|facade)$");
  private static final Pattern IMPL_PATTERN = Pattern.compile("^(.+)-impl.*$");

  /**
   * Creates an SpiModuleConditional rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public SpiModuleConditional() {
    super("SEA-104", "structure-rules");
  }

  /**
   * Creates an SpiModuleConditional rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public SpiModuleConditional(RuleLoader ruleLoader) {
    super("SEA-104", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to parent directories that contain multiple submodules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that match our patterns
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(
              name ->
                  LAYER_PATTERN.matcher(name).matches() || IMPL_PATTERN.matcher(name).matches());
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  protected List<StructureViolation> doValidate(Path path) {
    List<StructureViolation> violations = new ArrayList<>();

    try (Stream<Path> entries = Files.list(path)) {
      // Group modules by their base name
      Set<String> componentBases = new HashSet<>();
      Set<String> spiBases = new HashSet<>();
      Set<String> basesWithMultipleImpls = new HashSet<>();

      List<Path> allModules = entries.filter(Files::isDirectory).toList();

      for (Path modulePath : allModules) {
        String moduleName = modulePath.getFileName().toString();

        // Check for standard layers
        Matcher layerMatcher = LAYER_PATTERN.matcher(moduleName);
        if (layerMatcher.matches()) {
          String baseName = layerMatcher.group(1);
          String layer = layerMatcher.group(2);

          componentBases.add(baseName);

          if ("spi".equals(layer)) {
            spiBases.add(baseName);
          }
        }

        // Check for impl modules
        Matcher implMatcher = IMPL_PATTERN.matcher(moduleName);
        if (implMatcher.matches()) {
          String baseName = implMatcher.group(1);
          basesWithMultipleImpls.add(baseName);
        }
      }

      // Warn if a component has multiple implementations but no SPI
      for (String baseName : basesWithMultipleImpls) {
        if (componentBases.contains(baseName) && !spiBases.contains(baseName)) {
          long implCount =
              allModules.stream()
                  .map(p -> p.getFileName().toString())
                  .filter(name -> name.startsWith(baseName + "-impl"))
                  .count();

          if (implCount > 1) {
            violations.add(
                createViolation(
                    baseName,
                    String.format(
                        "Component '%s' has multiple implementation modules (%d found) but no SPI module. "
                            + "Consider creating '%s-spi' to define extension points and plugin contracts.",
                        baseName, implCount, baseName),
                    path.toString()));
          }
        }
      }
    } catch (IOException e) {
      // If we can't read the directory, we can't validate it
      return List.of();
    }

    return violations;
  }
}
