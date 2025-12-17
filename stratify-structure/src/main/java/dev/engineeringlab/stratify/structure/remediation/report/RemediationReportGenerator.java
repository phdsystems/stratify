package dev.engineeringlab.stratify.structure.remediation.report;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import dev.engineeringlab.stratify.structure.remediation.model.StructureFixResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates comprehensive remediation reports in JSON format.
 *
 * <p>This class produces detailed reports including:
 *
 * <ul>
 *   <li>All modified file paths
 *   <li>Individual fix results with status, description, and diffs
 *   <li>Violation details (rule category, expected/found, suggested fix)
 *   <li>Backup file locations
 *   <li>Error messages and failure reasons
 * </ul>
 */
public class RemediationReportGenerator {

  private static final String REPORT_DIR = ".remediation/reports";

  /**
   * Generate and save a comprehensive remediation report.
   *
   * @param context Report context containing all remediation data
   * @return Path to the saved report
   */
  public Path generateAndSave(RemediationReportContext context) {
    String json = generate(context);
    Path reportPath = getReportPath(context.projectPath(), context.timestamp());
    saveReport(json, reportPath);
    System.out.println("Remediation report saved to: " + reportPath);
    return reportPath;
  }

  /** Generate report content without saving. */
  public String generate(RemediationReportContext context) {
    Map<String, Object> report = buildReport(context);
    return toJson(report);
  }

  private Map<String, Object> buildReport(RemediationReportContext context) {
    Map<String, Object> report = new LinkedHashMap<>();

    // Metadata
    report.put("version", "1.0.0");
    report.put("timestamp", context.timestamp().toString());
    report.put("projectPath", context.projectPath().toAbsolutePath().toString());
    report.put("durationMs", context.durationMs());

    // Overall status
    report.put("status", determineOverallStatus(context));

    // Summary statistics
    report.put("summary", buildSummary(context));

    // Detailed fix results grouped by status
    report.put("fixes", buildFixesSection(context.fixes()));

    // Modified files list
    report.put("filesModified", context.filesModified());

    // Verification results
    if (context.verification() != null) {
      report.put("verification", buildVerificationSection(context.verification()));
    }

    // Violations by rule
    report.put("violationsByRule", groupViolationsByRule(context.fixes()));

    return report;
  }

  private Map<String, Object> buildSummary(RemediationReportContext context) {
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("totalAttempted", context.totalAttempted());
    summary.put("successful", context.successCount());
    summary.put("failed", context.failedCount());
    summary.put("skipped", context.skippedCount());
    summary.put("filesModifiedCount", context.filesModified().size());

    // Calculate percentages
    if (context.totalAttempted() > 0) {
      double successRate = (double) context.successCount() / context.totalAttempted() * 100;
      summary.put("successRate", String.format("%.1f%%", successRate));
    } else {
      summary.put("successRate", "N/A");
    }

    return summary;
  }

  private Map<String, Object> buildFixesSection(List<StructureFixResult> fixes) {
    Map<String, Object> fixesSection = new LinkedHashMap<>();

    // Group by status
    Map<FixStatus, List<StructureFixResult>> byStatus =
        fixes.stream().collect(Collectors.groupingBy(StructureFixResult::status));

    // Successful fixes
    List<Map<String, Object>> successful = new ArrayList<>();
    if (byStatus.containsKey(FixStatus.FIXED)) {
      for (StructureFixResult fix : byStatus.get(FixStatus.FIXED)) {
        successful.add(buildFixDetail(fix));
      }
    }
    fixesSection.put("successful", successful);

    // Failed fixes
    List<Map<String, Object>> failed = new ArrayList<>();
    for (FixStatus status :
        List.of(FixStatus.FAILED, FixStatus.PARSE_ERROR, FixStatus.VALIDATION_FAILED)) {
      if (byStatus.containsKey(status)) {
        for (StructureFixResult fix : byStatus.get(status)) {
          failed.add(buildFixDetail(fix));
        }
      }
    }
    fixesSection.put("failed", failed);

    // Skipped fixes
    List<Map<String, Object>> skipped = new ArrayList<>();
    for (FixStatus status : List.of(FixStatus.SKIPPED, FixStatus.NOT_FIXABLE)) {
      if (byStatus.containsKey(status)) {
        for (StructureFixResult fix : byStatus.get(status)) {
          skipped.add(buildFixDetail(fix));
        }
      }
    }
    fixesSection.put("skipped", skipped);

    // Dry run results
    List<Map<String, Object>> dryRun = new ArrayList<>();
    if (byStatus.containsKey(FixStatus.DRY_RUN)) {
      for (StructureFixResult fix : byStatus.get(FixStatus.DRY_RUN)) {
        dryRun.add(buildFixDetail(fix));
      }
    }
    if (!dryRun.isEmpty()) {
      fixesSection.put("dryRun", dryRun);
    }

    return fixesSection;
  }

