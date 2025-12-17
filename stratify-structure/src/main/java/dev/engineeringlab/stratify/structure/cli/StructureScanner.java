package dev.engineeringlab.stratify.structure.cli;

import dev.engineeringlab.stratify.structure.backup.api.BackupManager;
import dev.engineeringlab.stratify.structure.backup.facade.Backups;
import dev.engineeringlab.stratify.structure.model.Category;
import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.model.Severity;
import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import dev.engineeringlab.stratify.structure.remediation.model.StructureFixResult;
import dev.engineeringlab.stratify.structure.remediation.report.RemediationReportGenerator;
import dev.engineeringlab.stratify.structure.remediation.report.RemediationReportGenerator.RemediationReportContext;
import dev.engineeringlab.stratify.structure.remediation.report.RemediationReportGenerator.VerificationResult;
import dev.engineeringlab.stratify.structure.scanner.ModuleScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * CLI for scanning project structure for SEA-4 compliance.
 *
 * <p>Usage:
 *
 * <pre>
 * StructureScanner scan /path/to/project
 * StructureScanner remediate /path/to/project [--apply]
 * </pre>
 */
public final class StructureScanner {

  public static void main(String[] args) {
    if (args.length == 0) {
      printUsage();
      System.exit(1);
    }

    String command = args[0];

    // Handle legacy usage (just path)
    if (!command.equals("scan") && !command.equals("remediate")) {
      Path projectRoot = Paths.get(args[0]);
      if (Files.isDirectory(projectRoot)) {
        try {
          new StructureScanner().scan(projectRoot);
        } catch (Exception e) {
          System.err.println("Error: " + e.getMessage());
          System.exit(1);
        }
        return;
      }
      printUsage();
      System.exit(1);
    }

    if (args.length < 2) {
      printUsage();
      System.exit(1);
    }

    Path projectRoot = Paths.get(args[1]);
    if (!Files.isDirectory(projectRoot)) {
      System.err.println("Error: Not a directory: " + projectRoot);
      System.exit(1);
    }

    boolean apply = args.length > 2 && args[2].equals("--apply");

    try {
      StructureScanner scanner = new StructureScanner();
      if (command.equals("scan")) {
        scanner.scan(projectRoot);
      } else if (command.equals("remediate")) {
        scanner.remediate(projectRoot, apply);
      }
    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  private static void printUsage() {
    System.err.println("Usage:");
    System.err.println("  StructureScanner scan <project-path>");
    System.err.println("  StructureScanner remediate <project-path> [--apply]");
  }

  public void scan(Path projectRoot) throws Exception {
    System.out.println("=".repeat(80));
    System.out.println("Stratify Structure Scanner");
    System.out.println("=".repeat(80));
    System.out.println("Scanning: " + projectRoot.toAbsolutePath());
    System.out.println();

    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    System.out.println("DISCOVERED MODULES");
    System.out.println("-".repeat(80));

    List<String> violations = new ArrayList<>();

    if (modules.isEmpty()) {
      System.out.println("No stratified modules found.");
    } else {
      for (ModuleInfo module : modules) {
        System.out.printf("Module: %s%n", module.baseName());
        System.out.printf("  Path: %s%n", module.path());
        System.out.printf(
            "  Layers: api=%s, core=%s, facade=%s, spi=%s, common=%s, util=%s%n",
            module.hasApi() ? "Y" : "-",
            module.hasCore() ? "Y" : "-",
            module.hasFacade() ? "Y" : "-",
            module.hasSpi() ? "Y" : "-",
            module.hasCommon() ? "Y" : "-",
            module.hasUtil() ? "Y" : "-");
        System.out.printf(
            "  Complete: %s | SEA-4: %s%n",
            module.isComplete() ? "Yes" : "No", module.isSea4Compliant() ? "Yes" : "No");
        System.out.println();

        // Collect violations
        if (!module.hasApi()) {
          violations.add("[SS-001] " + module.baseName() + ": Missing -api module");
        }
        if (!module.hasCore()) {
          violations.add("[SS-002] " + module.baseName() + ": Missing -core module");
        }
        if (module.hasCommon()) {
          violations.add("[SS-006] " + module.baseName() + ": Has forbidden -common layer");
        }
        if (module.hasUtil()) {
          violations.add("[SS-007] " + module.baseName() + ": Has -util module (anti-pattern)");
        }
      }
    }

    System.out.println("VIOLATIONS");
    System.out.println("-".repeat(80));

    if (violations.isEmpty()) {
      System.out.println("None - project is SEA-4 compliant!");
    } else {
      violations.forEach(System.out::println);
    }

    System.out.println();
    System.out.println("=".repeat(80));
    System.out.printf("Summary: %d modules, %d violations%n", modules.size(), violations.size());
    System.out.println("=".repeat(80));

    if (!violations.isEmpty()) {
      System.exit(1);
    }
  }

  public void remediate(Path projectRoot, boolean apply) throws Exception {
    Instant startTime = Instant.now();
    long startMs = System.currentTimeMillis();

    System.out.println("=".repeat(80));
    System.out.println("Stratify Structure Remediation");
    System.out.println("=".repeat(80));
    System.out.println("Project: " + projectRoot.toAbsolutePath());
    System.out.println("Mode: " + (apply ? "APPLY" : "DRY-RUN"));
    System.out.println();

    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);
    List<StructureFixResult> results = new ArrayList<>();
    List<String> filesModified = new ArrayList<>();
    BackupManager backupManager = Backups.createManager(projectRoot);

    for (ModuleInfo module : modules) {
      if (module.hasCommon()) {
        List<StructureFixResult> commonResults =
            remediateCommonLayer(module, apply, backupManager, filesModified);
        results.addAll(commonResults);
      }
      if (module.hasUtil()) {
        List<StructureFixResult> utilResults =
            remediateUtilLayer(module, apply, backupManager, filesModified);
        results.addAll(utilResults);
      }
    }

    long durationMs = System.currentTimeMillis() - startMs;

    // Calculate statistics
    int successCount = (int) results.stream().filter(r -> r.status() == FixStatus.FIXED).count();
    int failedCount = (int) results.stream().filter(StructureFixResult::isFailed).count();
    int skippedCount =
        (int)
            results.stream()
                .filter(r -> r.status() == FixStatus.SKIPPED || r.status() == FixStatus.NOT_FIXABLE)
                .count();
    int dryRunCount = (int) results.stream().filter(r -> r.status() == FixStatus.DRY_RUN).count();

    // Print summary
    System.out.println("=".repeat(80));
    if (apply) {
      System.out.printf("Remediation complete: %d actions applied%n", successCount);
    } else {
      System.out.printf("Dry-run complete: %d actions planned%n", dryRunCount);
      System.out.println("Run with --apply to execute changes");
    }
    System.out.println("=".repeat(80));

    // Generate report if we actually did something
    if (!results.isEmpty() && apply) {
      RemediationReportGenerator reportGenerator = new RemediationReportGenerator();
      RemediationReportContext context =
          RemediationReportContext.builder()
              .timestamp(startTime)
              .projectPath(projectRoot)
              .durationMs(durationMs)
              .totalAttempted(results.size())
              .successCount(successCount)
              .failedCount(failedCount)
              .skippedCount(skippedCount)
              .fixes(results)
              .filesModified(filesModified)
              .verification(VerificationResult.success())
              .build();

      Path reportPath = reportGenerator.generateAndSave(context);
      System.out.println("Report saved to: " + reportPath);
    }
  }

  private List<StructureFixResult> remediateCommonLayer(
      ModuleInfo module, boolean apply, BackupManager backupManager, List<String> filesModified)
      throws IOException {
    System.out.println("REMEDIATE: " + module.baseName() + "-common");
    System.out.println("-".repeat(80));

    List<StructureFixResult> results = new ArrayList<>();
    Path commonPath = module.path().resolve(module.baseName() + "-common");
    Path commonSrc = commonPath.resolve("src/main/java");
    Path apiSrc = module.path().resolve(module.baseName() + "-api/src/main/java");
    Path coreSrc = module.path().resolve(module.baseName() + "-core/src/main/java");

    if (!Files.isDirectory(commonSrc)) {
      System.out.println("  No source files found in common module");
      return results;
    }

    try (Stream<Path> files = Files.walk(commonSrc)) {
      List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();

      for (Path file : javaFiles) {
        String content = Files.readString(file);
        Path relativePath = commonSrc.relativize(file);
        String fileName = file.getFileName().toString();

        // Determine target based on file type
        Path targetDir;
        String reason;

        if (isApiCandidate(fileName, content)) {
          targetDir = apiSrc;
          reason = "interface/DTO/exception -> api";
        } else {
          targetDir = coreSrc;
          reason = "implementation -> core";
        }

        Path targetFile = targetDir.resolve(relativePath);
        System.out.printf("  [MOVE] %s%n", relativePath);
        System.out.printf("         %s%n", reason);
        System.out.printf("         -> %s%n", module.path().relativize(targetFile));

        StructureViolation violation =
            StructureViolation.builder()
                .ruleId("SS-006")
                .ruleName("NoCommonLayer")
                .target(module.baseName() + "-common")
                .message(
                    "File in common module should be in "
                        + (targetDir.equals(apiSrc) ? "api" : "core"))
                .severity(Severity.WARNING)
                .category(Category.STRUCTURE)
                .location(file.toString())
                .fix("Move to " + targetDir.getFileName())
                .build();

        if (apply) {
          // Backup first
          backupManager.backup(file);

          Files.createDirectories(targetFile.getParent());
          Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
          filesModified.add(targetFile.toString());

          results.add(
              StructureFixResult.success(
                  violation,
                  List.of(targetFile),
                  "Moved " + relativePath + " to " + targetDir.getFileName()));
        } else {
          results.add(
              StructureFixResult.dryRun(
                  violation,
                  List.of(targetFile),
                  "Would move " + relativePath + " to " + targetDir.getFileName(),
                  List.of()));
        }
      }
    }

    // Delete empty common module
    if (apply && !results.isEmpty()) {
      System.out.printf("  [DELETE] %s-common (empty after migration)%n", module.baseName());
      deleteDirectory(commonPath);
    } else if (!results.isEmpty()) {
      System.out.printf(
          "  [DELETE] %s-common (will be empty after migration)%n", module.baseName());
    }

    System.out.println();
    return results;
  }

  private List<StructureFixResult> remediateUtilLayer(
      ModuleInfo module, boolean apply, BackupManager backupManager, List<String> filesModified)
      throws IOException {
    System.out.println("REMEDIATE: " + module.baseName() + "-util");
    System.out.println("-".repeat(80));

    List<StructureFixResult> results = new ArrayList<>();
    Path utilPath = module.path().resolve(module.baseName() + "-util");
    Path utilSrc = utilPath.resolve("src/main/java");
    Path coreSrc = module.path().resolve(module.baseName() + "-core/src/main/java");

    if (!Files.isDirectory(utilSrc)) {
      System.out.println("  No source files found in util module");
      return results;
    }

    try (Stream<Path> files = Files.walk(utilSrc)) {
      List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();

      for (Path file : javaFiles) {
        Path relativePath = utilSrc.relativize(file);
        Path targetFile = coreSrc.resolve(relativePath);

        System.out.printf("  [MOVE] %s -> core%n", relativePath);

        StructureViolation violation =
            StructureViolation.builder()
                .ruleId("SS-007")
                .ruleName("NoUtilModules")
                .target(module.baseName() + "-util")
                .message("File in util module should be in core")
                .severity(Severity.WARNING)
                .category(Category.STRUCTURE)
                .location(file.toString())
                .fix("Move to core module")
                .build();

        if (apply) {
          // Backup first
          backupManager.backup(file);

          Files.createDirectories(targetFile.getParent());
          Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
          filesModified.add(targetFile.toString());

          results.add(
              StructureFixResult.success(
                  violation, List.of(targetFile), "Moved " + relativePath + " to core"));
        } else {
          results.add(
              StructureFixResult.dryRun(
                  violation,
                  List.of(targetFile),
                  "Would move " + relativePath + " to core",
                  List.of()));
        }
      }
    }

    if (apply && !results.isEmpty()) {
      System.out.printf("  [DELETE] %s-util%n", module.baseName());
      deleteDirectory(utilPath);
    } else if (!results.isEmpty()) {
      System.out.printf("  [DELETE] %s-util (will be empty after migration)%n", module.baseName());
    }

    System.out.println();
    return results;
  }

  private boolean isApiCandidate(String fileName, String content) {
    // Interfaces, DTOs, exceptions, enums go to API
    if (fileName.endsWith("Exception.java")) return true;
    if (fileName.endsWith("DTO.java") || fileName.endsWith("Dto.java")) return true;
    if (fileName.endsWith("Request.java") || fileName.endsWith("Response.java")) return true;
    if (content.contains("public interface ")) return true;
    if (content.contains("public enum ")) return true;
    if (content.contains("public record ")) return true;
    return false;
  }

  private void deleteDirectory(Path dir) throws IOException {
    if (!Files.isDirectory(dir)) return;
    try (Stream<Path> walk = Files.walk(dir)) {
      walk.sorted((a, b) -> b.compareTo(a))
          .forEach(
              p -> {
                try {
                  Files.delete(p);
                } catch (IOException e) {
                  // ignore
                }
              });
    }
  }
}
