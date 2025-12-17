package dev.engineeringlab.stratify.structure.remediation.core;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.Fixer;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.FixerRegistry;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.config.StructureFixerConfig;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Orchestrates the execution of structure fixers.
 *
 * <p>Coordinates multiple fixers, manages execution order, and aggregates results.
 */
public class FixerOrchestrator {

  private final FixerRegistry registry;
  private final StructureFixerConfig config;
  private Consumer<String> logger = System.out::println;

  public FixerOrchestrator(FixerRegistry registry, StructureFixerConfig config) {
    this.registry = registry;
    this.config = config;
  }

  public FixerOrchestrator(StructureFixerConfig config) {
    this(new DefaultFixerRegistry(), config);
  }

  /** Sets the logger for this orchestrator. */
  public void setLogger(Consumer<String> logger) {
    this.logger = logger;
  }

  /** Returns the fixer registry. */
  public FixerRegistry getRegistry() {
    return registry;
  }

  /**
   * Fixes all violations using registered fixers.
   *
   * @param violations the violations to fix
   * @param context the execution context
   * @return list of fix results
   */
  public List<FixResult> fixAll(List<StructureViolation> violations, FixerContext context) {
    List<FixResult> results = new ArrayList<>();

    // Group violations by rule for efficient processing
    Map<String, List<StructureViolation>> byRule =
        violations.stream()
            .filter(v -> config.isRuleEnabled(v.ruleId()))
            .collect(Collectors.groupingBy(StructureViolation::ruleId));

    // Process each rule group
    for (Map.Entry<String, List<StructureViolation>> entry : byRule.entrySet()) {
      String ruleId = entry.getKey();
      List<StructureViolation> ruleViolations = entry.getValue();

      List<Fixer> fixers = registry.findFixersForRule(ruleId);
      if (fixers.isEmpty()) {
        // No fixer available for this rule
        for (StructureViolation v : ruleViolations) {
          results.add(FixResult.notFixable(v, "No fixer registered for rule: " + ruleId));
        }
        continue;
      }

      // Use highest priority fixer
      Fixer fixer = fixers.get(0);
      if (!config.isFixerEnabled(fixer.getName())) {
        for (StructureViolation v : ruleViolations) {
          results.add(FixResult.skipped(v, "Fixer disabled: " + fixer.getName()));
        }
        continue;
      }

      logger.accept(
          "Running " + fixer.getName() + " for " + ruleViolations.size() + " violation(s)");

      List<FixResult> fixerResults = fixer.fixAll(ruleViolations, context);
      results.addAll(fixerResults);
    }

    logSummary(results);
    return results;
  }

  /**
   * Fixes a single violation.
   *
   * @param violation the violation to fix
   * @param context the execution context
   * @return the fix result
   */
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!config.isRuleEnabled(violation.ruleId())) {
      return FixResult.skipped(violation, "Rule disabled: " + violation.ruleId());
    }

    return registry
        .findFixerFor(violation)
        .filter(f -> config.isFixerEnabled(f.getName()))
        .map(
            fixer -> {
              logger.accept("Running " + fixer.getName() + " for " + violation.ruleId());
              return fixer.fix(violation, context);
            })
        .orElse(FixResult.notFixable(violation, "No fixer available"));
  }

  private void logSummary(List<FixResult> results) {
    long fixed = results.stream().filter(r -> r.status() == FixStatus.FIXED).count();
    long failed = results.stream().filter(FixResult::isFailed).count();
    long skipped = results.stream().filter(r -> r.status() == FixStatus.SKIPPED).count();
    long notFixable = results.stream().filter(r -> r.status() == FixStatus.NOT_FIXABLE).count();
    long dryRun = results.stream().filter(r -> r.status() == FixStatus.DRY_RUN).count();

    logger.accept("");
    logger.accept("═══════════════════════════════════════════════════════════");
    logger.accept("  Fix Summary");
    logger.accept("═══════════════════════════════════════════════════════════");
    logger.accept("  Total:       " + results.size());
    if (dryRun > 0) {
      logger.accept("  Would Fix:   " + dryRun + " (dry-run)");
    } else {
      logger.accept("  Fixed:       " + fixed);
    }
    logger.accept("  Failed:      " + failed);
    logger.accept("  Skipped:     " + skipped);
    logger.accept("  Not Fixable: " + notFixable);
    logger.accept("═══════════════════════════════════════════════════════════");
  }
}