  private Map<String, Object> buildFixDetail(StructureFixResult fix) {
    Map<String, Object> detail = new LinkedHashMap<>();

    // Fix metadata
    detail.put("status", fix.status().name());
    detail.put("description", fix.description());

    // Violation details
    StructureViolation v = fix.violation();
    Map<String, Object> violation = new LinkedHashMap<>();
    violation.put("ruleId", v.ruleId());
    violation.put("ruleName", v.ruleName());
    violation.put("message", v.message());
    violation.put("severity", v.severity().name());
    violation.put("category", v.category().name());

    if (v.location() != null && !v.location().isEmpty()) {
      violation.put("location", v.location());
    }
    if (v.fix() != null && !v.fix().isEmpty()) {
      violation.put("suggestedFix", v.fix());
    }
    if (v.reference() != null && !v.reference().isEmpty()) {
      violation.put("reference", v.reference());
    }

    detail.put("violation", violation);

    // Modified files
    if (fix.modifiedFiles() != null && !fix.modifiedFiles().isEmpty()) {
      detail.put("modifiedFiles", fix.modifiedFiles().stream().map(Path::toString).toList());
    }

    // Diffs (if available)
    if (fix.diffs() != null && !fix.diffs().isEmpty()) {
      detail.put("diffs", fix.diffs());
    }

    // Error message (if failed)
    if (fix.errorMessage() != null) {
      detail.put("errorMessage", fix.errorMessage());
    }

    return detail;
  }

  private Map<String, Object> buildVerificationSection(VerificationResult verification) {
    Map<String, Object> section = new LinkedHashMap<>();
    section.put("compileSuccess", verification.compileSuccess());
    section.put("testSuccess", verification.testSuccess());
    section.put("testsRun", verification.testsRun());
    section.put("testsFailed", verification.testsFailed());

    if (verification.compileOutput() != null && !verification.compileOutput().isEmpty()) {
      section.put("compileOutput", verification.compileOutput());
    }

    if (verification.testOutput() != null && !verification.testOutput().isEmpty()) {
      section.put("testOutput", verification.testOutput());
    }

    // Rollback info
    Map<String, Object> rollback = new LinkedHashMap<>();
    rollback.put("performed", verification.rollbackPerformed());
    rollback.put("filesRestored", verification.filesRestored());
    if (verification.rollbackReason() != null) {
      rollback.put("reason", verification.rollbackReason());
    }
    section.put("rollback", rollback);

    return section;
  }

