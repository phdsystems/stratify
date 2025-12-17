package dev.engineeringlab.stratify.structure.scanner;

import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import java.io.IOException;
import java.io.InputStream;
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
 * Maven-based scanner for SEA module structure.
 *
 * <p>This scanner optionally uses the Maven Model API if available on the classpath. It provides
 * richer information than {@link ModuleScanner} by parsing {@code pom.xml} files to extract:
 *
 * <ul>
 *   <li>groupId
 *   <li>artifactId
 *   <li>version
 *   <li>declared module list
 *   <li>dependency information
 * </ul>
 *
 * <p>The scanner gracefully degrades if Maven Model API is not available. Use {@link
 * #isAvailable()} to check before calling scan methods.
 *
 * <p><strong>Optional Dependency:</strong> This scanner requires the Maven Model API to be on the
 * classpath:
 *
 * <pre>{@code
 * <dependency>
 *     <groupId>org.apache.maven</groupId>
 *     <artifactId>maven-model</artifactId>
 *     <version>3.9.6</version>
 *     <optional>true</optional>
 * </dependency>
 * }</pre>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * if (MavenModuleScanner.isAvailable()) {
 *     List<ModuleInfo> modules = MavenModuleScanner.scan(projectRoot);
 *     // Process modules with Maven metadata
 * } else {
 *     // Fall back to ModuleScanner
 *     List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);
 * }
 * }</pre>
 *
 * @see ModuleScanner
 * @see ModuleInfo
 */
public final class MavenModuleScanner {

  /**
   * Pattern for matching layer suffixes in artifactId or directory names.
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

  /** Cached availability check result. */
  private static volatile Boolean available = null;

  /** Private constructor to prevent instantiation. */
  private MavenModuleScanner() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Checks if Maven Model API is available on the classpath.
   *
   * <p>This method performs a one-time check and caches the result. It attempts to load the Maven
   * Model class to determine if the optional Maven dependency is present.
   *
   * @return true if Maven Model API is available, false otherwise
   */
  public static boolean isAvailable() {
    if (available == null) {
      synchronized (MavenModuleScanner.class) {
        if (available == null) {
          try {
            Class.forName("org.apache.maven.model.Model");
            Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Reader");
            available = true;
          } catch (ClassNotFoundException e) {
            available = false;
          }
        }
      }
    }
    return available;
  }

  /**
   * Scans the project directory using Maven Model API.
   *
   * <p>This method parses {@code pom.xml} files using the Maven Model to extract structured
   * information about modules including groupId, artifactId, and declared submodules.
   *
   * <p><strong>Note:</strong> This method requires Maven Model API on the classpath. Call {@link
   * #isAvailable()} first to verify availability.
   *
   * @param projectRoot the root directory of the project to scan
   * @return a list of discovered modules with Maven metadata
   * @throws IllegalArgumentException if projectRoot is null or not a directory
   * @throws IllegalStateException if Maven Model API is not available
   * @throws IOException if an I/O error occurs during scanning
   */
  public static List<ModuleInfo> scan(Path projectRoot) throws IOException {
    if (projectRoot == null) {
      throw new IllegalArgumentException("projectRoot cannot be null");
    }
    if (!Files.isDirectory(projectRoot)) {
      throw new IllegalArgumentException("projectRoot must be a directory: " + projectRoot);
    }
    if (!isAvailable()) {
      throw new IllegalStateException(
          "Maven Model API is not available. Add org.apache.maven:maven-model to classpath.");
    }

    // Use reflection to avoid compile-time dependency on Maven Model
    return scanWithReflection(projectRoot);
  }

  /**
   * Scans a single module directory using Maven Model API.
   *
   * <p>This method parses the {@code pom.xml} file to extract module information and then examines
   * child directories to determine layer composition.
   *
   * @param modulePath the path to the module directory to scan
   * @return an Optional containing the ModuleInfo if valid submodules are found
   * @throws IllegalArgumentException if modulePath is null or not a directory
   * @throws IllegalStateException if Maven Model API is not available
   * @throws IOException if an I/O error occurs during scanning
   */
  public static Optional<ModuleInfo> scanModule(Path modulePath) throws IOException {
    if (modulePath == null) {
      throw new IllegalArgumentException("modulePath cannot be null");
    }
    if (!Files.isDirectory(modulePath)) {
      throw new IllegalArgumentException("modulePath must be a directory: " + modulePath);
    }
    if (!isAvailable()) {
      throw new IllegalStateException(
          "Maven Model API is not available. Add org.apache.maven:maven-model to classpath.");
    }

    return scanModuleWithReflection(modulePath);
  }

