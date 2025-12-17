package dev.engineeringlab.stratify.structure.remediation.core;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.Fixer;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.FixerWorkflowTracker.Operation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * Abstract base class for structure fixers.
 *
 * <p>Provides common functionality for fixers including:
 *
 * <ul>
 *   <li>Rule ID matching
 *   <li>Dry-run support
 *   <li>File backup (stored in {@code .remediation/staging/})
 *   <li>Error handling
 * </ul>
 *
 * <p>Backups are stored in a dedicated staging directory ({@code .remediation/staging/}) relative
 * to the project root, preserving the original file's relative path structure. This keeps backups
 * isolated and easy to clean up.
 */
public abstract class AbstractStructureFixer implements Fixer {

  /** Directory name for storing backups and staging files. */
  public static final String STAGING_DIR = ".remediation/staging";

  private boolean enabled = true;
  private int priority = 50;

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  @Override
  public String getDescription() {
    // Default implementation - builds description from supported rules
    return "Fixes " + String.join(", ", getAllSupportedRules()) + " violations";
  }

  @Override
  public boolean canFix(StructureViolation violation) {
    if (violation == null || violation.ruleId() == null) {
      return false;
    }
    Set<String> supported = Set.of(getAllSupportedRules());
    return supported.contains(violation.ruleId());
  }

  @Override
  public List<FixResult> fixAll(List<StructureViolation> violations, FixerContext context) {
    return violations.stream().filter(this::canFix).map(v -> safeFix(v, context)).toList();
  }

  /** Wraps the fix method with error handling. */
  protected FixResult safeFix(StructureViolation violation, FixerContext context) {
    try {
      return fix(violation, context);
    } catch (Exception e) {
      context.log("Error fixing %s: %s", violation.ruleId(), e.getMessage());
      return FixResult.failed(violation, e.getMessage());
    }
  }

  /**
   * Creates a backup of the given file in the staging directory.
   *
   * <p>The backup is stored in {@code {projectRoot}/.remediation/staging/} with the same relative
   * path as the original file, plus a {@code .bak} extension.
   *
   * @param file the file to backup
   * @param projectRoot the project root directory (for staging location)
   * @return the path to the backup file
   * @throws IOException if backup fails
   */
  protected Path backup(Path file, Path projectRoot) throws IOException {
    if (!Files.exists(file)) {
      return null;
    }
    Path backup = getStagingPath(file, projectRoot);
    // Ensure staging directory exists
    Files.createDirectories(backup.getParent());
    // Delete existing backup if present
    Files.deleteIfExists(backup);
    Files.copy(file, backup);
    trackOperation(Operation.BACKUP, file);
    return backup;
  }

  /**
   * Restores a file from its backup in the staging directory.
   *
   * @param file the file to restore
   * @param projectRoot the project root directory (for staging location)
   * @throws IOException if restore fails
   */
  protected void restore(Path file, Path projectRoot) throws IOException {
    Path backup = getStagingPath(file, projectRoot);
    if (Files.exists(backup)) {
      Files.move(backup, file, StandardCopyOption.REPLACE_EXISTING);
      trackOperation(Operation.RESTORE, file);
    }
  }

  /**
   * Deletes the backup of the given file from the staging directory.
   *
   * @param file the file whose backup should be deleted
   * @param projectRoot the project root directory (for staging location)
   * @throws IOException if deletion fails
   */
  protected void deleteBackup(Path file, Path projectRoot) throws IOException {
    Path backup = getStagingPath(file, projectRoot);
    Files.deleteIfExists(backup);
    trackOperation(Operation.DELETE_BACKUP, file);
  }

  /**
   * Gets the staging path for a file's backup.
   *
   * <p>The staging path preserves the relative directory structure: {@code
   * {projectRoot}/.remediation/staging/{relativePath}.bak}
   *
   * @param file the original file
   * @param projectRoot the project root directory
   * @return the path where the backup should be stored
   */
  protected Path getStagingPath(Path file, Path projectRoot) {
    Path relativePath = projectRoot.relativize(file.toAbsolutePath());
    return projectRoot.resolve(STAGING_DIR).resolve(relativePath + ".bak");
  }

