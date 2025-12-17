package dev.engineeringlab.stratify.structure.remediation.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * Configuration for Structure Auto-Fixer.
 *
 * @param enabled whether auto-fixing is enabled
 * @param dryRun if true, show changes without applying
 * @param javaVersion Java version for parsing (e.g., "21")
 * @param backupFiles create backup files before modification
 * @param enabledFixers set of enabled fixer names (empty = all enabled)
 * @param disabledFixers set of disabled fixer names
 * @param disabledRules set of disabled rule IDs
 * @param outputPath path for fix reports
 * @param classpath classpath for compilation validation
 */
public record StructureFixerConfig(
    boolean enabled,
    boolean dryRun,
    String javaVersion,
    boolean backupFiles,
    Set<String> enabledFixers,
    Set<String> disabledFixers,
    Set<String> disabledRules,
    Path outputPath,
    List<String> classpath) {

  /** Default configuration with sensible defaults. */
  public static StructureFixerConfig defaults() {
    return new StructureFixerConfig(
        true, // enabled
        false, // dryRun
        "21", // javaVersion
        true, // backupFiles
        Set.of(), // enabledFixers (empty = all)
        Set.of(), // disabledFixers
        Set.of(), // disabledRules
        Path.of("target/structure-fix-report"),
        List.of() // classpath
        );
  }

  /** Creates a builder pre-populated with defaults. */
  public static Builder builder() {
    return new Builder();
  }

  /** Returns true if the given fixer is enabled. */
  public boolean isFixerEnabled(String fixerName) {
    if (disabledFixers.contains(fixerName)) {
      return false;
    }
    if (enabledFixers.isEmpty()) {
      return true;
    }
    return enabledFixers.contains(fixerName);
  }

  /** Returns true if the given rule is enabled for fixing. */
  public boolean isRuleEnabled(String ruleId) {
    return !disabledRules.contains(ruleId);
  }

  /** Builder for StructureFixerConfig. */
  public static class Builder {
    private boolean enabled = true;
    private boolean dryRun = false;
    private String javaVersion = "21";
    private boolean backupFiles = true;
    private Set<String> enabledFixers = Set.of();
    private Set<String> disabledFixers = Set.of();
    private Set<String> disabledRules = Set.of();
    private Path outputPath = Path.of("target/structure-fix-report");
    private List<String> classpath = List.of();

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
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

    public Builder backupFiles(boolean backupFiles) {
      this.backupFiles = backupFiles;
      return this;
    }

    public Builder enabledFixers(Set<String> enabledFixers) {
      this.enabledFixers = enabledFixers;
      return this;
    }

    public Builder disabledFixers(Set<String> disabledFixers) {
      this.disabledFixers = disabledFixers;
      return this;
    }

    public Builder disabledRules(Set<String> disabledRules) {
      this.disabledRules = disabledRules;
      return this;
    }

    public Builder outputPath(Path outputPath) {
      this.outputPath = outputPath;
      return this;
    }

    public Builder classpath(List<String> classpath) {
      this.classpath = classpath;
      return this;
    }

    public StructureFixerConfig build() {
      return new StructureFixerConfig(
          enabled,
          dryRun,
          javaVersion,
          backupFiles,
          enabledFixers,
          disabledFixers,
          disabledRules,
          outputPath,
          classpath);
    }
  }
}
