package dev.engineeringlab.stratify.structure.remediation.fixer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads project configuration from YAML files.
 *
 * <p>This utility reads the {@code application.yaml} or {@code compliance-config.yaml} to extract
 * namespace and project settings for package refactoring.
 *
 * <p>Expected YAML structure:
 *
 * <pre>
 * naming:
 *   namespace: dev.engineeringlab
 *   project: architecture
 * </pre>
 *
 * <p>Or legacy format:
 *
 * <pre>
 * compliance:
 *   naming:
 *     namespace: dev.engineeringlab
 *     project: architecture
 * </pre>
 */
public class ProjectConfigReader {

  private static final String[] CONFIG_FILE_NAMES = {
    "application.yaml",
    "application.yml",
    "compliance-config.yaml",
    "compliance-config.yml",
    ".sea/config.yaml",
    ".sea/config.yml"
  };

  private static final Pattern NAMESPACE_PATTERN =
      Pattern.compile("^\\s*namespace:\\s*([\\w.]+)\\s*$", Pattern.MULTILINE);
  private static final Pattern PROJECT_PATTERN =
      Pattern.compile("^\\s*project:\\s*([\\w.-]+)\\s*$", Pattern.MULTILINE);

  /** Configuration holder for namespace and project settings. */
  public record ProjectConfig(String namespace, String project, Path configFile) {
    /**
     * Returns the base package derived from namespace and project. Formula: namespace.project
     * (e.g., dev.engineeringlab.architecture)
     */
    public String getBasePackage() {
      if (project == null || project.isEmpty()) {
        return namespace;
      }
      return namespace + "." + project;
    }

    /** Creates a default configuration. */
    public static ProjectConfig defaults() {
      return new ProjectConfig("dev.engineeringlab", "architecture", null);
    }
  }

  /**
   * Reads project configuration from the project root directory.
   *
   * <p>Searches for configuration files in order of preference and extracts namespace and project
   * settings.
   *
   * @param projectRoot the root directory of the project
   * @return the project configuration, or defaults if not found
   */
  public ProjectConfig readConfig(Path projectRoot) {
    // Try to find and read config file
    for (String configFileName : CONFIG_FILE_NAMES) {
      Path configFile = projectRoot.resolve(configFileName);
      if (Files.exists(configFile)) {
        Optional<ProjectConfig> config = parseConfigFile(configFile);
        if (config.isPresent()) {
          return config.get();
        }
      }

      // Also check in src/main/resources
      Path resourcesConfig = projectRoot.resolve("src/main/resources").resolve(configFileName);
      if (Files.exists(resourcesConfig)) {
        Optional<ProjectConfig> config = parseConfigFile(resourcesConfig);
        if (config.isPresent()) {
          return config.get();
        }
      }
    }

    // Return defaults if no config found
    return ProjectConfig.defaults();
  }

  /**
   * Reads project configuration, searching from a module path up to the project root.
   *
   * @param modulePath the path to start searching from
   * @return the project configuration
   */
  public ProjectConfig readConfigFromModule(Path modulePath) {
    Path current = modulePath.toAbsolutePath().normalize();

    // Walk up directory tree looking for config
    while (current != null) {
      ProjectConfig config = readConfig(current);
      if (config.configFile() != null) {
        return config;
      }

      // Check if we've reached a project root (has pom.xml and no parent pom reference)
      Path pomFile = current.resolve("pom.xml");
      if (Files.exists(pomFile) && isRootPom(pomFile)) {
        break;
      }

      current = current.getParent();
    }

    return ProjectConfig.defaults();
  }

  /** Parses a YAML configuration file to extract namespace and project. */
  private Optional<ProjectConfig> parseConfigFile(Path configFile) {
    try {
      String content = Files.readString(configFile);

      String namespace = extractValue(content, NAMESPACE_PATTERN);
      String project = extractValue(content, PROJECT_PATTERN);

      if (namespace != null) {
        return Optional.of(
            new ProjectConfig(namespace, project != null ? project : "", configFile));
      }
    } catch (IOException e) {
      // Ignore and try next file
    }
    return Optional.empty();
  }

  /** Extracts a value using a regex pattern. */
  private String extractValue(String content, Pattern pattern) {
    Matcher matcher = pattern.matcher(content);
    if (matcher.find()) {
      return matcher.group(1).trim();
    }
    return null;
  }

  /** Checks if a POM file is a root POM (no parent or parent is external). */
  private boolean isRootPom(Path pomFile) {
    try {
      String content = Files.readString(pomFile);
      // Simple heuristic: check if parent relativePath points outside
      if (content.contains("<relativePath/>")
          || content.contains("<relativePath></relativePath>")) {
        return true;
      }
      // Check for mvnw in same directory (indicates project root)
      return Files.exists(pomFile.getParent().resolve("mvnw"));
    } catch (IOException e) {
      return false;
    }
  }
}
