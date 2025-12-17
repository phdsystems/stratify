package dev.engineeringlab.stratify.structure.remediation.core;

import dev.engineeringlab.stratify.structure.remediation.api.Fixer;
import dev.engineeringlab.stratify.structure.remediation.api.FixerRegistry;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default implementation of FixerRegistry.
 *
 * <p>Thread-safe registry that maintains fixers sorted by priority.
 */
public class DefaultFixerRegistry implements FixerRegistry {

  private final List<Fixer> fixers = new CopyOnWriteArrayList<>();

  @Override
  public void register(Fixer fixer) {
    if (fixer != null && !fixers.contains(fixer)) {
      fixers.add(fixer);
      sortByPriority();
    }
  }

  @Override
  public void unregister(Fixer fixer) {
    fixers.remove(fixer);
  }

  @Override
  public Optional<Fixer> findFixerFor(StructureViolation violation) {
    return fixers.stream().filter(Fixer::isEnabled).filter(f -> f.canFix(violation)).findFirst();
  }

  @Override
  public List<Fixer> findAllFixersFor(StructureViolation violation) {
    return fixers.stream().filter(Fixer::isEnabled).filter(f -> f.canFix(violation)).toList();
  }

  @Override
  public List<Fixer> findFixersForRule(String ruleId) {
    return fixers.stream()
        .filter(Fixer::isEnabled)
        .filter(f -> Arrays.asList(f.getAllSupportedRules()).contains(ruleId))
        .toList();
  }

  @Override
  public List<Fixer> getAllFixers() {
    return new ArrayList<>(fixers);
  }

  @Override
  public List<Fixer> getAllEnabledFixers() {
    return fixers.stream().filter(Fixer::isEnabled).toList();
  }

  @Override
  public int size() {
    return fixers.size();
  }

  @Override
  public void clear() {
    fixers.clear();
  }

  private void sortByPriority() {
    fixers.sort(Comparator.comparingInt(Fixer::getPriority).reversed());
  }
}