  /**
   * Cleans up the staging directory by deleting all backup files.
   *
   * @param projectRoot the project root directory
   * @throws IOException if cleanup fails
   */
  protected void cleanupStaging(Path projectRoot) throws IOException {
    Path stagingDir = projectRoot.resolve(STAGING_DIR);
    if (Files.exists(stagingDir)) {
      Files.walk(stagingDir)
          .sorted((a, b) -> b.compareTo(a)) // reverse order for deletion
          .forEach(
              path -> {
                try {
                  Files.deleteIfExists(path);
                } catch (IOException e) {
                  // ignore cleanup errors
                }
              });
    }
    trackOperation(Operation.CLEANUP_STAGING);
  }

  /**
   * Cleans up backups after a successful fix.
   *
   * <p>This method should be called after a fix completes successfully to remove backup files from
   * the staging directory.
   *
   * @param files the files that were backed up
   * @param projectRoot the project root directory
   */
  protected void cleanupBackupsOnSuccess(List<Path> files, Path projectRoot) {
    trackOperation(Operation.CLEANUP_ON_SUCCESS);
    for (Path file : files) {
      try {
        deleteBackup(file, projectRoot);
      } catch (IOException e) {
        // Ignore cleanup errors - backups are optional
      }
    }
    // Also try to clean up empty staging directories
    try {
      Path stagingDir = projectRoot.resolve(STAGING_DIR);
      if (Files.exists(stagingDir)) {
        // Clean up empty parent directories
        Files.walk(stagingDir)
            .sorted((a, b) -> b.compareTo(a))
            .filter(Files::isDirectory)
            .forEach(
                dir -> {
                  try {
                    // Only delete if empty
                    if (Files.list(dir).findAny().isEmpty()) {
                      Files.delete(dir);
                    }
                  } catch (IOException ignored) {
                  }
                });
      }
    } catch (IOException ignored) {
    }
  }

  /**
   * Restores files from backup after a failed fix.
   *
   * <p>This method should be called when a fix fails to restore the original files from their
   * backups.
   *
   * @param files the files that were backed up
   * @param projectRoot the project root directory
   */
  protected void rollbackOnFailure(List<Path> files, Path projectRoot) {
    trackOperation(Operation.ROLLBACK_ON_FAILURE);
    for (Path file : files) {
      try {
        restore(file, projectRoot);
      } catch (IOException e) {
        // Log but continue with other files
      }
    }
    // Cleanup any remaining staging files
    try {
      cleanupStaging(projectRoot);
    } catch (IOException ignored) {
    }
  }

  /**
   * Reads the content of a file.
   *
   * @param file the file to read
   * @return the file content
   * @throws IOException if reading fails
   */
  protected String readFile(Path file) throws IOException {
    trackOperation(Operation.READ_FILE, file);
    return Files.readString(file);
  }

  /**
   * Writes content to a file.
   *
   * @param file the file to write
   * @param content the content to write
   * @throws IOException if writing fails
   */
  protected void writeFile(Path file, String content) throws IOException {
    Files.writeString(file, content);
    trackOperation(Operation.WRITE_FILE, file);
  }

