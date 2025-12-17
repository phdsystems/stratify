package dev.engineeringlab.stratify.rules;

import java.util.List;
import java.util.Set;

/** Represents a single architecture rule violation. */
public record Violation(
    String rule, String message, Class<?> sourceClass, Set<Class<?>> violatingDependencies) {

  /** Creates a violation for a dependency rule. */
  public static Violation dependency(String rule, Class<?> source, Set<Class<?>> deps) {
    String depNames = deps.stream().map(Class::getName).reduce((a, b) -> a + ", " + b).orElse("");
    return new Violation(rule, source.getName() + " depends on: " + depNames, source, deps);
  }

  /** Creates a violation for a class rule (e.g., not final). */
  public static Violation classRule(String rule, Class<?> source, String reason) {
    return new Violation(rule, source.getName() + ": " + reason, source, Set.of());
  }

  /** Creates a violation for a cycle. */
  public static Violation cycle(String rule, List<String> cyclePath) {
    String path = String.join(" -> ", cyclePath);
    return new Violation(rule, "Cycle detected: " + path, null, Set.of());
  }

  @Override
  public String toString() {
    return "[" + rule + "] " + message;
  }
}
