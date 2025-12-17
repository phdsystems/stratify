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
 * Rule that checks if a module has a core submodule.
 *
 * <p>In a stratified architecture, the core module ({base}-core) contains the implementation logic
 * and business rules. This rule verifies that for each component base, a core module exists.
 *
 * <p>The rule checks for the presence of {base}-core directories and validates that they contain a
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
 * <p>This rule would pass because {base}-core exists.
 *
 * <p>However, if only my-component-api exists without my-component-core, this rule would report a
 * violation.
 */
public class CoreModuleExists extends AbstractStructureRule {

  private static final Pattern COMPONENT_PATTERN = Pattern.compile("^(.+)-(api|facade|spi)$");

  /**
   * Creates a CoreModuleExists rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public CoreModuleExists() {
    super("SEA-102", "structure-rules");
  }

  /**
   * Creates a CoreModuleExists rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public CoreModuleExists(RuleLoader ruleLoader) {
    super("SEA-102", ruleLoader);
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
      // Find all component bases that should have a core module
      List<Path> componentModules =
          entries
              .filter(Files::isDirectory)
              .filter(p -> COMPONENT_PATTERN.matcher(p.getFileName().toString()).matches())
              .toList();

      // Group by base name and check for missing core modules
      for (Path modulePath : componentModules) {
        String moduleName = modulePath.getFileName().toString();
        Matcher matcher = COMPONENT_PATTERN.matcher(moduleName);

        if (matcher.matches()) {
          String baseName = matcher.group(1);
          String coreModuleName = baseName + "-core";
          Path coreModulePath = path.resolve(coreModuleName);

          // Check if core module exists and has a pom.xml
          if (!Files.exists(coreModulePath)
              || !Files.isDirectory(coreModulePath)
              || !Files.exists(coreModulePath.resolve("pom.xml"))) {

            violations.add(
                createViolation(
                    baseName,
                    String.format(
                        "Component '%s' is missing its core module. Expected '%s' directory with pom.xml.",
                        baseName, coreModuleName),
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
