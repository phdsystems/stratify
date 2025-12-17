package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-016: All Submodules Must Be Listed.
 *
 * <p>All subdirectories containing pom.xml must be listed in the aggregator's modules section. This
 * fixer adds the unlisted submodules to the modules section.
 *
 * <p>Example transformation:
 *
 * <pre>
 * // Before (with unlisted submodule 'feature-facade')
 * &lt;modules&gt;
 *     &lt;module&gt;feature-api&lt;/module&gt;
 *     &lt;module&gt;feature-core&lt;/module&gt;
 * &lt;/modules&gt;
 *
 * // After
 * &lt;modules&gt;
 *     &lt;module&gt;feature-api&lt;/module&gt;
 *     &lt;module&gt;feature-core&lt;/module&gt;
 *     &lt;module&gt;feature-facade&lt;/module&gt;
 * &lt;/modules&gt;
 * </pre>
 *
 * @see dev.engineeringlab.scanner.plugin.scanner.plugin.ms.scanner.core.MS016AllSubmodulesListed
 * @since 0.2.0
 */
public class MS016UnlistedSubmodulesRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-016"};
  private static final int PRIORITY = 45;

  // Pattern to match the modules section
  private static final Pattern MODULES_SECTION_PATTERN =
      Pattern.compile("(<modules>)(.*?)(</modules>)", Pattern.DOTALL);

  // Pattern to extract module names from violation message
  private static final Pattern UNLISTED_MODULES_PATTERN =
      Pattern.compile("unlisted submodules?: (.+)$");

  public MS016UnlistedSubmodulesRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS016UnlistedSubmodulesRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-016 violations: adds unlisted submodules to the modules section in pom.xml";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-016 violation");
    }

    Path location = violation.location();
    if (location == null) {
      return FixResult.skipped(violation, "No location specified");
    }

    // If location is a directory, look for pom.xml in it
    Path pomFile = location;
    if (Files.isDirectory(location)) {
      pomFile = location.resolve("pom.xml");
    }

    if (!pomFile.toString().endsWith("pom.xml")) {
      return FixResult.skipped(violation, "Not a pom.xml file: " + pomFile);
    }

    if (!Files.exists(pomFile)) {
      return FixResult.skipped(violation, "File not found: " + pomFile);
    }

    // Extract unlisted modules from violation message
    List<String> unlistedModules = extractUnlistedModules(violation.message());
    if (unlistedModules.isEmpty()) {
      return FixResult.skipped(
          violation, "Could not parse unlisted modules from violation message");
    }

    try {
      return fixPomFile(pomFile, unlistedModules, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error adding unlisted submodules: " + e.getMessage());
    }
  }

  /**
   * Extracts unlisted module names from violation message. Message format: "Aggregator module 'X'
   * has N unlisted submodules: module1, module2"
   */
  private List<String> extractUnlistedModules(String message) {
    List<String> modules = new ArrayList<>();

    Matcher matcher = UNLISTED_MODULES_PATTERN.matcher(message);
    if (matcher.find()) {
      String modulesList = matcher.group(1).trim();
      for (String module : modulesList.split(",")) {
        String trimmed = module.trim();
        if (!trimmed.isEmpty()) {
          modules.add(trimmed);
        }
      }
    }

    return modules;
  }

  private FixResult fixPomFile(
      Path pomFile,
      List<String> unlistedModules,
      StructureViolation violation,
      FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Find modules section
    Matcher modulesMatcher = MODULES_SECTION_PATTERN.matcher(originalContent);
    if (!modulesMatcher.find()) {
      // No modules section exists, create one
      return createModulesSection(pomFile, unlistedModules, originalContent, violation, context);
    }

    String modulesStart = modulesMatcher.group(1);
    String modulesContent = modulesMatcher.group(2);
    String modulesEnd = modulesMatcher.group(3);

    // Build new modules content
    StringBuilder newModulesContent = new StringBuilder(modulesContent);

    // Determine indentation
    String indent = detectIndentation(modulesContent);

    // Add unlisted modules
    for (String module : unlistedModules) {
      newModulesContent
          .append("\n")
          .append(indent)
          .append("<module>")
          .append(module)
          .append("</module>");
    }

    String modifiedContent =
        modulesMatcher.replaceFirst(
            Matcher.quoteReplacement(modulesStart + newModulesContent + "\n    " + modulesEnd));

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    List<Path> modifiedFiles = new ArrayList<>();
    modifiedFiles.add(pomFile);

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());
    List<String> diffs = new ArrayList<>();
    diffs.add(diff);

    context.log(
        "Adding %d unlisted submodules to %s:", unlistedModules.size(), pomFile.getFileName());
    for (String module : unlistedModules) {
      context.log("  - %s", module);
    }

    if (context.dryRun()) {
      context.log("[DRY-RUN] Would add %d submodules to modules section", unlistedModules.size());
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format("Would add %d unlisted submodules (dry-run)", unlistedModules.size()))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Added %d submodules to modules section in %s", unlistedModules.size(), pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Added %d unlisted submodules: %s",
                unlistedModules.size(), String.join(", ", unlistedModules)))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /** Creates a new modules section with the unlisted modules. */
  private FixResult createModulesSection(
      Path pomFile,
      List<String> unlistedModules,
      String originalContent,
      StructureViolation violation,
      FixerContext context)
      throws Exception {
    StringBuilder modulesSection = new StringBuilder("\n    <modules>\n");
    for (String module : unlistedModules) {
      modulesSection.append("        <module>").append(module).append("</module>\n");
    }
    modulesSection.append("    </modules>\n");

    // Insert before </project>
    String modifiedContent = originalContent.replace("</project>", modulesSection + "</project>");

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    List<Path> modifiedFiles = new ArrayList<>();
    modifiedFiles.add(pomFile);

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());
    List<String> diffs = new ArrayList<>();
    diffs.add(diff);

    if (context.dryRun()) {
      context.log(
          "[DRY-RUN] Would create modules section with %d submodules", unlistedModules.size());
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would create modules section with %d submodules (dry-run)",
                  unlistedModules.size()))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log(
        "Created modules section with %d submodules in %s", unlistedModules.size(), pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Created modules section with %d submodules: %s",
                unlistedModules.size(), String.join(", ", unlistedModules)))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /** Detects the indentation used in the modules content. */
  private String detectIndentation(String modulesContent) {
    // Look for existing module elements to detect indentation
    Pattern indentPattern = Pattern.compile("(\\s*)<module>");
    Matcher matcher = indentPattern.matcher(modulesContent);
    if (matcher.find()) {
      String indent = matcher.group(1);
      // Extract only whitespace from the last line
      int lastNewline = indent.lastIndexOf('\n');
      if (lastNewline >= 0) {
        return indent.substring(lastNewline + 1);
      }
      return indent;
    }
    return "        "; // Default 8 spaces
  }
}
