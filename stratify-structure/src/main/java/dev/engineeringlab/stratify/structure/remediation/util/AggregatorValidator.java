package dev.engineeringlab.stratify.structure.remediation.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates and resolves aggregator module boundaries for structure fixers.
 *
 * <p>This class enforces that cross-module fixers (like {@code PackageMatchesArtifactFixer}) can
 * only operate within the boundary of an aggregator or parent module. This prevents fixers from
 * accidentally modifying files outside their intended scope.
 *
 * <h2>Key Concepts</h2>
 *
 * <ul>
 *   <li><b>Aggregator Module</b>: A POM with {@code <packaging>pom</packaging>} and artifactId
 *       ending with {@code -aggregator}
 *   <li><b>Parent Module</b>: A POM with {@code <packaging>pom</packaging>} and artifactId ending
 *       with {@code -parent}
 *   <li><b>Aggregator Boundary</b>: The scope defined by the aggregator's {@code <modules>} section
 *       - fixers should only operate within these modules
 * </ul>
 *
 * <h2>Usage</h2>
 *
 * <pre>{@code
 * AggregatorValidator validator = new AggregatorValidator();
 *
 * // Check if current module is an aggregator
 * if (validator.isAggregatorModule(moduleRoot)) {
 *     // Safe to run cross-module fixers
 *     List<Path> modules = validator.getAggregatorModules(moduleRoot);
 * } else {
 *     // Not an aggregator - reject cross-module operations
 *     return FixResult.failed(violation,
 *         "Cross-module fixers must run from an -aggregator or -parent module");
 * }
 * }</pre>
 *
 * @see PackageMatchesArtifactFixer
 */
public class AggregatorValidator {

  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<artifactId>([^<]+)</artifactId>");
  private static final Pattern PACKAGING_PATTERN =
      Pattern.compile("<packaging>([^<]+)</packaging>");
  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");
  private static final Pattern PARENT_SECTION_PATTERN =
      Pattern.compile("<parent>.*?</parent>", Pattern.DOTALL);

  /**
   * Result of aggregator validation.
   *
   * @param valid true if the module is a valid aggregator/parent
   * @param artifactId the module's artifactId
   * @param modules list of child module paths (empty if not an aggregator)
   * @param errorMessage error message if validation failed
   */
  public record ValidationResult(
      boolean valid, String artifactId, List<Path> modules, String errorMessage) {
    /** Creates a successful validation result. */
    public static ValidationResult success(String artifactId, List<Path> modules) {
      return new ValidationResult(true, artifactId, modules, null);
    }

    /** Creates a failed validation result. */
    public static ValidationResult failure(String errorMessage) {
      return new ValidationResult(false, null, List.of(), errorMessage);
    }

    /** Creates a failed validation result with artifactId context. */
    public static ValidationResult failure(String artifactId, String errorMessage) {
      return new ValidationResult(false, artifactId, List.of(), errorMessage);
    }
  }

  /**
   * Validates that the given module is an aggregator or parent module.
   *
   * <p>An aggregator module must:
   *
   * <ol>
   *   <li>Have a pom.xml file
   *   <li>Have {@code <packaging>pom</packaging>}
   *   <li>Have an artifactId ending with {@code -aggregator} or {@code -parent}
   *   <li>Have a {@code <modules>} section with at least one module
   * </ol>
   *
   * @param moduleRoot the module root directory to validate
   * @return validation result with details
   */
  public ValidationResult validateAggregator(Path moduleRoot) {
    Path pomFile = moduleRoot.resolve("pom.xml");

    if (!Files.exists(pomFile)) {
      return ValidationResult.failure("No pom.xml found at " + moduleRoot);
    }

    try {
      String pomContent = Files.readString(pomFile);

      // Extract artifactId (excluding parent section)
      String artifactId = extractOwnArtifactId(pomContent);
      if (artifactId == null) {
        return ValidationResult.failure("Could not extract artifactId from pom.xml");
      }

      // Check packaging
      String packaging = extractPackaging(pomContent);
      if (!"pom".equals(packaging)) {
        return ValidationResult.failure(
            artifactId,
            "Module '"
                + artifactId
                + "' has packaging '"
                + packaging
                + "', expected 'pom' for aggregator/parent modules");
      }

      // Check artifactId suffix
      if (!isAggregatorArtifactId(artifactId)) {
        return ValidationResult.failure(
            artifactId,
            "Module '"
                + artifactId
                + "' is not an aggregator or parent module. "
                + "Cross-module fixers must run from a module with artifactId ending in "
                + "'-aggregator' or '-parent'. Current module: "
                + artifactId);
      }

      // Extract modules
      List<Path> modules = extractModules(pomContent, moduleRoot);
      if (modules.isEmpty()) {
        return ValidationResult.failure(
            artifactId, "Aggregator '" + artifactId + "' has no <modules> defined");
      }

      return ValidationResult.success(artifactId, modules);

    } catch (IOException e) {
      return ValidationResult.failure("Failed to read pom.xml: " + e.getMessage());
    }
  }

