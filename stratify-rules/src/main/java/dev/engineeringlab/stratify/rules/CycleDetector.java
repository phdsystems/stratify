package dev.engineeringlab.stratify.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Detects cycles in package dependencies using DFS. */
public final class CycleDetector {

  private CycleDetector() {}

  /**
   * Detects cycles in package dependencies.
   *
   * @param classes the classes to analyze
   * @param basePackage the base package for grouping (uses first segment after base)
   * @return list of cycles found, each cycle is a list of package names
   */
  public static List<List<String>> detectCycles(Set<Class<?>> classes, String basePackage) {
    // Build package dependency graph
    Map<String, Set<String>> graph = buildPackageGraph(classes, basePackage);

    // Find cycles using DFS
    return findCycles(graph);
  }

  /**
   * Checks if there are any cycles in package dependencies.
   *
   * @param classes the classes to analyze
   * @param basePackage the base package
   * @return true if cycles exist
   */
  public static boolean hasCycles(Set<Class<?>> classes, String basePackage) {
    return !detectCycles(classes, basePackage).isEmpty();
  }

  private static Map<String, Set<String>> buildPackageGraph(
      Set<Class<?>> classes, String basePackage) {
    Map<String, Set<String>> graph = new HashMap<>();

    for (Class<?> clazz : classes) {
      String fromSlice = getSlice(clazz, basePackage);
      if (fromSlice == null) continue;

      graph.computeIfAbsent(fromSlice, k -> new HashSet<>());

      for (Class<?> dep : DependencyAnalyzer.getDependencies(clazz)) {
        String toSlice = getSlice(dep, basePackage);
        if (toSlice != null && !toSlice.equals(fromSlice)) {
          graph.get(fromSlice).add(toSlice);
          graph.computeIfAbsent(toSlice, k -> new HashSet<>());
        }
      }
    }

    return graph;
  }

  /**
   * Gets the "slice" (first package segment after base) for a class. Example:
   * basePackage="com.example", class in "com.example.foo.bar" -> "foo"
   */
  private static String getSlice(Class<?> clazz, String basePackage) {
    Package pkg = clazz.getPackage();
    if (pkg == null) return null;

    String pkgName = pkg.getName();
    if (!pkgName.startsWith(basePackage)) return null;

    String remainder = pkgName.substring(basePackage.length());
    if (remainder.startsWith(".")) {
      remainder = remainder.substring(1);
    }

    if (remainder.isEmpty()) return null;

    int dotIndex = remainder.indexOf('.');
    return dotIndex > 0 ? remainder.substring(0, dotIndex) : remainder;
  }

  private static List<List<String>> findCycles(Map<String, Set<String>> graph) {
    List<List<String>> cycles = new ArrayList<>();
    Set<String> visited = new HashSet<>();
    Set<String> inStack = new HashSet<>();
    List<String> stack = new ArrayList<>();

    for (String node : graph.keySet()) {
      if (!visited.contains(node)) {
        dfs(node, graph, visited, inStack, stack, cycles);
      }
    }

    return cycles;
  }

  private static void dfs(
      String node,
      Map<String, Set<String>> graph,
      Set<String> visited,
      Set<String> inStack,
      List<String> stack,
      List<List<String>> cycles) {

    visited.add(node);
    inStack.add(node);
    stack.add(node);

    Set<String> neighbors = graph.getOrDefault(node, Set.of());
    for (String neighbor : neighbors) {
      if (!visited.contains(neighbor)) {
        dfs(neighbor, graph, visited, inStack, stack, cycles);
      } else if (inStack.contains(neighbor)) {
        // Found a cycle - extract it from the stack
        List<String> cycle = new ArrayList<>();
        int startIndex = stack.indexOf(neighbor);
        for (int i = startIndex; i < stack.size(); i++) {
          cycle.add(stack.get(i));
        }
        cycle.add(neighbor); // Close the cycle
        cycles.add(cycle);
      }
    }

    stack.remove(stack.size() - 1);
    inStack.remove(node);
  }
}
