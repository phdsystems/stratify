package dev.engineeringlab.stratify.structure.remediation.api;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Execution context for structure fixers.
 *
 * <p>Provides all necessary information for fixers to execute, including project paths,
 * configuration, and logging facilities.
 *
 * @param projectRoot the root directory of the project
 * @param moduleRoot the root directory of the current module
 * @param dryRun if true, don't apply changes, just show what would be done
 * @param javaVersion the Java version for parsing (e.g., "21")
 * @param classpath the classpath for compilation validation
 * @param config fixer configuration
 * @param logger consumer for log messages
 */
public record FixerContext(
    Path projectRoot,
    Path moduleRoot,
    boolean dryRun,
    String javaVersion,
    List<String> classpath,
    FixerConfig config,
    Consumer<String> logger) {

  /** Creates a builder for constructing FixerContext instances. */
  public static Builder builder() {
    return new Builder();
  }

  /** Logs a message using the configured logger. */
  public void log(String message) {
    if (logger != null) {
      logger.accept(message);
    }
  }

  /** Logs a formatted message using the configured logger. */
  public void log(String format, Object... args) {
    if (logger != null) {
      logger.accept(String.format(format, args));
    }
  }

  /** Returns the namespace from configuration. */
  public String getNamespace() {
    return config != null ? config.getNamespace() : "dev.engineeringlab";
  }

  /** Returns the project name from configuration. */
  public String getProject() {
    return config != null ? config.getProject() : "stratify";
  }

  /** Returns true if a fixer is enabled in configuration. */
  public boolean isFixerEnabled(String fixerName) {
    return true;
  }

  /** Returns the remediation report directory from configuration. */
  public String getRemediationReportDir() {
    return config != null ? config.getRemediationReportDir() : ".remediation/reports";
  }

  /** Returns the Java language level as an integer. */
  public int getJavaVersionInt() {
    try {
      return Integer.parseInt(javaVersion);
    } catch (NumberFormatException e) {
      return 21; // Default to Java 21
    }
  }

  /** Builder for FixerContext. */
  public static class Builder {
    private Path projectRoot;
    private Path moduleRoot;
    private boolean dryRun = false;
    private String javaVersion = "21";
    private List<String> classpath = List.of();
    private FixerConfig config = new FixerConfig();
    private Consumer<String> logger = System.out::println;

    public Builder projectRoot(Path projectRoot) {
      this.projectRoot = projectRoot;
      return this;
    }

    public Builder moduleRoot(Path moduleRoot) {
      this.moduleRoot = moduleRoot;
      return this;
    }

    public Builder dryRun(boolean dryRun) {
      this.dryRun = dryRun;
      return this;
    }

    public Builder javaVersion(String javaVersion) {
      this.javaVersion = javaVersion;
      return this;
    }

    public Builder classpath(List<String> classpath) {
      this.classpath = classpath;
      return this;
    }

    public Builder config(FixerConfig config) {
      this.config = config;
      return this;
    }

    public Builder logger(Consumer<String> logger) {
      this.logger = logger;
      return this;
    }

    public FixerContext build() {
      return new FixerContext(
          projectRoot, moduleRoot, dryRun, javaVersion, classpath, config, logger);
    }
  }
}
