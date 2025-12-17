package dev.engineeringlab.stratify.structure.scanner.common.util;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.PluginInfo;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/** Scans and parses Maven POM files. */
public class PomScanner {

  /** Maven POM reader for parsing pom.xml files. */
  private final MavenXpp3Reader pomReader = new MavenXpp3Reader();

  /**
   * Reads and parses a POM file.
   *
   * @param pomPath Path to pom.xml
   * @return Parsed Maven Model, or null if file doesn't exist
   */
  public Model readPom(final Path pomPath) {
    File pomFile = pomPath.toFile();
    if (!pomFile.exists()) {
      return null;
    }

    try (FileInputStream fis = new FileInputStream(pomFile)) {
      Model model = pomReader.read(fis);
      // Set the POM file reference so parent paths can be resolved
      model.setPomFile(pomFile);
      return model;
    } catch (IOException | XmlPullParserException e) {
      throw new RuntimeException("Failed to parse POM: " + pomPath, e);
    }
  }

  /**
   * Gets the artifactId from a POM file.
   *
   * @param pom the Maven POM model
   * @return the artifact ID, or null if POM is null
   */
  public String getArtifactId(final Model pom) {
    return pom != null ? pom.getArtifactId() : null;
  }

  /**
   * Gets the groupId from a POM file (including inherited from parent).
   *
   * @param pom the Maven POM model
   * @return the group ID, or null if not found
   */
  public String getGroupId(final Model pom) {
    if (pom == null) {
      return null;
    }
    String groupId = pom.getGroupId();
    if (groupId == null && pom.getParent() != null) {
      groupId = pom.getParent().getGroupId();
    }
    return groupId;
  }

  /**
   * Gets the packaging type from a POM file.
   *
   * @param pom the Maven POM model
   * @return the packaging type (defaults to "jar")
   */
  public String getPackaging(final Model pom) {
    if (pom == null) {
      return "jar"; // Maven default
    }
    String packaging = pom.getPackaging();
    return packaging != null ? packaging : "jar";
  }

  /**
   * Checks if this is a parent/aggregator POM.
   *
   * @param pom the Maven POM model
   * @return true if packaging is "pom"
   */
  public boolean isParentPom(final Model pom) {
    return "pom".equals(getPackaging(pom));
  }

  /**
   * Gets list of dependency artifactIds from a POM.
   *
   * @param pom the Maven POM model
   * @return list of dependency artifact IDs
   */
  public List<String> getDependencyArtifactIds(final Model pom) {
    if (pom == null || pom.getDependencies() == null) {
      return Collections.emptyList();
    }

    return pom.getDependencies().stream()
        .map(Dependency::getArtifactId)
        .collect(Collectors.toList());
  }

  /**
   * Checks if POM has a specific dependency.
   *
   * @param pom the Maven POM model
   * @param artifactId the dependency artifact ID to check
   * @return true if dependency exists
   */
  public boolean hasDependency(final Model pom, final String artifactId) {
    return getDependencyArtifactIds(pom).contains(artifactId);
  }

  /**
   * Checks if POM has a dependency with specific groupId and artifactId.
   *
   * @param pom the Maven POM model
   * @param groupId the dependency group ID
   * @param artifactId the dependency artifact ID
   * @return true if dependency exists
   */
  public boolean hasDependency(final Model pom, final String groupId, final String artifactId) {
    if (pom == null || pom.getDependencies() == null) {
      return false;
    }

    return pom.getDependencies().stream()
        .anyMatch(d -> groupId.equals(d.getGroupId()) && artifactId.equals(d.getArtifactId()));
  }

  /**
   * Gets all module names from parent POM.
   *
   * @param pom the Maven POM model
   * @return list of module names
   */
  public List<String> getModules(final Model pom) {
    if (pom == null || pom.getModules() == null) {
      return Collections.emptyList();
    }
    return pom.getModules();
  }

  /**
   * Scans for all plugins in a POM file.
   *
   * @param pom the Maven POM model
   * @return list of all plugins found
   */
  public List<PluginInfo> scanAllPlugins(final Model pom) {
    if (pom == null) {
      return Collections.emptyList();
    }

    List<PluginInfo> plugins = new ArrayList<>();

    // Scan build plugins
    if (pom.getBuild() != null && pom.getBuild().getPlugins() != null) {
      for (Plugin plugin : pom.getBuild().getPlugins()) {
        plugins.add(convertToPluginInfo(plugin));
      }
    }

    // Scan pluginManagement plugins
    if (pom.getBuild() != null
        && pom.getBuild().getPluginManagement() != null
        && pom.getBuild().getPluginManagement().getPlugins() != null) {
      for (Plugin plugin : pom.getBuild().getPluginManagement().getPlugins()) {
        if (!containsPlugin(plugins, plugin)) {
          plugins.add(convertToPluginInfo(plugin));
        }
      }
    }

    return plugins;
  }

  /**
   * Scans for code quality plugins in a POM file.
   *
   * @param pom the Maven POM model
   * @return list of code quality plugins
   */
  public List<PluginInfo> scanCodeQualityPlugins(final Model pom) {
    List<PluginInfo> allPlugins = scanAllPlugins(pom);
    return allPlugins.stream().filter(PluginInfo::isCodeQualityPlugin).collect(Collectors.toList());
  }

