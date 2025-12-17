package dev.engineeringlab.stratify.structure.scanner.common.report;

import dev.engineeringlab.stratify.structure.scanner.common.model.ComplianceResult;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.PluginInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.maven.plugin.logging.Log;

/** Reports compliance results to console. */
public class ConsoleReporter {

  private final Log log;

  public ConsoleReporter(Log logParam) {
    this.log = logParam;
  }

  /** Prints module structure information. */
  public void printModuleStructure(ModuleInfo moduleInfo) {
    log.info("");
    log.info("╔════════════════════════════════════════════════════════════╗");
    log.info("║   Module Structure - " + moduleInfo.getModuleName() + "                     ║");
    log.info("╚════════════════════════════════════════════════════════════╝");
    log.info("");

    log.info("Module Name: " + moduleInfo.getModuleName());
    log.info("Base Path: " + moduleInfo.getBasePath());
    log.info("Is Parent: " + moduleInfo.isParent());
    log.info("");

    // Print code quality plugins for parent module
    printCodeQualityPlugins(moduleInfo.getCodeQualityPlugins(), "Parent Module");

    log.info("Sub-Modules:");
    boolean isStandalone = moduleInfo.isStandaloneModule();

    moduleInfo
        .getSubModules()
        .forEach(
            (type, subModule) -> {
              // For standalone modules (core-only or common-only), only show existing modules
              // Don't show api/spi/facade as "Not found" since they're optional
              if (isStandalone && !subModule.isExists()) {
                return; // Skip missing modules for standalone modules
              }

              String status = subModule.isExists() ? "✅ Found" : "❌ Not found";
              log.info("  " + type + ": " + status);
              if (subModule.isExists()) {
                log.info("    Path: " + subModule.getPath());
                log.info("    ArtifactId: " + subModule.getArtifactId());
                log.info("    Java Files: " + subModule.getJavaSourceFiles().size());
                if (subModule.getExceptionPackagePath() != null) {
                  log.info("    Exception Package: ✅ Found");
                }
                // Print code quality plugins for sub-module if any
                if (!subModule.getCodeQualityPlugins().isEmpty()) {
                  printCodeQualityPlugins(subModule.getCodeQualityPlugins(), "    ");
                }
              }
            });

    String standaloneType = moduleInfo.getStandaloneModuleType();
    if (standaloneType != null) {
      log.info("");
      log.info("  ℹ️  Standalone " + standaloneType + " module (full facade pattern not required)");
    }

    log.info("");
  }

  /** Prints code quality plugins found in the module. */
  private void printCodeQualityPlugins(List<PluginInfo> plugins, String prefix) {
    if (plugins.isEmpty()) {
      return;
    }

    log.info("");
    log.info(prefix + " Code Quality Plugins:");

    // Group plugins by category
    Map<String, List<PluginInfo>> pluginsByCategory =
        plugins.stream().collect(Collectors.groupingBy(PluginInfo::getCategory));

    pluginsByCategory.forEach(
        (category, categoryPlugins) -> {
          log.info(prefix + "  [" + category + "]");
          categoryPlugins.forEach(
              plugin -> {
                String indent = prefix.equals("Parent Module") ? "    " : "      ";
                log.info(indent + "• " + plugin.getDisplayName());
                if (plugin.getVersion() != null) {
                  log.info(indent + "  Version: " + plugin.getVersion());
                }
                log.info(indent + "  Coordinates: " + plugin.getCoordinates());
              });
        });
  }

  /** Prints compliance validation results. */
  public void printResults(ComplianceResult result) {
    log.info("");
    log.info("═══════════════════════════════════════════════════════════");
    log.info("  Compliance Validation Results");
    log.info("═══════════════════════════════════════════════════════════");
    log.info("");

    // Print violations by category
    printViolationsByCategory(result.getErrors(), "ERRORS");
    printViolationsByCategory(result.getWarnings(), "WARNINGS");

    // Print summary
    printSummary(result);
  }

  /** Prints violations grouped by category. */
  private void printViolationsByCategory(List<Violation> violations, String title) {
    if (violations.isEmpty()) {
      return;
    }

    log.info("");
    log.info("━━━ " + title + " ━━━");
    log.info("");

    for (Violation violation : violations) {
      log.info(
          violation.getSeverity().getIcon()
              + " "
              + violation.getRuleId()
              + ": "
              + violation.getDescription());
      if (violation.getLocation() != null) {
        log.info("  Location: " + violation.getLocation());
      }
      if (violation.getExpected() != null) {
        log.info("  Expected: " + violation.getExpected());
      }
      if (violation.getFound() != null) {
        log.info("  Found: " + violation.getFound());
      }
      if (violation.getReason() != null) {
        log.info("  Reason: " + violation.getReason());
      }
      if (violation.getFix() != null) {
        log.info("  Fix: " + violation.getFix());
      }
      if (violation.getReference() != null) {
        log.info("  Reference: " + violation.getReference());
      }
      log.info("");
    }
  }

  /** Prints compliance summary. */
  private void printSummary(ComplianceResult result) {
    log.info("");
    log.info("═══════════════════════════════════════════════════════════");
    log.info("  Summary");
    log.info("═══════════════════════════════════════════════════════════");
    log.info("");

    log.info("Total Rules Checked:   " + result.getTotalCount());
    log.info("Passed:                " + result.getPassedCount() + " ✅");
    log.info("Warnings:              " + result.getWarningCount() + " ⚠️");
    log.info("Errors:                " + result.getErrorCount() + " ❌");
    log.info("");

    log.info("Compliance Score:      " + result.getComplianceScore() + "%");
    log.info("");

    if (result.isCompliant(false)) {
      log.info("Result: ✅ COMPLIANT");
    } else {
      log.info("Result: ❌ NOT COMPLIANT");
    }

    log.info("═══════════════════════════════════════════════════════════");
    log.info("");
  }
}
