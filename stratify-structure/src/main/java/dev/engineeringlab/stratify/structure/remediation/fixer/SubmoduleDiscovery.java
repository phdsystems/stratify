package dev.engineeringlab.stratify.structure.remediation.fixer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Discovers submodules in a Maven multi-module project.
 *
 * <p>This utility scans the project structure to find all Maven modules, including nested modules
 * in parent aggregators.
 *
 * <p>Module types recognized:
 *
 * <ul>
 *   <li>api - Public interfaces and DTOs
 *   <li>spi - Service Provider Interfaces
 *   <li>core - Implementation classes
 *   <li>facade - Public facade (combines api/core)
 *   <li>common - Shared utilities
 * </ul>
 */
public class SubmoduleDiscovery {

  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<artifactId>([^<]+)</artifactId>");
  private static final Pattern GROUP_ID_PATTERN = Pattern.compile("<groupId>([^<]+)</groupId>");
  private static final Pattern PACKAGING_PATTERN =
      Pattern.compile("<packaging>([^<]+)</packaging>");

  /** Represents a discovered Maven module. */
  public record ModuleInfo(
      String artifactId,
      String groupId,
      Path path,
      ModuleType type,
      String parentModule,
      boolean hasJavaSources) {
    /**
     * Derives the expected package prefix for this module. Formula: basePackage.moduleName.layer
     *
     * @param basePackage the base package (namespace.project)
     * @return the expected package prefix
     */
    public String getExpectedPackagePrefix(String basePackage) {
      String moduleName = extractModuleName();
      if (moduleName == null || moduleName.isEmpty()) {
        return basePackage;
      }

      // For layer modules (api, core, spi, facade), include the layer
      if (type != ModuleType.PARENT && type != ModuleType.UNKNOWN) {
        return basePackage + "." + moduleName + "." + type.suffix();
      }

      return basePackage + "." + moduleName;
    }

    /**
     * Extracts the module name without the layer suffix. e.g., "agent-core" -> "agent",
     * "llm-memory-api" -> "llm.memory"
     */
    public String extractModuleName() {
      if (artifactId == null) {
        return null;
      }
      String name = artifactId;
      // Remove layer suffix
      for (ModuleType t : ModuleType.values()) {
        if (t.suffix() != null && name.endsWith("-" + t.suffix())) {
          name = name.substring(0, name.length() - t.suffix().length() - 1);
          break;
        }
      }
      // Convert hyphens to dots for package names
      return name.replace("-", ".");
    }
  }

  /** Module types based on layered architecture. */
  public enum ModuleType {
    API("api"),
    SPI("spi"),
    CORE("core"),
    FACADE("facade"),
    COMMON("common"),
    PARENT(null),
    UNKNOWN(null);

    private final String suffix;

    ModuleType(String suffix) {
      this.suffix = suffix;
    }

    public String suffix() {
      return suffix;
    }

    public static ModuleType fromArtifactId(String artifactId) {
      if (artifactId == null) {
        return UNKNOWN;
      }
      if (artifactId.endsWith("-api")) return API;
      if (artifactId.endsWith("-spi")) return SPI;
      if (artifactId.endsWith("-core")) return CORE;
      if (artifactId.endsWith("-facade")) return FACADE;
      if (artifactId.endsWith("-common") || artifactId.contains("common")) return COMMON;
      if (artifactId.endsWith("-parent")) return PARENT;
      return UNKNOWN;
    }
  }

  /**
   * Discovers all modules in the project starting from the project root.
   *
   * @param projectRoot the root directory of the multi-module project
   * @return list of discovered modules
   */
  public List<ModuleInfo> discoverModules(Path projectRoot) {
    List<ModuleInfo> modules = new ArrayList<>();
    discoverModulesRecursive(projectRoot, null, modules);
    return modules;
  }

  /**
   * Discovers only leaf modules (non-parent modules with Java sources).
   *
   * @param projectRoot the root directory of the multi-module project
   * @return list of leaf modules
   */
  public List<ModuleInfo> discoverLeafModules(Path projectRoot) {
    return discoverModules(projectRoot).stream()
        .filter(m -> m.type() != ModuleType.PARENT)
        .filter(ModuleInfo::hasJavaSources)
        .toList();
  }

  /** Recursively discovers modules in a directory. */
  private void discoverModulesRecursive(
      Path directory, String parentModule, List<ModuleInfo> modules) {
    Path pomFile = directory.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return;
    }

    try {
      String pomContent = Files.readString(pomFile);

      String artifactId = extractFirst(pomContent, ARTIFACT_ID_PATTERN);
      String groupId = extractFirst(pomContent, GROUP_ID_PATTERN);
      String packaging = extractFirst(pomContent, PACKAGING_PATTERN);

      // Determine if this is a parent/aggregator module
      boolean isPom = "pom".equals(packaging);
      ModuleType type = isPom ? ModuleType.PARENT : ModuleType.fromArtifactId(artifactId);

      // Check for Java sources
      boolean hasJavaSources = hasJavaSources(directory);

      ModuleInfo moduleInfo =
          new ModuleInfo(artifactId, groupId, directory, type, parentModule, hasJavaSources);
      modules.add(moduleInfo);

      // If this is a parent module, discover child modules
      if (isPom) {
        List<String> childModules = extractModules(pomContent);
        for (String childModule : childModules) {
          Path childPath = directory.resolve(childModule);
          if (Files.isDirectory(childPath)) {
            discoverModulesRecursive(childPath, artifactId, modules);
          }
        }
      }

    } catch (IOException e) {
      // Skip modules that can't be read
    }
  }

  /** Extracts module references from a POM file. */
  private List<String> extractModules(String pomContent) {
    List<String> modules = new ArrayList<>();
    Matcher matcher = MODULE_PATTERN.matcher(pomContent);
    while (matcher.find()) {
      modules.add(matcher.group(1).trim());
    }
    return modules;
  }

  /** Extracts the first match of a pattern. */
  private String extractFirst(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Checks if a module has Java source files. */
  private boolean hasJavaSources(Path modulePath) {
    Path srcMainJava = modulePath.resolve("src/main/java");
    if (!Files.isDirectory(srcMainJava)) {
      return false;
    }
    try (Stream<Path> paths = Files.walk(srcMainJava, 10)) {
      return paths.anyMatch(p -> p.toString().endsWith(".java"));
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Finds a module by its artifact ID.
   *
   * @param projectRoot the project root
   * @param artifactId the artifact ID to find
   * @return the module info if found
   */
  public Optional<ModuleInfo> findModule(Path projectRoot, String artifactId) {
    return discoverModules(projectRoot).stream()
        .filter(m -> artifactId.equals(m.artifactId()))
        .findFirst();
  }

  /**
   * Finds all modules of a specific type.
   *
   * @param projectRoot the project root
   * @param type the module type to find
   * @return list of matching modules
   */
  public List<ModuleInfo> findModulesByType(Path projectRoot, ModuleType type) {
    return discoverModules(projectRoot).stream().filter(m -> m.type() == type).toList();
  }
}