  private Map<String, Map<String, Object>> groupViolationsByRule(List<StructureFixResult> fixes) {
    Map<String, List<StructureFixResult>> byRule =
        fixes.stream().collect(Collectors.groupingBy(f -> f.violation().ruleId()));

    Map<String, Map<String, Object>> ruleStats = new LinkedHashMap<>();

    for (Map.Entry<String, List<StructureFixResult>> entry : byRule.entrySet()) {
      String ruleId = entry.getKey();
      List<StructureFixResult> ruleFixes = entry.getValue();

      Map<String, Object> stats = new LinkedHashMap<>();
      stats.put("total", ruleFixes.size());
      stats.put("fixed", ruleFixes.stream().filter(f -> f.status() == FixStatus.FIXED).count());
      stats.put("failed", ruleFixes.stream().filter(StructureFixResult::isFailed).count());
      stats.put(
          "skipped",
          ruleFixes.stream()
              .filter(f -> f.status() == FixStatus.SKIPPED || f.status() == FixStatus.NOT_FIXABLE)
              .count());

      // First violation message as example
      if (!ruleFixes.isEmpty()) {
        stats.put("exampleMessage", truncate(ruleFixes.get(0).violation().message(), 150));
      }

      ruleStats.put(ruleId, stats);
    }

    return ruleStats;
  }

  private String determineOverallStatus(RemediationReportContext context) {
    VerificationResult verification = context.verification();
    if (verification != null) {
      if (!verification.compileSuccess()) {
        return "COMPILE_FAILED";
      }
      if (!verification.testSuccess()) {
        return "TEST_FAILED";
      }
      if (verification.rollbackPerformed()) {
        return "ROLLED_BACK";
      }
    }

    if (context.failedCount() > 0) {
      return "PARTIAL_SUCCESS";
    }

    if (context.successCount() == 0 && context.skippedCount() > 0) {
      return "ALL_SKIPPED";
    }

    if (context.successCount() == context.totalAttempted()) {
      return "SUCCESS";
    }

    return "COMPLETED";
  }

  private Path getReportPath(Path projectPath, Instant timestamp) {
    String filename = "remediation-" + timestamp.toString().replace(":", "-") + ".json";
    return projectPath.resolve(REPORT_DIR).resolve(filename);
  }

  private void saveReport(String content, Path reportPath) {
    try {
      Files.createDirectories(reportPath.getParent());
      Files.writeString(reportPath, content);
    } catch (IOException e) {
      throw new RuntimeException("Failed to save remediation report", e);
    }
  }

  private String truncate(String text, int maxLength) {
    if (text == null) return null;
    if (text.length() <= maxLength) return text;
    return text.substring(0, maxLength - 3) + "...";
  }

