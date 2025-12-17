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
 * Rule that checks if a module has a facade submodule when appropriate.
 *
 * <p>In a stratified architecture, the facade module ({base}-facade) is optional but recommended
 * for components with multiple submodules. The facade provides a simplified, unified API for
 * external consumers.
 *
 * <p>This rule checks if components with both API and core modules might benefit from having a
 * facade module. It reports informational violations suggesting the creation of a facade when the
 * component appears to be a candidate (based on having multiple submodules).
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
 *   my-component-spi/
 *     pom.xml
 * </pre>
 *
 * <p>This rule might suggest creating a facade module since the component has multiple submodules
 * (api, core, spi).
 */
public class FacadeModuleExists extends AbstractStructureRule {

  private static final Pattern LAYER_PATTERN = Pattern.compile("^(.+)-(api|core|spi|facade)$");

  /**
   * Creates a FacadeModuleExists rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public FacadeModuleExists() {
    super("SEA-103", "structure-rules");
  }

  /**
   * Creates a FacadeModuleExists rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public FacadeModuleExists(RuleLoader ruleLoader) {
    super("SEA-103", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to parent directories that contain multiple submodules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that match our layer pattern
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> LAYER_PATTERN.matcher(name).matches());
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
      Set<String> facadeBases = new HashSet<>();

      entries
          .filter(Files::isDirectory)
          .forEach(
              modulePath -> {
                String moduleName = modulePath.getFileName().toString();
                Matcher matcher = LAYER_PATTERN.matcher(moduleName);

                if (matcher.matches()) {
                  String baseName = matcher.group(1);
                  String layer = matcher.group(2);

                  componentBases.add(baseName);

                  if ("facade".equals(layer)) {
                    facadeBases.add(baseName);
                  }
                }
              });

      // Check each component base to see if it should have a facade
      for (String baseName : componentBases) {
        if (!facadeBases.contains(baseName)) {
          Path apiPath = path.resolve(baseName + "-api");
          Path corePath = path.resolve(baseName + "-core");
          Path spiPath = path.resolve(baseName + "-spi");

          // Suggest facade if the component has api, core, and potentially spi
          boolean hasApi = Files.exists(apiPath) && Files.isDirectory(apiPath);
          boolean hasCore = Files.exists(corePath) && Files.isDirectory(corePath);
          boolean hasSpi = Files.exists(spiPath) && Files.isDirectory(spiPath);

          if (hasApi && hasCore && hasSpi) {
            violations.add(
                createViolation(
                    baseName,
                    String.format(
                        "Component '%s' has multiple submodules (api, core, spi) and might benefit from a facade module '%s-facade' to provide a simplified external API.",
                        baseName, baseName),
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
