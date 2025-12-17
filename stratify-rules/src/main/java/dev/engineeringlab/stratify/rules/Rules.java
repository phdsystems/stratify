package dev.engineeringlab.stratify.rules;

import dev.engineeringlab.stratify.annotation.Facade;
import dev.engineeringlab.stratify.annotation.Provider;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * SEA (Stratified Encapsulation Architecture) rules for validating architecture compliance.
 *
 * <p>Zero-dependency implementation using reflection-based analysis.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Check a single rule
 * Rules.commonLayer("com.example").enforce("com.example");
 *
 * // Check all rules
 * Rules.enforceAll("com.example");
 * }</pre>
 */
public final class Rules {

  private Rules() {}

  /** A rule that can be checked against a set of classes. */
  @FunctionalInterface
  public interface Rule {

    /**
     * Checks this rule against the given classes.
     *
     * @param classes the classes to check
     * @return list of violations (empty if rule passes)
     */
    List<Violation> check(Set<Class<?>> classes);

    /**
     * Checks the rule and throws if violations found.
     *
     * @param classes the classes to check
     * @throws AssertionError if violations found
     */
    default void enforce(Set<Class<?>> classes) {
      List<Violation> violations = check(classes);
      if (!violations.isEmpty()) {
        StringBuilder sb = new StringBuilder("Architecture violations found:\n");
        for (Violation v : violations) {
          sb.append("  - ").append(v).append("\n");
        }
        throw new AssertionError(sb.toString());
      }
    }

    /**
     * Checks the rule against classes in the given package.
     *
     * @param basePackage the package to scan
     * @throws AssertionError if violations found
     */
    default void enforce(String basePackage) {
      enforce(ClassScanner.scan(basePackage));
    }
  }

  /** Rule: Common layer should not depend on higher layers. */
  public static Rule commonLayer(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        if (!isInLayer(clazz, basePackage, "common")) continue;

        Set<Class<?>> badDeps =
            DependencyAnalyzer.getDependenciesInPackages(
                clazz,
                basePackage + "..spi..",
                basePackage + "..api..",
                basePackage + "..core..",
                basePackage + "..impl..");

        if (!badDeps.isEmpty()) {
          violations.add(Violation.dependency("Common layer dependency", clazz, badDeps));
        }
      }

      return violations;
    };
  }

  /** Rule: SPI layer should only depend on Common. */
  public static Rule spiLayer(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        if (!isInLayer(clazz, basePackage, "spi")) continue;

        Set<Class<?>> badDeps =
            DependencyAnalyzer.getDependenciesInPackages(
                clazz, basePackage + "..api..", basePackage + "..core..", basePackage + "..impl..");

        if (!badDeps.isEmpty()) {
          violations.add(Violation.dependency("SPI layer dependency", clazz, badDeps));
        }
      }

      return violations;
    };
  }

  /** Rule: API layer should not depend on Core or Impl. */
  public static Rule apiLayer(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        if (!isInLayer(clazz, basePackage, "api")) continue;

        Set<Class<?>> badDeps =
            DependencyAnalyzer.getDependenciesInPackages(
                clazz, basePackage + "..core..", basePackage + "..impl..");

        if (!badDeps.isEmpty()) {
          violations.add(Violation.dependency("API layer dependency", clazz, badDeps));
        }
      }

      return violations;
    };
  }

  /** Rule: No circular dependencies between slices. */
  public static Rule noCycles(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();
      List<List<String>> cycles = CycleDetector.detectCycles(classes, basePackage);

      for (List<String> cycle : cycles) {
        violations.add(Violation.cycle("Circular dependency", cycle));
      }

      return violations;
    };
  }

  /** Rule: Facade classes should be final. */
  public static Rule facadesFinal(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        boolean isFacade =
            clazz.isAnnotationPresent(Facade.class) || clazz.getSimpleName().endsWith("Facade");

        if (isFacade && !Modifier.isFinal(clazz.getModifiers())) {
          violations.add(Violation.classRule("Facade not final", clazz, "should be final"));
        }
      }

      return violations;
    };
  }

  /** Rule: @Provider classes should implement an interface. */
  public static Rule providersImplement() {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        if (!clazz.isAnnotationPresent(Provider.class)) continue;

        if (clazz.getInterfaces().length == 0) {
          violations.add(
              Violation.classRule(
                  "Provider without interface", clazz, "should implement an interface"));
        }
      }

      return violations;
    };
  }

  /** Rule: Internal classes should not be accessed externally. */
  public static Rule internalHidden(String basePackage) {
    return classes -> {
      List<Violation> violations = new ArrayList<>();

      for (Class<?> clazz : classes) {
        if (isInLayer(clazz, basePackage, "internal")) continue;

        Set<Class<?>> badDeps =
            DependencyAnalyzer.getDependenciesInPackages(clazz, basePackage + "..internal..");

        if (!badDeps.isEmpty()) {
          violations.add(Violation.dependency("Internal class access", clazz, badDeps));
        }
      }

      return violations;
    };
  }

  /** Returns all rules. */
  public static List<Rule> all(String basePackage) {
    return List.of(
        commonLayer(basePackage),
        spiLayer(basePackage),
        apiLayer(basePackage),
        noCycles(basePackage),
        facadesFinal(basePackage),
        providersImplement(),
        internalHidden(basePackage));
  }

  /**
   * Enforces all rules on the given package.
   *
   * @throws AssertionError if any violations found
   */
  public static void enforceAll(String basePackage) {
    Set<Class<?>> classes = ClassScanner.scan(basePackage);
    List<Violation> allViolations = new ArrayList<>();

    for (Rule rule : all(basePackage)) {
      allViolations.addAll(rule.check(classes));
    }

    if (!allViolations.isEmpty()) {
      StringBuilder sb = new StringBuilder("Architecture violations found:\n");
      for (Violation v : allViolations) {
        sb.append("  - ").append(v).append("\n");
      }
      throw new AssertionError(sb.toString());
    }
  }

  private static boolean isInLayer(Class<?> clazz, String basePackage, String layer) {
    Package pkg = clazz.getPackage();
    if (pkg == null) return false;

    String pkgName = pkg.getName();
    return pkgName.startsWith(basePackage) && pkgName.contains("." + layer);
  }
}
