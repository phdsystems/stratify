package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-024: Parent Cannot Contain Another Parent.
 *
 * <p>Fixes violations where a parent aggregator (-parent suffix) contains another parent aggregator
 * as a submodule. The fix converts the nested parent to a pure aggregator (-aggregator suffix) by:
 *
 * <ol>
 *   <li>Renaming the nested module's artifactId from *-parent to *-aggregator
 *   <li>Updating the parent's module.parent.yml to reflect the change
 *   <li>Creating/updating the nested module's module.aggregator.yml config
 *   <li>Removing module.parent.yml from the nested module if it exists
 * </ol>
 *
 * <p>Following the principle: No assumptions - verify the child module actually exists and check
 * its type directly before making changes.
 */
public class MS024ParentNestingRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-024"};
  private static final int PRIORITY = 70;

  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("(<artifactId>)([^<]+)(</artifactId>)", Pattern.DOTALL);

  private static final Pattern MODULE_PATTERN = Pattern.compile("(<module>)([^<]+)(</module>)");

  /** Hierarchy validator for checking module types. */
  private final ModuleHierarchyValidator hierarchyValidator;

  public MS024ParentNestingRemediator() {
    this(new ModuleHierarchyValidator());
  }

  /**
   * Constructor with custom hierarchy validator (for testing).
   *
   * @param hierarchyValidator the validator to use
   */
  public MS024ParentNestingRemediator(ModuleHierarchyValidator hierarchyValidator) {
    setPriority(PRIORITY);
    this.hierarchyValidator = hierarchyValidator;
  }

  @Override
  public String getName() {
    return "MS024ParentNestingRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-024 violations: converts nested parent aggregators to pure aggregators";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-024 violation");
    }

    // The violation location points to the nested parent module
    Path nestedParentPath = violation.location();
    if (nestedParentPath == null || !Files.exists(nestedParentPath)) {
      return FixResult.skipped(
          violation, "Nested parent module not found at " + violation.location());
    }

    Path nestedParentPom = nestedParentPath.resolve("pom.xml");
    if (!Files.exists(nestedParentPom)) {
      return FixResult.skipped(
          violation, "pom.xml not found in nested parent: " + nestedParentPath);
    }

    try {
      return fixNestedParent(nestedParentPath, nestedParentPom, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error fixing parent nesting: " + e.getMessage());
    }
  }

  /** Fixes the nested parent by converting it to an aggregator. */
  private FixResult fixNestedParent(
      Path nestedParentPath,
      Path nestedParentPom,
      StructureViolation violation,
      FixerContext context)
      throws Exception {
    String pomContent = readFile(nestedParentPom);
    String currentArtifactId = extractArtifactId(pomContent);

    if (currentArtifactId == null || currentArtifactId.isEmpty()) {
      return FixResult.failed(violation, "Could not extract artifactId from nested parent pom.xml");
    }

    // Verify this is actually a parent aggregator
    ModuleHierarchyValidator.ModuleType moduleType =
        hierarchyValidator.detectModuleType(pomContent, nestedParentPath);

    if (moduleType != ModuleHierarchyValidator.ModuleType.PARENT_AGGREGATOR) {
      return FixResult.skipped(
          violation,
          "Module '"
              + currentArtifactId
              + "' is not a parent aggregator (type="
              + moduleType
              + ")");
    }

    // Determine new artifactId
    String newArtifactId;
    if (currentArtifactId.endsWith("-parent")) {
      newArtifactId =
          currentArtifactId.substring(0, currentArtifactId.length() - 7) + "-aggregator";
    } else {
      newArtifactId = currentArtifactId + "-aggregator";
    }

    List<Path> modifiedFiles = new ArrayList<>();
    List<String> diffs = new ArrayList<>();

    // Step 1: Rename artifactId in nested module's pom.xml
    String updatedPomContent = renameArtifactId(pomContent, currentArtifactId, newArtifactId);
    if (!updatedPomContent.equals(pomContent)) {
      String diff =
          generateDiff(pomContent, updatedPomContent, nestedParentPom.getFileName().toString());
      diffs.add(diff);

      if (!context.dryRun()) {
        backup(nestedParentPom, context.projectRoot());
        writeFile(nestedParentPom, updatedPomContent);
      }
      modifiedFiles.add(nestedParentPom);
    }

    // Step 2: Update parent's module.parent.yml if it exists
    Path parentPath = nestedParentPath.getParent();
    Path parentConfigPath = parentPath.resolve("config/module.parent.yml");
    if (Files.exists(parentConfigPath)) {
      Map<String, Object> parentConfig = loadYamlConfig(parentConfigPath);
      boolean configUpdated =
          updateModuleReference(
              parentConfig, nestedParentPath.getFileName().toString(), newArtifactId);

      if (configUpdated) {
        diffs.add("Updated module reference in parent's module.parent.yml");
        if (!context.dryRun()) {
          backup(parentConfigPath, context.projectRoot());
          writeYamlConfigWithModuleHeader(parentConfigPath, parentConfig, "Parent");
        }
        modifiedFiles.add(parentConfigPath);
      }
    }

    // Step 3: Convert module.parent.yml to module.aggregator.yml
    Path oldConfigPath = nestedParentPath.resolve("config/module.parent.yml");
    Path newConfigPath = nestedParentPath.resolve("config/module.aggregator.yml");

    if (Files.exists(oldConfigPath)) {
      Map<String, Object> config = loadYamlConfig(oldConfigPath);
      // Update module name
      if (config.containsKey("module")) {
        @SuppressWarnings("unchecked")
        Map<String, Object> moduleInfo = (Map<String, Object>) config.get("module");
        moduleInfo.put("name", newArtifactId);
      }

      diffs.add("Converted module.parent.yml to module.aggregator.yml");

      if (!context.dryRun()) {
        // Remove old config
        backup(oldConfigPath, context.projectRoot());
        Files.delete(oldConfigPath);

        // Write new config
        writeYamlConfigWithModuleHeader(newConfigPath, config, "Aggregator");
      }
      modifiedFiles.add(oldConfigPath);
      modifiedFiles.add(newConfigPath);
    } else if (!Files.exists(newConfigPath)) {
      // Create new module.aggregator.yml if neither exists
      Map<String, Object> newConfig = createDefaultAggregatorConfig(newArtifactId);
      diffs.add("Created module.aggregator.yml");

      if (!context.dryRun()) {
        if (!Files.exists(newConfigPath.getParent())) {
          Files.createDirectories(newConfigPath.getParent());
        }
        writeYamlConfigWithModuleHeader(newConfigPath, newConfig, "Aggregator");
      }
      modifiedFiles.add(newConfigPath);
    }

    // Step 4: Update any child modules that reference this as parent
    List<Path> childPoms = findChildModulePoms(nestedParentPath, updatedPomContent);
    for (Path childPom : childPoms) {
      if (Files.exists(childPom)) {
        String childContent = readFile(childPom);
        String updatedChildContent =
            updateParentArtifactId(childContent, currentArtifactId, newArtifactId);

        if (!childContent.equals(updatedChildContent)) {
          String diff =
              generateDiff(
                  childContent,
                  updatedChildContent,
                  childPom.getParent().getFileName() + "/pom.xml");
          diffs.add(diff);

          if (!context.dryRun()) {
            backup(childPom, context.projectRoot());
            writeFile(childPom, updatedChildContent);
          }
          modifiedFiles.add(childPom);
        }
      }
    }

    if (context.dryRun()) {
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description(
              String.format(
                  "Would convert parent '%s' to aggregator '%s' (dry-run)",
                  currentArtifactId, newArtifactId))
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();
    }

    context.log("Converted parent '%s' to aggregator '%s'", currentArtifactId, newArtifactId);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(modifiedFiles, context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description(
            String.format(
                "Converted parent '%s' to aggregator '%s'", currentArtifactId, newArtifactId))
        .modifiedFiles(modifiedFiles)
        .diffs(diffs)
        .build();
  }

  /** Extracts the artifactId from pom.xml content. */
  private String extractArtifactId(String content) {
    // Find the parent block end if it exists
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    // Find artifactId after parent block
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(content.substring(searchStart));
    if (matcher.find()) {
      return matcher.group(2).trim();
    }
    return null;
  }

  /** Renames the artifactId in the pom.xml content. */
  private String renameArtifactId(String content, String oldArtifactId, String newArtifactId) {
    // Find the parent block end if it exists
    int parentEnd = content.indexOf("</parent>");
    int searchStart = parentEnd >= 0 ? parentEnd : 0;

    // Split content at parent end
    String beforeParent = content.substring(0, searchStart);
    String afterParent = content.substring(searchStart);

    // Replace first artifactId occurrence after parent block
    Matcher matcher = ARTIFACT_ID_PATTERN.matcher(afterParent);
    if (matcher.find()) {
      String replacement = matcher.group(1) + newArtifactId + matcher.group(3);
      afterParent = matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }

    return beforeParent + afterParent;
  }

  /** Updates parent artifactId references in child module pom files. */
  private String updateParentArtifactId(
      String content, String oldArtifactId, String newArtifactId) {
    Pattern parentArtifactPattern =
        Pattern.compile(
            "(<parent>.*?<artifactId>)([^<]+)(</artifactId>.*?</parent>)", Pattern.DOTALL);
    Matcher matcher = parentArtifactPattern.matcher(content);
    if (matcher.find() && matcher.group(2).trim().equals(oldArtifactId)) {
      String replacement = matcher.group(1) + newArtifactId + matcher.group(3);
      return matcher.replaceFirst(Matcher.quoteReplacement(replacement));
    }
    return content;
  }

  /** Finds child module pom.xml files by parsing the modules section. */
  private List<Path> findChildModulePoms(Path parentDir, String pomContent) {
    List<Path> childPoms = new ArrayList<>();
    Matcher matcher = MODULE_PATTERN.matcher(pomContent);

    while (matcher.find()) {
      String moduleName = matcher.group(2).trim();
      Path childPom = parentDir.resolve(moduleName).resolve("pom.xml");
      if (Files.exists(childPom)) {
        childPoms.add(childPom);
      }
    }

    return childPoms;
  }

  /**
   * Updates module reference in parent config (module.parent.yml).
   *
   * @param config the parent config
   * @param moduleName the module directory name
   * @param newArtifactId the new artifactId
   * @return true if config was updated
   */
  private boolean updateModuleReference(
      Map<String, Object> config, String moduleName, String newArtifactId) {
    boolean updated = false;

    // Update in layers section
    if (config.containsKey("layers")) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> layers = (List<Map<String, Object>>) config.get("layers");
      for (Map<String, Object> layer : layers) {
        String name = (String) layer.get("name");
        if (name != null && name.equals(moduleName)) {
          layer.put("name", newArtifactId);
          updated = true;
        }
      }
    }

    // Update in submodules section
    if (config.containsKey("submodules")) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> submodules = (List<Map<String, Object>>) config.get("submodules");
      for (Map<String, Object> submodule : submodules) {
        String name = (String) submodule.get("name");
        if (name != null && name.equals(moduleName)) {
          submodule.put("name", newArtifactId);
          // Update type from parent to aggregator
          submodule.put("type", "aggregator");
          updated = true;
        }
      }
    }

    return updated;
  }

  /** Creates a default module.aggregator.yml configuration. */
  private Map<String, Object> createDefaultAggregatorConfig(String moduleName) {
    Map<String, Object> config = new LinkedHashMap<>();

    Map<String, Object> module = new LinkedHashMap<>();
    module.put("name", moduleName);
    module.put("description", "Pure aggregator for " + moduleName);
    config.put("module", module);

    config.put("modules", new ArrayList<>());

    return config;
  }
}
