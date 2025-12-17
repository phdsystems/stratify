package dev.engineeringlab.stratify.structure.scanner.common.report;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.engineeringlab.stratify.structure.scanner.common.model.ComplianceResult;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generates and saves compliance reports in JSON format.
 *
 * <p>Supports consolidated reporting where results from multiple modules are merged into a single
 * JSON file.
 */
public class JsonReporter {

  private static final int ONE_HUNDRED = 100;

  private static final DateTimeFormatter DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  // Global lock map for file-based synchronization across JVM instances
  private static final Map<String, Lock> FILE_LOCKS = new HashMap<>();

  private final Gson gson;

  public JsonReporter() {
    this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
  }

  /**
   * Resets (deletes) an existing report file to ensure a fresh start.
   *
   * <p>This should be called at the beginning of a scan run (on the first/root module) to ensure
   * stale data from previous runs is not preserved.
   *
   * @param outputPath the output file path for the JSON report
   * @throws IOException if file deletion fails
   */
  public void resetReport(String outputPath) throws IOException {
    Path reportPath = Paths.get(outputPath);

    Lock lock = getFileLock(outputPath);
    lock.lock();
    try {
      synchronized (JsonReporter.class) {
        Files.deleteIfExists(reportPath);
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Saves a compliance report to a JSON file.
   *
   * @param result the compliance validation result
   * @param moduleName the name of the module being validated
   * @param modulePath the path to the module
   * @param outputPath the output file path for the JSON report
   * @param consolidate whether to consolidate with existing report
   * @throws IOException if file operations fail
   */
  public void saveReport(
      ComplianceResult result,
      String moduleName,
      String modulePath,
      String outputPath,
      boolean consolidate)
      throws IOException {

    // Add timestamp to filename for better tracking
    Path reportPath = addTimestampToPath(outputPath, consolidate);

    // Ensure parent directory exists
    Path parentDir = reportPath.getParent();
    if (parentDir != null) {
      Files.createDirectories(parentDir);
    }

    // For consolidated reports, use file locking to prevent concurrent write conflicts
    if (consolidate) {
      Lock lock = getFileLock(outputPath);
      lock.lock();
      try {
        synchronized (JsonReporter.class) {
          if (Files.exists(reportPath)) {
            // Merge with existing report
            mergeAndSaveReport(result, moduleName, modulePath, reportPath);
          } else {
            // Create new report
            createNewReport(result, moduleName, modulePath, reportPath, consolidate);
          }
        }
      } finally {
        lock.unlock();
      }
    } else {
      // Non-consolidated: no locking needed
      createNewReport(result, moduleName, modulePath, reportPath, consolidate);
    }
  }

  /** Gets or creates a lock for the specified file path. */
  private static synchronized Lock getFileLock(String filePath) {
    return FILE_LOCKS.computeIfAbsent(filePath, k -> new ReentrantLock());
  }

  /**
   * Adds a timestamp to the file path to create unique timestamped reports. For consolidated
   * reports, uses the base filename. For non-consolidated reports, appends timestamp before the
   * extension.
   *
   * @param outputPath the original output path
   * @param consolidate whether this is a consolidated report
   * @return the path with timestamp added
   */
  private Path addTimestampToPath(String outputPath, boolean consolidate) {
    Path original = Paths.get(outputPath);

    // For consolidated reports, don't add timestamp - use a single file
    if (consolidate) {
      return original;
    }

    // For non-consolidated reports, add timestamp
    Path fileNamePath = original.getFileName();
    if (fileNamePath == null) {
      return original;
    }
    String fileName = fileNamePath.toString();
    String timestamp =
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

    // Insert timestamp before extension
    int dotIndex = fileName.lastIndexOf('.');
    String newFileName;
    if (dotIndex > 0) {
      String nameWithoutExt = fileName.substring(0, dotIndex);
      String ext = fileName.substring(dotIndex);
      newFileName = nameWithoutExt + "_" + timestamp + ext;
    } else {
      newFileName = fileName + "_" + timestamp;
    }

    Path parentPath = original.getParent();
    return parentPath != null ? parentPath.resolve(newFileName) : Paths.get(newFileName);
  }

  /** Creates a new JSON report file. */
  private void createNewReport(
      ComplianceResult result,
      String moduleName,
      String modulePath,
      Path reportPath,
      boolean consolidate)
      throws IOException {

    Map<String, Object> report = new HashMap<>();
    report.put("generatedAt", LocalDateTime.now().format(DATE_FORMATTER));
    report.put("consolidatedReport", consolidate);

    // Add module result
    Map<String, Object> moduleReport = buildModuleReport(result, moduleName, modulePath);

    if (consolidate) {
      // For consolidated reports, use modules array
      List<Map<String, Object>> modules = new ArrayList<>();
      modules.add(moduleReport);
      report.put("modules", modules);

      // Add summary
      report.put("summary", buildSummary(List.of(moduleReport)));
    } else {
      // For single module reports, include module data at root level
      report.putAll(moduleReport);
    }

    String json = gson.toJson(report);
    Files.writeString(reportPath, json);
  }

  /** Merges the current result with an existing consolidated report. */
  private void mergeAndSaveReport(
      ComplianceResult result, String moduleName, String modulePath, Path reportPath)
      throws IOException {

    // Read existing report
    String existingJson = Files.readString(reportPath);
    JsonObject existingReport = JsonParser.parseString(existingJson).getAsJsonObject();

    // Convert to map for easier manipulation
    Map<String, Object> report = gson.fromJson(existingReport, Map.class);

    // Update timestamp
    report.put("generatedAt", LocalDateTime.now().format(DATE_FORMATTER));
    report.put("consolidatedReport", true);

    // Get or create modules list
    List<Map<String, Object>> modules =
        (List<Map<String, Object>>) report.computeIfAbsent("modules", k -> new ArrayList<>());

    // Remove existing entry for this module if present
    modules.removeIf(m -> moduleName.equals(m.get("moduleName")));

    // Add new module result
    Map<String, Object> moduleReport = buildModuleReport(result, moduleName, modulePath);
    modules.add(moduleReport);

    // Update summary
    report.put("summary", buildSummary(modules));

    // Save merged report
    String json = gson.toJson(report);
    Files.writeString(reportPath, json);
  }

  /** Builds a module-specific report structure. */
  private Map<String, Object> buildModuleReport(
      ComplianceResult result, String moduleName, String modulePath) {
    Map<String, Object> moduleReport = new HashMap<>();
    moduleReport.put("moduleName", moduleName);
    moduleReport.put("modulePath", modulePath);
    moduleReport.put("executionTimeMs", result.getExecutionTimeMs());
    moduleReport.put("complianceScore", result.getComplianceScore());
    moduleReport.put("errorCount", result.getErrorCount());
    moduleReport.put("warningCount", result.getWarningCount());
    moduleReport.put("passedCount", result.getPassedCount());
    moduleReport.put("totalCount", result.getTotalCount());
    moduleReport.put("compliant", result.isCompliant(false));

    // Add violations grouped by severity
    Map<String, List<Map<String, Object>>> violationsBySeverity = new HashMap<>();
    violationsBySeverity.put("errors", serializeViolations(result.getErrors()));
    violationsBySeverity.put("warnings", serializeViolations(result.getWarnings()));
    violationsBySeverity.put("passed", serializeViolations(result.getPassed()));

    moduleReport.put("violations", violationsBySeverity);

    return moduleReport;
  }

  /** Serializes a list of violations to maps. */
  private List<Map<String, Object>> serializeViolations(List<Violation> violations) {
    List<Map<String, Object>> serialized = new ArrayList<>();

    for (Violation violation : violations) {
      Map<String, Object> v = new HashMap<>();
      v.put("ruleId", violation.getRuleId());
      v.put(
          "severity", violation.getSeverity() != null ? violation.getSeverity().toString() : null);
      v.put(
          "category",
          violation.getCategory() != null ? violation.getCategory().getDisplayName() : null);
      v.put("description", violation.getDescription());
      v.put("location", violation.getLocation());
      v.put("expected", violation.getExpected());
      v.put("found", violation.getFound());
      v.put("reason", violation.getReason());
      v.put("fix", violation.getFix());
      v.put("reference", violation.getReference());
      v.put("passed", violation.isPassed());

      serialized.add(v);
    }

    return serialized;
  }

  /** Builds a summary from all module reports. */
  private Map<String, Object> buildSummary(List<Map<String, Object>> modules) {
    Map<String, Object> summary = new HashMap<>();

    int totalModules = modules.size();
    int totalErrors = 0;
    int totalWarnings = 0;
    int totalPassed = 0;
    int totalCount = 0;

    for (Map<String, Object> module : modules) {
      totalErrors += ((Number) module.get("errorCount")).intValue();
      totalWarnings += ((Number) module.get("warningCount")).intValue();
      totalPassed += ((Number) module.get("passedCount")).intValue();
      totalCount += ((Number) module.get("totalCount")).intValue();
    }

    int overallComplianceScore =
        totalCount > 0 ? (int) ((double) totalPassed / totalCount * ONE_HUNDRED) : ONE_HUNDRED;

    summary.put("totalModules", totalModules);
    summary.put("totalErrors", totalErrors);
    summary.put("totalWarnings", totalWarnings);
    summary.put("totalPassed", totalPassed);
    summary.put("totalCount", totalCount);
    summary.put("overallComplianceScore", overallComplianceScore);
    summary.put("overallCompliant", totalErrors == 0);

    return summary;
  }
}