  /**
   * Checks if the given module is an aggregator or parent module.
   *
   * <p>This is a quick check that only validates the artifactId suffix, without fully parsing the
   * POM.
   *
   * @param moduleRoot the module root directory
   * @return true if the module appears to be an aggregator or parent
   */
  public boolean isAggregatorModule(Path moduleRoot) {
    Path pomFile = moduleRoot.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return false;
    }

    try {
      String pomContent = Files.readString(pomFile);
      String artifactId = extractOwnArtifactId(pomContent);
      String packaging = extractPackaging(pomContent);

      return "pom".equals(packaging) && isAggregatorArtifactId(artifactId);
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Gets the list of child modules declared in an aggregator's pom.xml.
   *
   * @param aggregatorRoot the aggregator module root directory
   * @return list of absolute paths to child modules, or empty list if not an aggregator
   */
  public List<Path> getAggregatorModules(Path aggregatorRoot) {
    Path pomFile = aggregatorRoot.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return List.of();
    }

    try {
      String pomContent = Files.readString(pomFile);
      return extractModules(pomContent, aggregatorRoot);
    } catch (IOException e) {
      return List.of();
    }
  }

  /**
   * Finds the nearest aggregator ancestor for a given module.
   *
   * <p>Walks up the directory tree looking for a pom.xml with an aggregator artifactId. Stops at
   * the filesystem root or after maxLevels.
   *
   * @param moduleRoot the starting module directory
   * @param maxLevels maximum number of parent directories to check
   * @return the aggregator root path, or empty if not found
   */
  public Optional<Path> findAggregatorAncestor(Path moduleRoot, int maxLevels) {
    Path current = moduleRoot.toAbsolutePath().normalize();

    for (int i = 0; i < maxLevels && current != null; i++) {
      if (isAggregatorModule(current)) {
        return Optional.of(current);
      }
      current = current.getParent();
    }

    return Optional.empty();
  }

  /**
   * Finds the nearest aggregator ancestor, checking up to 5 levels.
   *
   * @param moduleRoot the starting module directory
   * @return the aggregator root path, or empty if not found
   */
  public Optional<Path> findAggregatorAncestor(Path moduleRoot) {
    return findAggregatorAncestor(moduleRoot, 5);
  }

  /**
   * Checks if a path is within the boundary of an aggregator's modules.
   *
   * @param path the path to check
   * @param aggregatorRoot the aggregator root directory
   * @return true if the path is within one of the aggregator's modules
   */
  public boolean isWithinAggregatorBoundary(Path path, Path aggregatorRoot) {
    List<Path> modules = getAggregatorModules(aggregatorRoot);
    Path normalizedPath = path.toAbsolutePath().normalize();

    for (Path module : modules) {
      Path normalizedModule = module.toAbsolutePath().normalize();
      if (normalizedPath.startsWith(normalizedModule)) {
        return true;
      }
    }

    // Also check if path is directly in aggregator root (e.g., aggregator's own pom.xml)
    return normalizedPath.startsWith(aggregatorRoot.toAbsolutePath().normalize());
  }

  /** Extracts the module's own artifactId (not the parent's). */
  private String extractOwnArtifactId(String pomContent) {
    // Remove parent section first to avoid matching parent's artifactId
    String withoutParent = PARENT_SECTION_PATTERN.matcher(pomContent).replaceFirst("");

    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(withoutParent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Extracts the packaging type from POM content. */
  private String extractPackaging(String pomContent) {
    Matcher matcher = PACKAGING_PATTERN.matcher(pomContent);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    // Default packaging is 'jar' if not specified
    return "jar";
  }

  /** Extracts module paths from POM content. */
  private List<Path> extractModules(String pomContent, Path aggregatorRoot) {
    List<Path> modules = new ArrayList<>();
    Matcher matcher = MODULE_PATTERN.matcher(pomContent);

    while (matcher.find()) {
      String moduleName = matcher.group(1).trim();
      Path modulePath = aggregatorRoot.resolve(moduleName).toAbsolutePath().normalize();
      modules.add(modulePath);
    }

    return Collections.unmodifiableList(modules);
  }

  /** Checks if an artifactId indicates an aggregator or parent module. */
  private boolean isAggregatorArtifactId(String artifactId) {
    if (artifactId == null) {
      return false;
    }
    return artifactId.endsWith("-aggregator") || artifactId.endsWith("-parent");
  }
}
