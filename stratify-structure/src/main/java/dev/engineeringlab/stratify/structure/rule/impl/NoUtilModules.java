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
 * Rule that prohibits {base}-util and {base}-utils modules.
 *
 * <p>This rule enforces architectural standards by preventing the creation of utility modules as
 * part of stratified components. Utility modules ({base}-util or {base}-utils) are anti-patterns
 * that:
 *
 * <ul>
 *   <li>Create unclear boundaries and responsibilities
 *   <li>Tend to become dumping grounds for miscellaneous code
 *   <li>Violate single responsibility principle
 *   <li>Make refactoring and maintenance more difficult
 * </ul>
 *
 * <h2>Recommended Alternatives</h2>
 *
 * <p>Instead of creating utility modules, developers should:
 *
 * <ul>
 *   <li>Place component-specific utilities in the core module where they're used
 *   <li>Create dedicated, well-named modules for cross-cutting concerns (e.g., {base}-validation,
 *       {base}-serialization)
 *   <li>Use existing utility libraries (Apache Commons, Guava, etc.) for general-purpose utilities
 *   <li>Extract truly shared utilities into separate, reusable library projects
 * </ul>
 *
 * <h2>Example Violations</h2>
 *
 * <p>Given a module structure:
 *
 * <pre>
 * my-component/
 *   my-component-api/
 *     pom.xml
 *   my-component-core/
 *     pom.xml
 *   my-component-util/
 *     pom.xml
 * </pre>
 *
 * <p>This rule would report an error for my-component-util.
 *
 * <p>Similarly:
 *
 * <pre>
 * another-component/
 *   another-component-api/
 *     pom.xml
 *   another-component-core/
 *     pom.xml
 *   another-component-utils/
 *     pom.xml
 * </pre>
 *
 * <p>This rule would report an error for another-component-utils.
 */
public class NoUtilModules extends AbstractStructureRule {

  private static final Pattern UTIL_MODULE_PATTERN = Pattern.compile("^(.+)-utils?$");

  /**
   * Creates a NoUtilModules rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public NoUtilModules() {
    super("SEA-106", "structure-rules");
  }

  /**
   * Creates a NoUtilModules rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public NoUtilModules(RuleLoader ruleLoader) {
    super("SEA-106", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to any directory that might contain modules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that might be util modules
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> name.endsWith("-util") || name.endsWith("-utils"));
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  protected List<StructureViolation> doValidate(Path path) {
    List<StructureViolation> violations = new ArrayList<>();

    try (Stream<Path> entries = Files.list(path)) {
      entries
          .filter(Files::isDirectory)
          .forEach(
              modulePath -> {
                String moduleName = modulePath.getFileName().toString();
                Matcher matcher = UTIL_MODULE_PATTERN.matcher(moduleName);

                if (matcher.matches()) {
                  String baseName = matcher.group(1);
                  String suffix = moduleName.endsWith("-utils") ? "-utils" : "-util";

                  // Verify it's a Maven module by checking for pom.xml
                  if (Files.exists(modulePath.resolve("pom.xml"))) {
                    violations.add(
                        createViolation(
                            moduleName,
                            String.format(
                                "Module '%s' uses the anti-pattern '%s' suffix. "
                                    + "Utility modules create unclear boundaries and tend to become dumping grounds. "
                                    + "Instead: (1) Move component-specific utilities to '%s-core', "
                                    + "(2) Create focused modules with clear names (e.g., '%s-validation'), "
                                    + "(3) Use existing utility libraries, or "
                                    + "(4) Extract truly shared utilities into a separate library project.",
                                moduleName, suffix, baseName, baseName),
                            modulePath.toString()));
                  }
                }
              });
    } catch (IOException e) {
      // If we can't read the directory, we can't validate it
      return List.of();
    }

    return violations;
  }
}