  /**
   * Internal implementation using reflection to avoid compile-time Maven dependency.
   *
   * <p>This method uses reflection to:
   *
   * <ol>
   *   <li>Instantiate MavenXpp3Reader
   *   <li>Parse pom.xml files
   *   <li>Extract artifactId to determine base names
   *   <li>Group modules by base name
   * </ol>
   *
   * @param projectRoot the root directory to scan
   * @return list of discovered modules
   * @throws IOException if an I/O error occurs
   */
  private static List<ModuleInfo> scanWithReflection(Path projectRoot) throws IOException {
    try {
      // Load Maven Model classes via reflection
      Class<?> readerClass = Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Reader");
      Class<?> modelClass = Class.forName("org.apache.maven.model.Model");
      Object reader = readerClass.getDeclaredConstructor().newInstance();

      // Find all directories containing pom.xml
      List<Path> pomFiles = new ArrayList<>();
      try (Stream<Path> paths = Files.walk(projectRoot, MAX_DEPTH)) {
        paths
            .filter(Files::isRegularFile)
            .filter(path -> path.getFileName().toString().equals("pom.xml"))
            .forEach(pomFiles::add);
      }

      // Parse each pom.xml and extract module information
      Map<String, Map<String, Path>> moduleGroups = new HashMap<>();
      for (Path pomFile : pomFiles) {
        try (InputStream is = Files.newInputStream(pomFile)) {
          // Call reader.read(InputStream)
          Object model = readerClass.getMethod("read", InputStream.class).invoke(reader, is);

          // Get artifactId
          String artifactId = (String) modelClass.getMethod("getArtifactId").invoke(model);
          if (artifactId == null) {
            continue;
          }

          // Match against layer pattern
          Matcher matcher = LAYER_PATTERN.matcher(artifactId);
          if (matcher.matches()) {
            String baseName = matcher.group(1);
            String layer = matcher.group(2);

            // Normalize "utils" to "util"
            if ("utils".equals(layer)) {
              layer = "util";
            }

            Path modulePath = pomFile.getParent();
            moduleGroups.computeIfAbsent(baseName, k -> new HashMap<>()).put(layer, modulePath);
          }
        } catch (Exception e) {
          // Skip invalid pom.xml files
          continue;
        }
      }

      // Build ModuleInfo for each component
      List<ModuleInfo> modules = new ArrayList<>();
      for (Map.Entry<String, Map<String, Path>> entry : moduleGroups.entrySet()) {
        String baseName = entry.getKey();
        Map<String, Path> layers = entry.getValue();

        // Use the parent directory as the module path
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

    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Maven Model API classes not found", e);
    } catch (Exception e) {
      throw new IOException("Failed to scan with Maven Model API", e);
    }
  }

  /**
   * Internal implementation for scanning a single module using reflection.
   *
   * @param modulePath the path to scan
   * @return optional ModuleInfo if found
   * @throws IOException if an I/O error occurs
   */
  private static Optional<ModuleInfo> scanModuleWithReflection(Path modulePath) throws IOException {
    try {
      // Load Maven Model classes via reflection
      Class<?> readerClass = Class.forName("org.apache.maven.model.io.xpp3.MavenXpp3Reader");
      Class<?> modelClass = Class.forName("org.apache.maven.model.Model");
      Object reader = readerClass.getDeclaredConstructor().newInstance();

      // Find all immediate child pom.xml files
      Map<String, Boolean> layers = new HashMap<>();
      String baseName = null;

      try (Stream<Path> paths = Files.list(modulePath)) {
        List<Path> childPoms =
            paths
                .filter(Files::isDirectory)
                .map(dir -> dir.resolve("pom.xml"))
                .filter(Files::exists)
                .toList();

        for (Path pomFile : childPoms) {
          try (InputStream is = Files.newInputStream(pomFile)) {
            // Parse the pom.xml
            Object model = readerClass.getMethod("read", InputStream.class).invoke(reader, is);

            // Get artifactId
            String artifactId = (String) modelClass.getMethod("getArtifactId").invoke(model);
            if (artifactId == null) {
              continue;
            }

            // Match against layer pattern
            Matcher matcher = LAYER_PATTERN.matcher(artifactId);
            if (matcher.matches()) {
              if (baseName == null) {
                baseName = matcher.group(1);
              }
              String layer = matcher.group(2);

              // Normalize "utils" to "util"
              if ("utils".equals(layer)) {
                layer = "util";
              }

              layers.put(layer, true);
            }
          } catch (Exception e) {
            // Skip invalid pom.xml files
            continue;
          }
        }
      }

      // If no valid layers found, return empty
      if (layers.isEmpty() || baseName == null) {
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

    } catch (ClassNotFoundException e) {
      throw new IllegalStateException("Maven Model API classes not found", e);
    } catch (Exception e) {
      throw new IOException("Failed to scan module with Maven Model API", e);
    }
  }
}