  /**
   * Creates a directory if it doesn't exist.
   *
   * @param dir the directory to create
   * @throws IOException if creation fails
   */
  protected void ensureDirectory(Path dir) throws IOException {
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }
  }

  /**
   * Derives the module root from the violation location.
   *
   * <p>This method extracts the correct module root from a violation's location, which is essential
   * when remediating violations in submodules. The context's moduleRoot points to the project being
   * scanned, but violations may occur in nested submodules.
   *
   * <p>The derivation logic handles different violation location patterns:
   *
   * <ul>
   *   <li>Config file paths (e.g., "module/config/file.yml") -> go up 2 levels
   *   <li>Directory paths (e.g., "module/subdir") -> parent is module root
   *   <li>Documentation paths (e.g., "module/doc/3-design") -> traverse up past doc/
   *   <li>Direct module paths (e.g., "module") -> use as-is if has pom.xml
   * </ul>
   *
   * @param violation the violation whose module root to derive
   * @param context the fixer context (provides projectRoot and fallback moduleRoot)
   * @return the derived module root, or context.moduleRoot() as fallback
   */
  protected Path deriveModuleRoot(StructureViolation violation, FixerContext context) {
    Path location = violation.location();
    if (location == null) {
      return context.moduleRoot();
    }

    // Make location absolute if relative
    if (!location.isAbsolute()) {
      location = context.projectRoot().resolve(location);
    }

    // Strategy 1: If location is inside config/, go up to module root
    // e.g., "module/config/bootstrap.yaml" -> "module"
    String locationStr = location.toString();
    if (locationStr.contains("/config/")) {
      Path current = location;
      while (current != null && current.getFileName() != null) {
        if (current.getFileName().toString().equals("config")) {
          Path moduleRoot = current.getParent();
          if (moduleRoot != null && isValidModuleRoot(moduleRoot)) {
            return moduleRoot;
          }
        }
        current = current.getParent();
      }
    }

    // Strategy 2: If location is inside doc/, go up to module root
    // e.g., "module/doc/3-design" -> "module"
    if (locationStr.contains("/doc/")) {
      Path current = location;
      while (current != null && current.getFileName() != null) {
        String name = current.getFileName().toString();
        if (name.equals("doc") || name.matches("\\d+-.*")) {
          current = current.getParent();
        } else {
          if (isValidModuleRoot(current)) {
            return current;
          }
          break;
        }
      }
    }

    // Strategy 3: If location is a file, get parent directory
    if (Files.isRegularFile(location)) {
      location = location.getParent();
    }

    // Strategy 4: If location itself is a valid module root, use it
    if (isValidModuleRoot(location)) {
      return location;
    }

    // Strategy 5: If parent is a valid module root, use parent
    // e.g., "module/submodule" -> "module" (for undeclared directory violations)
    Path parent = location.getParent();
    if (parent != null && isValidModuleRoot(parent)) {
      return parent;
    }

    // Fallback to context module root
    return context.moduleRoot();
  }

  /**
   * Checks if a path is a valid Maven module root (has pom.xml).
   *
   * @param path the path to check
   * @return true if the path is a directory containing pom.xml
   */
  protected boolean isValidModuleRoot(Path path) {
    return path != null && Files.isDirectory(path) && Files.exists(path.resolve("pom.xml"));
  }

  // ========== String Utilities ==========

  /**
   * Extracts the base module name from a path, removing common suffixes.
   *
   * <p>Removes suffixes like -parent, -api, -core, -spi, -facade, -plugin, -aggregator.
   *
   * @param moduleRoot the module root path
   * @return the base module name
   */
  protected String extractModuleName(Path moduleRoot) {
    String name = moduleRoot.getFileName().toString();
    // Remove common suffixes to get base name
    name = name.replaceAll("-(parent|api|core|spi|facade|plugin|aggregator|engine)$", "");
    return name;
  }

  /**
   * Converts a hyphenated or underscored string to PascalCase.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"my-module" -> "MyModule"
   *   <li>"some_name" -> "SomeName"
   * </ul>
   *
   * @param str the string to convert
   * @return the PascalCase string
   */
  protected String toPascalCase(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : str.toCharArray()) {
      if (c == '-' || c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  /**
   * Converts a hyphenated or underscored string to Title Case with spaces.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>"my-module" -> "My Module"
   *   <li>"some_name" -> "Some Name"
   * </ul>
   *
   * @param str the string to convert
   * @return the Title Case string
   */
  protected String toTitleCase(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : str.toCharArray()) {
      if (c == '-' || c == '_') {
        result.append(' ');
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }

  // ========== XML Utilities ==========

  /**
   * Extracts a value from an XML tag, preferring values outside the parent block.
   *
   * <p>This is useful for pom.xml parsing where groupId/version may appear in both the parent block
   * and the project's own definition.
   *
   * @param content the XML content
   * @param tag the tag name to find
   * @param defaultValue the default value if not found
   * @return the extracted value or default
   */
  protected String extractXmlValue(String content, String tag, String defaultValue) {
    String patternStr = "<" + tag + ">([^<]+)</" + tag + ">";
    Pattern p = Pattern.compile(patternStr);
    Matcher m = p.matcher(content);

    int parentEnd = content.indexOf("</parent>");
    // First, try to find a value outside the parent block
    while (m.find()) {
      if (parentEnd < 0 || m.start() > parentEnd) {
        return m.group(1).trim();
      }
    }

    // Fall back to any value found
    m.reset();
    if (m.find()) {
      return m.group(1).trim();
    }

    return defaultValue;
  }

  /**
   * Adds a module to the parent pom.xml's modules section.
   *
   * <p>Creates the modules section if it doesn't exist. Skips if the module is already listed.
   *
   * @param parentPom the path to the parent pom.xml
   * @param newModuleName the module name to add
   * @param context the fixer context for logging and backup
   * @throws IOException if reading or writing fails
   */
  protected void addModuleToParentPom(Path parentPom, String newModuleName, FixerContext context)
      throws IOException {

    String content = readFile(parentPom);

    // Check if module already listed
    if (content.contains("<module>" + newModuleName + "</module>")) {
      context.log("Module already in parent pom.xml: %s", newModuleName);
      return;
    }

    // Find the </modules> tag and insert before it
    int modulesEnd = content.indexOf("</modules>");
    if (modulesEnd < 0) {
      // No modules section - create one
      int projectEnd = content.lastIndexOf("</project>");
      if (projectEnd > 0) {
        String modulesSection =
            "\n    <modules>\n        <module>" + newModuleName + "</module>\n    </modules>\n";
        content = content.substring(0, projectEnd) + modulesSection + content.substring(projectEnd);
      }
    } else {
      // Insert new module before </modules>
      String newModule = "        <module>" + newModuleName + "</module>\n    ";
      content = content.substring(0, modulesEnd) + newModule + content.substring(modulesEnd);
    }

    // Backup and write
    backup(parentPom, context.projectRoot());
    writeFile(parentPom, content);
    context.log("Added module to parent pom.xml: %s", newModuleName);
  }

  // ========== YAML Utilities ==========

  /**
   * Loads a YAML configuration file into a Map.
   *
   * @param configPath the path to the YAML file
   * @return the configuration as a Map, or empty Map if null
   * @throws IOException if reading fails
   */
  protected Map<String, Object> loadYamlConfig(Path configPath) throws IOException {
    Yaml yaml = new Yaml();
    String content = Files.readString(configPath);
    Map<String, Object> config = yaml.load(content);
    return config != null ? config : new LinkedHashMap<>();
  }

  /**
   * Writes a Map configuration to a YAML file with a header comment.
   *
   * @param configPath the path to write to
   * @param config the configuration Map
   * @param headerComment the header comment (without # prefix)
   * @throws IOException if writing fails
   */
  protected void writeYamlConfig(Path configPath, Map<String, Object> config, String headerComment)
      throws IOException {
    DumperOptions options = new DumperOptions();
    options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
    options.setPrettyFlow(true);
    options.setIndent(2);
    Yaml yaml = new Yaml(options);
    String content = yaml.dump(config);

    String header = headerComment != null ? headerComment + "\n\n" : "";
    Files.writeString(configPath, header + content);
  }

  /**
   * Writes a Map configuration to a YAML file with a standard module header.
   *
   * @param configPath the path to write to
   * @param config the configuration Map
   * @param configType the type of configuration (e.g., "Aggregator", "Parent")
   * @throws IOException if writing fails
   */
  protected void writeYamlConfigWithModuleHeader(
      Path configPath, Map<String, Object> config, String configType) throws IOException {
    String moduleName = configPath.getParent().getParent().getFileName().toString();
    String header =
        "# Module "
            + configType
            + " Configuration\n"
            + "# Module: "
            + moduleName
            + "\n"
            + "# Auto-generated - declares all child modules and their types";
    writeYamlConfig(configPath, config, header);
  }

  // ========== Module Creation Utilities ==========

  /**
   * Generates a basic package-info.java content.
   *
   * @param packageName the fully qualified package name
   * @param moduleName the module name for documentation
   * @param layerDescription description of the layer (e.g., "API layer", "Facade layer")
   * @return the package-info.java content
   */
  protected String generatePackageInfo(
      String packageName, String moduleName, String layerDescription) {
    return """
        /**
         * %s for %s.
         *
         * @see <a href="compliance-checklist.md">SEA Compliance Checklist</a>
         */
        package %s;
        """
        .formatted(layerDescription, moduleName, packageName);
  }

  /**
   * Deletes a directory and all its contents recursively.
   *
   * @param dir the directory to delete
   * @throws IOException if deletion fails
   */
  protected void deleteDirectoryRecursively(Path dir) throws IOException {
    if (Files.exists(dir)) {
      Files.walk(dir)
          .sorted((a, b) -> b.compareTo(a)) // Reverse order to delete files before directories
          .forEach(
              path -> {
                try {
                  Files.delete(path);
                } catch (IOException ignored) {
                }
              });
    }
  }

  /**
   * Generates a unified diff between original and modified content.
   *
   * @param original the original content
   * @param modified the modified content
   * @param fileName the file name for the diff header
   * @return the unified diff
   */
  protected String generateDiff(String original, String modified, String fileName) {
    // Simple line-by-line diff (can be enhanced with proper diff library)
    StringBuilder diff = new StringBuilder();
    diff.append("--- a/").append(fileName).append("\n");
    diff.append("+++ b/").append(fileName).append("\n");

    String[] origLines = original.split("\n", -1);
    String[] modLines = modified.split("\n", -1);

    int maxLines = Math.max(origLines.length, modLines.length);
    for (int i = 0; i < maxLines; i++) {
      String origLine = i < origLines.length ? origLines[i] : "";
      String modLine = i < modLines.length ? modLines[i] : "";

      if (!origLine.equals(modLine)) {
        if (i < origLines.length) {
          diff.append("-").append(origLine).append("\n");
        }
        if (i < modLines.length) {
          diff.append("+").append(modLine).append("\n");
        }
      }
    }

    return diff.toString();
  }

  @Override
  public String toString() {
    return getName()
        + " (rules: "
        + Arrays.toString(getAllSupportedRules())
        + ", priority: "
        + priority
        + ")";
  }

  // ========== Workflow Tracking ==========

  /**
   * Records a workflow operation if tracking is enabled.
   *
   * @param operation the operation type
   * @param file the file involved, or null if not applicable
   */
  private void trackOperation(Operation operation, Path file) {
    FixerWorkflowTracker tracker = FixerWorkflowTracker.current();
    if (tracker != null) {
      tracker.record(operation, file);
    }
  }

  /**
   * Records a workflow operation without a file if tracking is enabled.
   *
   * @param operation the operation type
   */
  private void trackOperation(Operation operation) {
    trackOperation(operation, null);
  }

  /**
   * Creates a workflow tracker for this fixer.
   *
   * <p>Use this to track and validate workflow compliance during testing:
   *
   * <pre>{@code
   * FixerWorkflowTracker tracker = fixer.createWorkflowTracker();
   * try {
   *     tracker.start();
   *     FixResult result = fixer.fix(violation, context);
   *     ValidationResult validation = tracker.validateSuccessWorkflow();
   *     assertThat(validation.isValid()).isTrue();
   * } finally {
   *     tracker.stop();
   * }
   * }</pre>
   *
   * @return a new workflow tracker for this fixer
   */
  public FixerWorkflowTracker createWorkflowTracker() {
    return new FixerWorkflowTracker(getName());
  }
}