  /** Simple JSON serialization without external dependencies. */
  private String toJson(Map<String, Object> map) {
    StringBuilder sb = new StringBuilder();
    toJson(map, sb, 0);
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private void toJson(Object obj, StringBuilder sb, int indent) {
    String indentStr = "  ".repeat(indent);
    String nextIndent = "  ".repeat(indent + 1);

    if (obj == null) {
      sb.append("null");
    } else if (obj instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) obj;
      sb.append("{\n");
      boolean first = true;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        if (!first) sb.append(",\n");
        first = false;
        sb.append(nextIndent).append("\"").append(escapeJson(entry.getKey())).append("\": ");
        toJson(entry.getValue(), sb, indent + 1);
      }
      sb.append("\n").append(indentStr).append("}");
    } else if (obj instanceof List) {
      List<?> list = (List<?>) obj;
      if (list.isEmpty()) {
        sb.append("[]");
      } else {
        sb.append("[\n");
        boolean first = true;
        for (Object item : list) {
          if (!first) sb.append(",\n");
          first = false;
          sb.append(nextIndent);
          toJson(item, sb, indent + 1);
        }
        sb.append("\n").append(indentStr).append("]");
      }
    } else if (obj instanceof String) {
      sb.append("\"").append(escapeJson((String) obj)).append("\"");
    } else if (obj instanceof Number || obj instanceof Boolean) {
      sb.append(obj);
    } else {
      sb.append("\"").append(escapeJson(obj.toString())).append("\"");
    }
  }

  private String escapeJson(String s) {
    if (s == null) return "";
    return s.replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  /** Context record containing all data needed for report generation. */
  public record RemediationReportContext(
      Instant timestamp,
      Path projectPath,
      long durationMs,
      int totalAttempted,
      int successCount,
      int failedCount,
      int skippedCount,
      List<StructureFixResult> fixes,
      List<String> filesModified,
      VerificationResult verification) {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private Instant timestamp = Instant.now();
      private Path projectPath;
      private long durationMs;
      private int totalAttempted;
      private int successCount;
      private int failedCount;
      private int skippedCount;
      private List<StructureFixResult> fixes = List.of();
      private List<String> filesModified = List.of();
      private VerificationResult verification;

      public Builder timestamp(Instant timestamp) {
        this.timestamp = timestamp;
        return this;
      }

      public Builder projectPath(Path path) {
        this.projectPath = path;
        return this;
      }

      public Builder durationMs(long ms) {
        this.durationMs = ms;
        return this;
      }

      public Builder totalAttempted(int count) {
        this.totalAttempted = count;
        return this;
      }

      public Builder successCount(int count) {
        this.successCount = count;
        return this;
      }

      public Builder failedCount(int count) {
        this.failedCount = count;
        return this;
      }

      public Builder skippedCount(int count) {
        this.skippedCount = count;
        return this;
      }

      public Builder fixes(List<StructureFixResult> fixes) {
        this.fixes = fixes;
        return this;
      }

      public Builder filesModified(List<String> files) {
        this.filesModified = files;
        return this;
      }

      public Builder verification(VerificationResult result) {
        this.verification = result;
        return this;
      }

      public RemediationReportContext build() {
        return new RemediationReportContext(
            timestamp,
            projectPath,
            durationMs,
            totalAttempted,
            successCount,
            failedCount,
            skippedCount,
            fixes,
            filesModified,
            verification);
      }
    }
  }

  /** Verification result record for compile/test status. */
  public record VerificationResult(
      boolean compileSuccess,
      boolean testSuccess,
      int testsRun,
      int testsFailed,
      String compileOutput,
      String testOutput,
      boolean rollbackPerformed,
      int filesRestored,
      String rollbackReason) {
    public static VerificationResult success() {
      return new VerificationResult(true, true, 0, 0, null, null, false, 0, null);
    }

    public static VerificationResult compileFailed(String output) {
      return new VerificationResult(false, false, 0, 0, output, null, false, 0, null);
    }

    public static VerificationResult withRollback(String reason, int filesRestored) {
      return new VerificationResult(false, false, 0, 0, null, null, true, filesRestored, reason);
    }

    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private boolean compileSuccess = true;
      private boolean testSuccess = true;
      private int testsRun = 0;
      private int testsFailed = 0;
      private String compileOutput;
      private String testOutput;
      private boolean rollbackPerformed = false;
      private int filesRestored = 0;
      private String rollbackReason;

      public Builder compileSuccess(boolean success) {
        this.compileSuccess = success;
        return this;
      }

      public Builder testSuccess(boolean success) {
        this.testSuccess = success;
        return this;
      }

      public Builder testsRun(int count) {
        this.testsRun = count;
        return this;
      }

      public Builder testsFailed(int count) {
        this.testsFailed = count;
        return this;
      }

      public Builder compileOutput(String output) {
        this.compileOutput = output;
        return this;
      }

      public Builder testOutput(String output) {
        this.testOutput = output;
        return this;
      }

      public Builder rollbackPerformed(boolean performed) {
        this.rollbackPerformed = performed;
        return this;
      }

      public Builder filesRestored(int count) {
        this.filesRestored = count;
        return this;
      }

      public Builder rollbackReason(String reason) {
        this.rollbackReason = reason;
        return this;
      }

      public VerificationResult build() {
        return new VerificationResult(
            compileSuccess,
            testSuccess,
            testsRun,
            testsFailed,
            compileOutput,
            testOutput,
            rollbackPerformed,
            filesRestored,
            rollbackReason);
      }
    }
  }
}
