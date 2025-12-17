package dev.engineeringlab.stratify.structure.rule.impl;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Rule that checks if a module has an API submodule.
 *
 * <p>In a stratified architecture, the API module ({base}-api) contains the public contracts,
 * interfaces, and DTOs that external consumers depend on. This rule verifies that for each
 * component base, an API module exists.
 *
 * <p>The rule checks for the presence of {base}-api directories and validates that they contain a
 * pom.xml file to ensure they are proper Maven modules.
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
 * </pre>
 *
 * <p>This rule would pass because {base}-api exists.
 *
 * <p>However, if only my-component-core exists without my-component-api, this rule would report a
 * violation.
 */
public class ApiModuleExists extends AbstractStructureRule {

  private static final Pattern COMPONENT_PATTERN = Pattern.compile("^(.+)-(core|facade|spi)$");

  /**
   * Creates an ApiModuleExists rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public ApiModuleExists() {
    super("SEA-101", "structure-rules");
  }

  /**
   * Creates an ApiModuleExists rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public ApiModuleExists(RuleLoader ruleLoader) {
    super("SEA-101", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to parent directories that contain multiple submodules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that match our component pattern
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> COMPONENT_PATTERN.matcher(name).matches());
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  protected List<StructureViolation> doValidate(Path path) {
    List<StructureViolation> violations = new ArrayList<>();

    try (Stream<Path> entries = Files.list(path)) {
      // Find all component bases that should have an API module
      List<Path> componentModules =
          entries
              .filter(Files::isDirectory)
              .filter(p -> COMPONENT_PATTERN.matcher(p.getFileName().toString()).matches())
              .toList();

      // Group by base name and check for missing API modules
      for (Path modulePath : componentModules) {
        String moduleName = modulePath.getFileName().toString();
        Matcher matcher = COMPONENT_PATTERN.matcher(moduleName);

        if (matcher.matches()) {
          String baseName = matcher.group(1);
          String apiModuleName = baseName + "-api";
          Path apiModulePath = path.resolve(apiModuleName);

          // Check if API module exists and has a pom.xml
          if (!Files.exists(apiModulePath)
              || !Files.isDirectory(apiModulePath)
              || !Files.exists(apiModulePath.resolve("pom.xml"))) {

            violations.add(
                createViolation(
                    baseName,
                    String.format(
                        "Component '%s' is missing its API module. Expected '%s' directory with pom.xml.",
                        baseName, apiModuleName),
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
