package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-010: *-common module must be listed first in parent &lt;modules&gt;.
 *
 * <p>The common module contains shared constants, enums, and utilities that other modules depend
 * on. It must be built first to ensure proper dependency resolution.
 *
 * <p>This fixer:
 *
 * <ol>
 *   <li>Reads the parent pom.xml
 *   <li>Finds the *-common module in &lt;modules&gt; section
 *   <li>Moves it to the first position
 *   <li>Writes the updated pom.xml
 * </ol>
 */
public class CommonModuleOrderRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-010"};
  private static final int PRIORITY = 85; // High priority - structural fix
  private static final Pattern MODULES_PATTERN =
      Pattern.compile("(<modules>)(.*?)(</modules>)", Pattern.DOTALL);
  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");

  public CommonModuleOrderRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "CommonModuleOrderRemediator";
  }

  @Override
  public String getDescription() {
    return "Moves *-common module to first position in parent <modules> (MS-010)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!"MS-010".equals(violation.ruleId())) {
      return FixResult.skipped(violation, "Not an MS-010 violation");
    }

    try {
      Path pomPath = context.moduleRoot().resolve("pom.xml");
      if (!Files.exists(pomPath)) {
        return FixResult.skipped(violation, "No pom.xml found");
      }

      String pomContent = readFile(pomPath);

      // Find <modules> section
      Matcher modulesMatcher = MODULES_PATTERN.matcher(pomContent);
      if (!modulesMatcher.find()) {
        return FixResult.skipped(violation, "No <modules> section found in pom.xml");
      }

      String modulesSection = modulesMatcher.group(2);

      // Extract all module names
      List<String> modules = new ArrayList<>();
      Matcher moduleMatcher = MODULE_PATTERN.matcher(modulesSection);
      while (moduleMatcher.find()) {
        modules.add(moduleMatcher.group(1));
      }

      if (modules.isEmpty()) {
        return FixResult.skipped(violation, "No modules found in <modules> section");
      }

      // Find common module
      String commonModule = null;
      int commonIndex = -1;
      for (int i = 0; i < modules.size(); i++) {
        String module = modules.get(i);
        if (module.endsWith("-common")
            || module.equals("common")
            || module.endsWith("common") && !module.contains("-")) {
          commonModule = module;
          commonIndex = i;
          break;
        }
      }

      if (commonModule == null) {
        return FixResult.skipped(violation, "No *-common module found");
      }

      if (commonIndex == 0) {
        return FixResult.skipped(violation, "Common module is already first");
      }

      // Move common module to first position
      modules.remove(commonIndex);
      modules.add(0, commonModule);

      // Rebuild modules section with proper formatting
      StringBuilder newModulesContent = new StringBuilder("\n");
      for (String module : modules) {
        newModulesContent.append("        <module>").append(module).append("</module>\n");
      }
      newModulesContent.append("    ");

      // Replace in pom content
      String newPomContent =
          modulesMatcher.replaceFirst(
              "$1" + Matcher.quoteReplacement(newModulesContent.toString()) + "$3");

      // Write back
      if (context.dryRun()) {
        return FixResult.dryRun(
            violation,
            List.of(pomPath),
            "Would move " + commonModule + " to first position in <modules>",
            List.of());
      }

      writeFile(pomPath, newPomContent);

      return FixResult.success(
          violation, List.of(pomPath), "Moved " + commonModule + " to first position in <modules>");

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to reorder modules: " + e.getMessage());
    }
  }
}
