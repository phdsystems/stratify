package dev.engineeringlab.stratify.structure.scanner;

import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File-based scanner for SEA module structure.
 *
 * <p>This scanner uses only {@link java.nio.file} APIs and has zero external dependencies. It
 * discovers modules by walking the directory tree and identifying directories that contain a {@code
 * pom.xml} file.
 *
 * <p>The scanner recognizes the following submodule layer suffixes:
 *
 * <ul>
 *   <li>{@code -api} - Public API interfaces
 *   <li>{@code -core} - Implementation logic
 *   <li>{@code -facade} - Simplified external APIs
 *   <li>{@code -spi} - Service Provider Interface for extensibility
 *   <li>{@code -common} - Shared utilities (discouraged in SEA-4)
 *   <li>{@code -util} or {@code -utils} - Utility classes
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * Path projectRoot = Paths.get("/path/to/project");
 * List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);
 * modules.forEach(module -> {
 *     System.out.println("Module: " + module.baseName());
 *     System.out.println("SEA-4 Compliant: " + module.isSea4Compliant());
 * });
 * }</pre>
 *
 * @see ModuleInfo
 */
public final class ModuleScanner {

  /**
   * Pattern for matching layer suffixes in module directory names.
   *
   * <p>Captures two groups:
   *
   * <ol>
   *   <li>The base name of the module (e.g., "text-processor")
   *   <li>The layer suffix (e.g., "api", "core", "facade")
   * </ol>
   */
  private static final Pattern LAYER_PATTERN =
      Pattern.compile("^(.+)-(api|core|facade|spi|common|util|utils)$");

  /** Maximum depth to search for modules (prevents infinite recursion). */
  private static final int MAX_DEPTH = 10;

  /** Private constructor to prevent instantiation. */
  private ModuleScanner() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Scans the project directory for SEA module structure.
   *
   * <p>This method walks the directory tree starting from {@code projectRoot}, looking for
   * directories that contain a {@code pom.xml} file. It then groups these directories by their base
   * name (derived from the directory name minus the layer suffix) and constructs a {@link
   * ModuleInfo} for each logical module.
   *
   * @param projectRoot the root directory of the project to scan
   * @return a list of discovered modules, or an empty list if none are found
   * @throws IllegalArgumentException if projectRoot is null or not a directory
   * @throws IOException if an I/O error occurs during scanning
   */
  public static List<ModuleInfo> scan(Path projectRoot) throws IOException {
    if (projectRoot == null) {
      throw new IllegalArgumentException("projectRoot cannot be null");
    }
    if (!Files.isDirectory(projectRoot)) {
      throw new IllegalArgumentException("projectRoot must be a directory: " + projectRoot);
    }

    // Step 1: Find all directories containing pom.xml
    List<Path> moduleDirectories = new ArrayList<>();
    try (Stream<Path> paths = Files.walk(projectRoot, MAX_DEPTH)) {
      paths
          .filter(Files::isDirectory)
          .filter(path -> Files.exists(path.resolve("pom.xml")))
          .forEach(moduleDirectories::add);
    }

    // Step 2 & 3: Extract base names and group by base name
    Map<String, Map<String, Path>> moduleGroups = new HashMap<>();
    for (Path modulePath : moduleDirectories) {
      String directoryName = modulePath.getFileName().toString();
      Matcher matcher = LAYER_PATTERN.matcher(directoryName);

      if (matcher.matches()) {
        String baseName = matcher.group(1);
        String layer = matcher.group(2);

        // Normalize "utils" to "util"
        if ("utils".equals(layer)) {
          layer = "util";
        }

        moduleGroups.computeIfAbsent(baseName, k -> new HashMap<>()).put(layer, modulePath);
      }
    }

    // Step 4: Build ModuleInfo for each component
    List<ModuleInfo> modules = new ArrayList<>();
    for (Map.Entry<String, Map<String, Path>> entry : moduleGroups.entrySet()) {
      String baseName = entry.getKey();
      Map<String, Path> layers = entry.getValue();

      // Use the parent directory as the module path (common parent of all layers)
      Path modulePath =
          layers.values().stream()
              .findFirst()
              .map(Path::getParent)
              .orElseThrow(() -> new IllegalStateException("No layers found for: " + baseName));

      ModuleInfo.Builder builder = ModuleInfo.builder(baseName, modulePath);
      builder.hasApi(layers.containsKey("api"));
      builder.hasCore(layers.containsKey("core"));
      builder.hasFacade(layers.containsKey("facade"));
      builder.hasSpi(layers.containsKey("spi"));
      builder.hasCommon(layers.containsKey("common"));
      builder.hasUtil(layers.containsKey("util"));

      modules.add(builder.build());
    }

    return modules;
  }

  /**
   * Scans a single module directory to determine its layer composition.
   *
   * <p>This method is useful when you already know the location of a specific module and want to
   * analyze its structure without scanning the entire project.
   *
   * <p>The method examines immediate child directories of {@code modulePath} that contain a {@code
   * pom.xml} file and match the expected layer naming pattern.
   *
   * @param modulePath the path to the module directory to scan
   * @return an Optional containing the ModuleInfo if valid submodules are found, or empty if no
   *     valid submodules exist
   * @throws IllegalArgumentException if modulePath is null or not a directory
   * @throws IOException if an I/O error occurs during scanning
   */
  public static Optional<ModuleInfo> scanModule(Path modulePath) throws IOException {
    if (modulePath == null) {
      throw new IllegalArgumentException("modulePath cannot be null");
    }
    if (!Files.isDirectory(modulePath)) {
      throw new IllegalArgumentException("modulePath must be a directory: " + modulePath);
    }

    // Find all immediate child directories containing pom.xml
    Map<String, Boolean> layers = new HashMap<>();
    try (Stream<Path> paths = Files.list(modulePath)) {
      paths
          .filter(Files::isDirectory)
          .filter(path -> Files.exists(path.resolve("pom.xml")))
          .forEach(
              path -> {
                String directoryName = path.getFileName().toString();
                Matcher matcher = LAYER_PATTERN.matcher(directoryName);
                if (matcher.matches()) {
                  String layer = matcher.group(2);
                  // Normalize "utils" to "util"
                  if ("utils".equals(layer)) {
                    layer = "util";
                  }
                  layers.put(layer, true);
                }
              });
    }

    // If no valid layers found, return empty
    if (layers.isEmpty()) {
      return Optional.empty();
    }

    // Derive base name from the first matching submodule
    String baseName = null;
    try (Stream<Path> paths = Files.list(modulePath)) {
      baseName =
          paths
              .filter(Files::isDirectory)
              .filter(path -> Files.exists(path.resolve("pom.xml")))
              .map(path -> path.getFileName().toString())
              .map(LAYER_PATTERN::matcher)
              .filter(Matcher::matches)
              .map(matcher -> matcher.group(1))
              .findFirst()
              .orElse(null);
    }

    if (baseName == null) {
      return Optional.empty();
    }

    ModuleInfo.Builder builder = ModuleInfo.builder(baseName, modulePath);
    builder.hasApi(layers.getOrDefault("api", false));
    builder.hasCore(layers.getOrDefault("core", false));
    builder.hasFacade(layers.getOrDefault("facade", false));
    builder.hasSpi(layers.getOrDefault("spi", false));
    builder.hasCommon(layers.getOrDefault("common", false));
    builder.hasUtil(layers.getOrDefault("util", false));

    return Optional.of(builder.build());
  }
}
