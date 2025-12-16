package dev.engineeringlab.stratify.plugin.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Scans filesystem for SEA module structure. Detects -common, -spi, -api, -core, -facade modules
 * and returns ModuleInfo.
 */
public class ModuleScanner {
  private static final Logger log = LoggerFactory.getLogger(ModuleScanner.class);

  private static final String[] MODULE_SUFFIXES = {"-common", "-spi", "-api", "-core", "-facade"};
  private static final String POM_XML = "pom.xml";
  private static final String BUILD_GRADLE = "build.gradle";

  private final BuildFileScanner buildFileScanner;

  public ModuleScanner() {
    this.buildFileScanner = new BuildFileScanner();
  }

  public ModuleScanner(BuildFileScanner buildFileScanner) {
    this.buildFileScanner = buildFileScanner;
  }

  /**
   * Scans the given directory for SEA modules.
   *
   * @param projectRoot the root directory to scan
   * @return list of detected modules
   * @throws IOException if directory cannot be read
   */
  public List<ModuleInfo> scanModules(Path projectRoot) throws IOException {
    if (!Files.isDirectory(projectRoot)) {
      throw new IllegalArgumentException("Project root must be a directory: " + projectRoot);
    }

    log.info("Scanning for modules in: {}", projectRoot);
    List<ModuleInfo> modules = new ArrayList<>();

    // First, check if project root itself is a module
    if (isModuleDirectory(projectRoot)) {
      ModuleInfo moduleInfo = createModuleInfo(projectRoot);
      if (moduleInfo != null) {
        modules.add(moduleInfo);
      }
    }

    // Then scan subdirectories
    try (Stream<Path> paths = Files.list(projectRoot)) {
      List<Path> directories =
          paths
              .filter(Files::isDirectory)
              .filter(p -> !p.getFileName().toString().startsWith("."))
              .collect(Collectors.toList());

      for (Path dir : directories) {
        if (isModuleDirectory(dir)) {
          ModuleInfo moduleInfo = createModuleInfo(dir);
          if (moduleInfo != null) {
            modules.add(moduleInfo);
          }
        }
      }
    }

    log.info("Found {} modules", modules.size());
    return modules;
  }

  /** Determines if a directory contains a module. */
  private boolean isModuleDirectory(Path dir) {
    return Files.exists(dir.resolve(POM_XML)) || Files.exists(dir.resolve(BUILD_GRADLE));
  }

  /** Creates ModuleInfo from a module directory. */
  private ModuleInfo createModuleInfo(Path moduleDir) {
    try {
      Path pomFile = moduleDir.resolve(POM_XML);
      Path gradleFile = moduleDir.resolve(BUILD_GRADLE);

      String artifactId;
      String groupId;
      List<String> dependencies;
      String parentArtifactId = null;

      if (Files.exists(pomFile)) {
        var buildInfo = buildFileScanner.parsePom(pomFile);
        artifactId = buildInfo.artifactId();
        groupId = buildInfo.groupId();
        dependencies = buildInfo.dependencies();
        parentArtifactId = buildInfo.parentArtifactId();
      } else if (Files.exists(gradleFile)) {
        var buildInfo = buildFileScanner.parseGradle(gradleFile);
        artifactId = buildInfo.artifactId();
        groupId = buildInfo.groupId();
        dependencies = buildInfo.dependencies();
      } else {
        log.warn("No build file found in: {}", moduleDir);
        return null;
      }

      ModuleLayer layer = detectLayer(artifactId);

      return new ModuleInfo(artifactId, groupId, layer, moduleDir, dependencies, parentArtifactId);

    } catch (Exception e) {
      log.error("Failed to scan module at {}: {}", moduleDir, e.getMessage());
      return null;
    }
  }

  /** Detects the SEA layer based on artifact ID suffix. */
  private ModuleLayer detectLayer(String artifactId) {
    if (artifactId.endsWith("-api")) {
      return ModuleLayer.API;
    } else if (artifactId.endsWith("-core")) {
      return ModuleLayer.CORE;
    } else if (artifactId.endsWith("-spi")) {
      return ModuleLayer.SPI;
    } else if (artifactId.endsWith("-common")) {
      return ModuleLayer.COMMON;
    } else if (artifactId.endsWith("-facade")) {
      return ModuleLayer.FACADE;
    } else {
      // Assume facade if no recognized suffix
      return ModuleLayer.FACADE;
    }
  }

  /** Represents an SEA module layer. */
  public enum ModuleLayer {
    API,
    CORE,
    SPI,
    COMMON,
    FACADE
  }

  /** Information about a discovered module. */
  public record ModuleInfo(
      String artifactId,
      String groupId,
      ModuleLayer layer,
      Path path,
      List<String> dependencies,
      String parentArtifactId) {
    public ModuleInfo {
      dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
    }

    public String getFullName() {
      return groupId + ":" + artifactId;
    }

    public boolean hasDependency(String dependency) {
      return dependencies.contains(dependency);
    }
  }
}
