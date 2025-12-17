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
 * Rule that enforces the prohibition of {base}-common modules (SEA-4).
 *
 * <p>As per SEA-4 architectural decisions, stratified modules should not use a {base}-common layer.
 * The common layer was previously used for shared constants, enums, and utilities, but this pattern
 * has been deprecated in favor of:
 *
 * <ul>
 *   <li>Placing shared contracts and DTOs in the API module
 *   <li>Placing shared implementation utilities in the core module
 *   <li>Using dedicated utility libraries for cross-cutting concerns
 * </ul>
 *
 * <p>This rule detects any modules ending in "-common" and reports them as violations.
 *
 * <h2>Rationale</h2>
 *
 * <p>The common layer created several issues:
 *
 * <ul>
 *   <li>Unclear ownership and responsibility boundaries
 *   <li>Tendency to become a dumping ground for miscellaneous code
 *   <li>Circular dependency risks between common and other layers
 *   <li>Violated the principle of clear layer separation
 * </ul>
 *
 * <h2>Example Violation</h2>
 *
 * <p>Given a module structure:
 *
 * <pre>
 * my-component/
 *   my-component-api/
 *     pom.xml
 *   my-component-core/
 *     pom.xml
 *   my-component-common/
 *     pom.xml
 * </pre>
 *
 * <p>This rule would report an error for my-component-common.
 */
public class NoCommonLayer extends AbstractStructureRule {

  private static final Pattern COMMON_MODULE_PATTERN = Pattern.compile("^(.+)-common$");

  /**
   * Creates a NoCommonLayer rule with default configuration.
   *
   * <p>Loads rule metadata from structure-rules configuration.
   */
  public NoCommonLayer() {
    super("SEA-4", "structure-rules");
  }

  /**
   * Creates a NoCommonLayer rule with a custom rule loader.
   *
   * @param ruleLoader the rule loader containing rule definitions
   */
  public NoCommonLayer(RuleLoader ruleLoader) {
    super("SEA-4", ruleLoader);
  }

  @Override
  protected boolean appliesTo(Path path) {
    // This rule applies to any directory that might contain modules
    if (!Files.isDirectory(path)) {
      return false;
    }

    // Check if this directory has subdirectories that might be common modules
    try (Stream<Path> entries = Files.list(path)) {
      return entries
          .filter(Files::isDirectory)
          .map(p -> p.getFileName().toString())
          .anyMatch(name -> name.endsWith("-common"));
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
                Matcher matcher = COMMON_MODULE_PATTERN.matcher(moduleName);

                if (matcher.matches()) {
                  String baseName = matcher.group(1);

                  // Verify it's a Maven module by checking for pom.xml
                  if (Files.exists(modulePath.resolve("pom.xml"))) {
                    violations.add(
                        createViolation(
                            moduleName,
                            String.format(
                                "Module '%s' uses the deprecated '-common' layer pattern. "
                                    + "As per SEA-4, common layers are prohibited. "
                                    + "Move shared contracts to '%s-api' and shared utilities to '%s-core', "
                                    + "or create a dedicated utility library.",
                                moduleName, baseName, baseName),
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