  private PluginInfo convertToPluginInfo(final Plugin plugin) {
    String groupId = plugin.getGroupId() != null ? plugin.getGroupId() : "org.apache.maven.plugins";
    String artifactId = plugin.getArtifactId();
    String version = plugin.getVersion();

    PluginInfo.PluginInfoBuilder builder =
        PluginInfo.builder().groupId(groupId).artifactId(artifactId).version(version);

    String category = categorizePlugin(groupId, artifactId);
    if (category != null) {
      builder.isCodeQualityPlugin(true);
      builder.category(category);
    }

    return builder.build();
  }

  private String categorizePlugin(final String groupId, final String artifactId) {
    if (artifactId.contains("checkstyle")) {
      return "Static Analysis";
    }
    if (artifactId.contains("pmd")) {
      return "Static Analysis";
    }
    if (artifactId.contains("spotbugs") || artifactId.contains("findbugs")) {
      return "Static Analysis";
    }
    if (artifactId.contains("jacoco")) {
      return "Code Coverage";
    }
    if (artifactId.contains("enforcer")) {
      return "Policy Enforcement";
    }
    if (groupId != null && groupId.contains("archunit")) {
      return "Architecture Testing";
    }
    if (artifactId.contains("sonar")) {
      return "Static Analysis";
    }
    if (artifactId.contains("error-prone")) {
      return "Static Analysis";
    }
    if (artifactId.contains("modernizer")) {
      return "Code Modernization";
    }
    if (artifactId.contains("duplicate-finder")) {
      return "Code Quality";
    }
    if (artifactId.contains("dependency-check")) {
      return "Security";
    }
    if (groupId != null && groupId.contains("owasp")) {
      return "Security";
    }

    return null;
  }

  private boolean containsPlugin(final List<PluginInfo> plugins, final Plugin plugin) {
    String groupId = plugin.getGroupId() != null ? plugin.getGroupId() : "org.apache.maven.plugins";
    String artifactId = plugin.getArtifactId();

    return plugins.stream()
        .anyMatch(p -> groupId.equals(p.getGroupId()) && artifactId.equals(p.getArtifactId()));
  }

  /**
   * Finds dependencies that have hardcoded versions.
   *
   * @param pom the Maven POM model
   * @param commonDependencies list of common dependency artifact IDs to check
   * @return list of artifact IDs that have explicit versions
   */
  public List<String> findDependenciesWithVersions(
      final Model pom, final List<String> commonDependencies) {
    if (pom == null || pom.getDependencies() == null) {
      return Collections.emptyList();
    }

    return pom.getDependencies().stream()
        .filter(
            dep ->
                commonDependencies.stream()
                    .anyMatch(common -> dep.getArtifactId().contains(common)))
        .filter(dep -> dep.getVersion() != null && !dep.getVersion().isEmpty())
        .map(Dependency::getArtifactId)
        .collect(Collectors.toList());
  }

  /**
   * Gets a map of dependency versions for common dependencies.
   *
   * @param pom the Maven POM model
   * @param commonDependencies list of common dependency artifact IDs to check
   * @return map of artifactId to version for dependencies with explicit versions
   */
  public Map<String, String> getDependencyVersionMap(
      final Model pom, final List<String> commonDependencies) {
    if (pom == null || pom.getDependencies() == null) {
      return Collections.emptyMap();
    }

    return pom.getDependencies().stream()
        .filter(
            dep ->
                commonDependencies.stream()
                    .anyMatch(common -> dep.getArtifactId().contains(common)))
        .filter(dep -> dep.getVersion() != null && !dep.getVersion().isEmpty())
        .collect(
            Collectors.toMap(Dependency::getArtifactId, Dependency::getVersion, (v1, v2) -> v1));
  }

  /**
   * Finds cross-module dependencies.
   *
   * @param pom the Maven POM model
   * @param currentModuleName the current module name
   * @param siblings list of sibling submodules
   * @return list of external module dependencies
   */
  public List<String> findCrossModuleDependencies(
      final Model pom,
      final String currentModuleName,
      final List<ModuleInfo.SubModuleInfo> siblings) {
    if (pom == null || pom.getDependencies() == null) {
      return Collections.emptyList();
    }

    List<String> siblingArtifactIds =
        siblings.stream()
            .filter(s -> s.isExists() && s.getArtifactId() != null)
            .map(ModuleInfo.SubModuleInfo::getArtifactId)
            .collect(Collectors.toList());

    return pom.getDependencies().stream()
        .filter(
            dep -> {
              String artifactId = dep.getArtifactId();
              String groupId = dep.getGroupId();
              if (groupId == null || !groupId.equals("dev.engineeringlab")) {
                return false;
              }
              return (artifactId.endsWith("-api")
                  || artifactId.endsWith("-spi")
                  || artifactId.endsWith("-core")
                  || artifactId.endsWith("-facade"));
            })
        .filter(
            dep -> {
              String artifactId = dep.getArtifactId();
              return !siblingArtifactIds.contains(artifactId);
            })
        .map(Dependency::getArtifactId)
        .collect(Collectors.toList());
  }
}
