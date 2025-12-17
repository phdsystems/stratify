package dev.engineeringlab.stratify.structure;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import java.nio.file.Path;
import java.util.List;

/**
 * Functional interface for structure validation rules.
 *
 * <p>Structure rules validate project and module organization against architectural standards. Each
 * rule examines a path (typically a module root or project root) and returns a list of violations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * StructureRule rule = StructureRules.noCommonLayer();
 * List<StructureViolation> violations = rule.check(Path.of("my-project"));
 *
 * // Or enforce (throws on violation)
 * rule.enforce(Path.of("my-project"));
 * }</pre>
 *
 * @see StructureRules for convenient factory methods
 */
@FunctionalInterface
public interface StructureRule {

  /**
   * Checks this rule against the given path.
   *
   * @param path the path to validate (typically a module root or project root directory)
   * @return list of violations found (empty if rule passes)
   */
  List<StructureViolation> check(Path path);

  /**
   * Checks the rule and throws if violations are found.
   *
   * <p>This is a convenience method that combines {@link #check(Path)} with exception throwing. It
   * is useful in test scenarios where violations should cause test failures.
   *
   * @param path the path to validate
   * @throws AssertionError if violations are found
   */
  default void enforce(Path path) {
    List<StructureViolation> violations = check(path);
    if (!violations.isEmpty()) {
      StringBuilder sb = new StringBuilder("Structure violations found:\n");
      for (StructureViolation v : violations) {
        sb.append("  - ").append(v).append("\n");
      }
      throw new AssertionError(sb.toString());
    }
  }
}
