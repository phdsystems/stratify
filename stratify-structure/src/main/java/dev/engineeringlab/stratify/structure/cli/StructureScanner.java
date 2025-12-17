package dev.engineeringlab.stratify.structure.cli;

import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.ModuleScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
    System.out.println("=".repeat(80));
    System.out.println("Stratify Structure Remediation");
    System.out.println("=".repeat(80));
    System.out.println("Project: " + projectRoot.toAbsolutePath());
    System.out.println("Mode: " + (apply ? "APPLY" : "DRY-RUN"));
    System.out.println();

    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);
    int totalActions = 0;

    for (ModuleInfo module : modules) {
      if (module.hasCommon()) {
        totalActions += remediateCommonLayer(module, apply);
      }
      if (module.hasUtil()) {
        totalActions += remediateUtilLayer(module, apply);
      }
    }

    System.out.println("=".repeat(80));
    if (apply) {
      System.out.printf("Remediation complete: %d actions applied%n", totalActions);
    } else {
      System.out.printf("Dry-run complete: %d actions planned%n", totalActions);
      System.out.println("Run with --apply to execute changes");
    }
    System.out.println("=".repeat(80));
  }

  private int remediateCommonLayer(ModuleInfo module, boolean apply) throws IOException {
    System.out.println("REMEDIATE: " + module.baseName() + "-common");
    System.out.println("-".repeat(80));

    Path commonPath = module.path().resolve(module.baseName() + "-common");
    Path commonSrc = commonPath.resolve("src/main/java");
    Path apiSrc = module.path().resolve(module.baseName() + "-api/src/main/java");
    Path coreSrc = module.path().resolve(module.baseName() + "-core/src/main/java");

    if (!Files.isDirectory(commonSrc)) {
      System.out.println("  No source files found in common module");
      return 0;
    }

    int actions = 0;
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

        if (apply) {
          Files.createDirectories(targetFile.getParent());
          Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        actions++;
      }
    }

    // Delete empty common module
    if (apply && actions > 0) {
      System.out.printf("  [DELETE] %s-common (empty after migration)%n", module.baseName());
      deleteDirectory(commonPath);
    } else if (actions > 0) {
      System.out.printf(
          "  [DELETE] %s-common (will be empty after migration)%n", module.baseName());
    }

    System.out.println();
    return actions;
  }

  private int remediateUtilLayer(ModuleInfo module, boolean apply) throws IOException {
    System.out.println("REMEDIATE: " + module.baseName() + "-util");
    System.out.println("-".repeat(80));

    Path utilPath = module.path().resolve(module.baseName() + "-util");
    Path utilSrc = utilPath.resolve("src/main/java");
    Path coreSrc = module.path().resolve(module.baseName() + "-core/src/main/java");

    if (!Files.isDirectory(utilSrc)) {
      System.out.println("  No source files found in util module");
      return 0;
    }

    int actions = 0;
    try (Stream<Path> files = Files.walk(utilSrc)) {
      List<Path> javaFiles = files.filter(p -> p.toString().endsWith(".java")).toList();

      for (Path file : javaFiles) {
        Path relativePath = utilSrc.relativize(file);
        Path targetFile = coreSrc.resolve(relativePath);

        System.out.printf("  [MOVE] %s -> core%n", relativePath);

        if (apply) {
          Files.createDirectories(targetFile.getParent());
          Files.move(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
        }
        actions++;
      }
    }

    if (apply && actions > 0) {
      System.out.printf("  [DELETE] %s-util%n", module.baseName());
      deleteDirectory(utilPath);
    } else if (actions > 0) {
      System.out.printf("  [DELETE] %s-util (will be empty after migration)%n", module.baseName());
    }

    System.out.println();
    return actions;
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
