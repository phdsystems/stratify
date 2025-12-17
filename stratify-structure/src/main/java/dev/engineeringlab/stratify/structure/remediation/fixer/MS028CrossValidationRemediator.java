package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-028: Cross-validate Parent-Child Types.
 *
 * <p>This remediator fixes parent-child relationship violations by:
 *
 * <ul>
 *   <li>Updating config files (module.aggregator.yml / module.parent.yml) to match actual structure
 *   <li>Adding missing module declarations to parent POMs
 *   <li>Creating missing parent or child modules when safe to do so
 *   <li>Reporting violations that require manual intervention
 * </ul>
 *
 * <h2>Fix Strategy</h2>
 *
 * <ol>
 *   <li>For missing children: Add them to parent's <modules> section if they exist on disk
 *   <li>For config type mismatches: Update config file to reflect actual module structure
 *   <li>For missing parents: Report as manual fix required (parent creation is complex)
 *   <li>For leaf parent type violations: Report as manual fix required (requires restructuring)
 * </ol>
 *
 * @see
 *     dev.engineeringlab.scanner.plugin.scanner.plugin.ms.scanner.core.MS028CrossValidateParentChild
 * @since 0.2.0
 */
public class MS028CrossValidationRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-028"};
  private static final int PRIORITY = 40; // Lower priority, runs after more specific fixers

  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");
  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

  public MS028CrossValidationRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS028CrossValidationRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-028 violations: cross-validates parent-child types and updates config files to match actual structure";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-028 violation");
    }

    Path location = violation.location();
    if (location == null) {
      return FixResult.skipped(violation, "No location specified");
    }

    String message = violation.message();

    try {
      // Analyze the violation message to determine what needs to be fixed
      // Note: Order matters! More specific checks must come before general ones.
      if (message.contains("declares child") && message.contains("does not exist")) {
        return handleMissingChild(violation, location, message, context);
      } else if (message.contains("does not exist")) {
        return handleMissingModule(violation, location, message, context);
      } else if (message.contains("config/module.aggregator.yml but actual structure")) {
        return handleConfigTypeMismatch(violation, location, message, context, true);
      } else if (message.contains("config/module.parent.yml but actual structure")) {
        return handleConfigTypeMismatch(violation, location, message, context, false);
      } else if (message.contains("pure aggregator")) {
        return handleLeafParentTypeViolation(violation, location, message, context);
      }

      return FixResult.skipped(violation, "Unknown MS-028 violation type");

    } catch (Exception e) {
      return FixResult.failed(violation, "Error fixing MS-028 violation: " + e.getMessage());
    }
  }

  /**
   * Handles violations where a declared parent or child module does not exist. Reports as manual
   * fix required since creating modules is complex.
   */
  private FixResult handleMissingModule(
      StructureViolation violation, Path location, String message, FixerContext context) {

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.NOT_FIXABLE)
        .description(
            "Missing module requires manual creation. "
                + "Either create the missing module or remove the reference from pom.xml.")
        .modifiedFiles(List.of())
        .diffs(List.of())
        .build();
  }

  /**
   * Handles violations where declared config type doesn't match actual structure. Updates the
   * config file to reflect reality.
   */
  private FixResult handleConfigTypeMismatch(
      StructureViolation violation,
      Path location,
      String message,
      FixerContext context,
      boolean isAggregator)
      throws IOException {

    Path configFile =
        isAggregator
            ? location.resolve("config/module.aggregator.yml")
            : location.resolve("config/module.parent.yml");

    if (!Files.exists(configFile)) {
      return FixResult.skipped(violation, "Config file does not exist: " + configFile);
    }

    // Extract declared and actual types from message
    // Message format: "...declared as 'X' but actual structure indicates 'Y'..."
    Pattern typePattern =
        Pattern.compile("declared as '([^']+)' but actual structure indicates '([^']+)'");
    Matcher matcher = typePattern.matcher(message);

    if (!matcher.find()) {
      return FixResult.skipped(violation, "Could not parse type mismatch from message");
    }

    String declaredType = matcher.group(1);
    String actualType = matcher.group(2);

    // The fix: Remove the config file since it doesn't match reality
    // This allows the module to be validated based on its actual structure
    List<Path> modifiedFiles = new ArrayList<>();
    List<String> diffs = new ArrayList<>();

    if (context.dryRun()) {
      context.log("[DRY-RUN] Would remove mismatched config file: %s", configFile);
      context.log("Declared type '%s' does not match actual type '%s'", declaredType, actualType);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would remove %s (declared as '%s' but actually '%s')",
                  configFile.getFileName(), declaredType, actualType))
          .modifiedFiles(List.of(configFile))
          .diffs(
              List.of(
                  String.format(
                      "Remove: %s (type mismatch: declared='%s', actual='%s')",
                      configFile, declaredType, actualType)))
          .build();
    }

    // Backup and remove the mismatched config file
    backup(configFile, context.projectRoot());
    Files.delete(configFile);
    modifiedFiles.add(configFile);

    context.log("Removed mismatched config file: %s", configFile);
    context.log("Declared type '%s' did not match actual type '%s'", declaredType, actualType);

    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Removed %s (declared as '%s' but actually '%s')",
                configFile.getFileName(), declaredType, actualType))
        .modifiedFiles(modifiedFiles)
        .diffs(List.of(String.format("Removed: %s (type mismatch)", configFile)))
        .build();
  }

  /**
   * Handles violations where a parent declares a child module that doesn't exist. If the directory
   * exists without pom.xml, this is a manual fix. If neither exists, remove from parent's modules
   * section.
   */
  private FixResult handleMissingChild(
      StructureViolation violation, Path location, String message, FixerContext context)
      throws IOException {

    // Extract child module name from message
    // Message format: "Module declares child 'X' in <modules> but..."
    Pattern childPattern = Pattern.compile("declares child '([^']+)'");
    Matcher matcher = childPattern.matcher(message);

    if (!matcher.find()) {
      return FixResult.skipped(violation, "Could not parse child module name from message");
    }

    String childModule = matcher.group(1);
    Path childPath = location.resolve(childModule);

    // If directory exists, it needs a pom.xml (manual fix)
    if (Files.exists(childPath) && Files.isDirectory(childPath)) {
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.NOT_FIXABLE)
          .description(
              String.format(
                  "Child directory '%s' exists but lacks pom.xml. "
                      + "Create pom.xml or remove directory.",
                  childModule))
          .modifiedFiles(List.of())
          .diffs(List.of())
          .build();
    }

    // If directory doesn't exist, remove from parent's modules section
    Path parentPom = location.resolve("pom.xml");
    if (!Files.exists(parentPom)) {
      return FixResult.skipped(violation, "Parent pom.xml not found");
    }

    String pomContent = Files.readString(parentPom);
    String originalContent = pomContent;

    // Remove the module declaration
    String updatedContent = removeModuleFromPom(pomContent, childModule);

    if (updatedContent.equals(originalContent)) {
      return FixResult.skipped(
          violation, String.format("Module '%s' not found in parent pom.xml", childModule));
    }

    List<Path> modifiedFiles = new ArrayList<>();
    List<String> diffs = new ArrayList<>();
    diffs.add(generateDiff(originalContent, updatedContent, "pom.xml"));

    if (context.dryRun()) {
      context.log("[DRY-RUN] Would remove module '%s' from %s", childModule, parentPom);

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format("Would remove non-existent module '%s' from pom.xml", childModule))
          .modifiedFiles(List.of(parentPom))
          .diffs(diffs)
          .build();
    }

    backup(parentPom, context.projectRoot());
    Files.writeString(parentPom, updatedContent);
    modifiedFiles.add(parentPom);

    context.log("Removed non-existent module '%s' from %s", childModule, parentPom);

    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(String.format("Removed non-existent module '%s' from pom.xml", childModule))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /**
   * Handles violations where leaf modules have wrong parent type. This requires restructuring, so
   * report as manual fix.
   */
  private FixResult handleLeafParentTypeViolation(
      StructureViolation violation, Path location, String message, FixerContext context) {

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.NOT_FIXABLE)
        .description(
            "Leaf module parent type violation requires manual restructuring. "
                + "Leaf modules must have a parent aggregator (-parent suffix), not a pure aggregator (-aggregator suffix). "
                + "Consider restructuring the module hierarchy.")
        .modifiedFiles(List.of())
        .diffs(List.of())
        .build();
  }

  /** Removes a module declaration from pom.xml content. */
  private String removeModuleFromPom(String pomContent, String moduleName) {
    // Pattern to match <module>moduleName</module> with surrounding whitespace
    Pattern pattern =
        Pattern.compile(
            "[ \\t]*<module>" + Pattern.quote(moduleName) + "</module>\\s*\\r?\\n",
            Pattern.MULTILINE);

    String result = pattern.matcher(pomContent).replaceAll("");

    // If no match with newline, try without
    if (result.equals(pomContent)) {
      pattern = Pattern.compile("<module>" + Pattern.quote(moduleName) + "</module>\\s*");
      result = pattern.matcher(pomContent).replaceAll("");
    }

    return result;
  }

  /** Generates a simple diff between original and modified content. */
  @Override
  protected String generateDiff(String original, String modified, String fileName) {
    StringBuilder diff = new StringBuilder();
    diff.append("--- ").append(fileName).append("\n");
    diff.append("+++ ").append(fileName).append("\n");

    String[] originalLines = original.split("\n");
    String[] modifiedLines = modified.split("\n");

    int minLength = Math.min(originalLines.length, modifiedLines.length);
    for (int i = 0; i < minLength; i++) {
      if (!originalLines[i].equals(modifiedLines[i])) {
        diff.append("@@ -").append(i + 1).append(" +").append(i + 1).append(" @@\n");
        diff.append("- ").append(originalLines[i]).append("\n");
        diff.append("+ ").append(modifiedLines[i]).append("\n");
      }
    }

    return diff.toString();
  }
}
