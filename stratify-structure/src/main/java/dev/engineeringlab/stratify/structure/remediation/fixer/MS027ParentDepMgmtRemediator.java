package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for MS-027: Parent Must Declare Dependency Management.
 *
 * <p>Parent modules (artifactId ending with -parent) must have a {@code <dependencyManagement>}
 * section in their pom.xml to manage common dependencies for child modules.
 *
 * <p>This remediator:
 *
 * <ol>
 *   <li>Adds an empty {@code <dependencyManagement>} section to pom.xml as a placeholder
 *   <li>Uses backup/rollback functionality for safe modification
 *   <li>Preserves existing pom.xml structure and formatting
 * </ol>
 *
 * <p>The {@code <dependencyManagement>} section centralizes version management and ensures
 * consistent dependency versions across all child modules.
 */
public class MS027ParentDepMgmtRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-027"};
  private static final int PRIORITY = 60;

  // Pattern to check if dependencyManagement already exists
  private static final Pattern DEPENDENCY_MANAGEMENT_PATTERN =
      Pattern.compile("<dependencyManagement>", Pattern.DOTALL);

  // Pattern to find </project> tag
  private static final Pattern PROJECT_END_PATTERN =
      Pattern.compile("(\\s*</project>)", Pattern.DOTALL);

  // Pattern to find </modules> tag
  private static final Pattern MODULES_END_PATTERN =
      Pattern.compile("(\\s*</modules>)", Pattern.DOTALL);

  public MS027ParentDepMgmtRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MS027ParentDepMgmtRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes MS-027 violations: adds <dependencyManagement> section to parent module pom.xml";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an MS-027 violation");
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

    try {
      return fixPomFile(pomFile, violation, context);
    } catch (Exception e) {
      return FixResult.failed(violation, "Error adding dependencyManagement: " + e.getMessage());
    }
  }

  private FixResult fixPomFile(Path pomFile, StructureViolation violation, FixerContext context)
      throws Exception {
    String originalContent = readFile(pomFile);

    // Check if dependencyManagement already exists
    if (DEPENDENCY_MANAGEMENT_PATTERN.matcher(originalContent).find()) {
      return FixResult.skipped(violation, "dependencyManagement section already exists");
    }

    // Build the dependencyManagement section
    String dependencyManagementSection = buildDependencyManagementSection();

    // Insert dependencyManagement section
    String modifiedContent =
        insertDependencyManagement(originalContent, dependencyManagementSection);

    if (modifiedContent.equals(originalContent)) {
      return FixResult.skipped(violation, "No changes needed");
    }

    String diff = generateDiff(originalContent, modifiedContent, pomFile.getFileName().toString());

    if (context.dryRun()) {
      context.log("Would add <dependencyManagement> section to %s", pomFile);
      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.DRY_RUN)
          .description("Would add <dependencyManagement> section (dry-run)")
          .modifiedFiles(List.of(pomFile))
          .diffs(List.of(diff))
          .build();
    }

    // Create backup and write modified content
    backup(pomFile, context.projectRoot());
    writeFile(pomFile, modifiedContent);

    context.log("Added <dependencyManagement> section to %s", pomFile);

    // Cleanup backups on success
    cleanupBackupsOnSuccess(List.of(pomFile), context.projectRoot());

    return FixResult.builder()
        .violation(violation)
        .status(FixStatus.FIXED)
        .description("Added <dependencyManagement> section with placeholder comment")
        .modifiedFiles(List.of(pomFile))
        .diffs(List.of(diff))
        .build();
  }

  /**
   * Builds the dependencyManagement section with a placeholder comment.
   *
   * @return the dependencyManagement section as a string
   */
  private String buildDependencyManagementSection() {
    return """

                <dependencyManagement>
                    <dependencies>
                        <!-- TODO: Add common dependency versions here for child modules to inherit -->
                        <!-- Example:
                        <dependency>
                            <groupId>org.junit.jupiter</groupId>
                            <artifactId>junit-jupiter</artifactId>
                            <version>5.10.0</version>
                            <scope>test</scope>
                        </dependency>
                        -->
                    </dependencies>
                </dependencyManagement>""";
  }

  /**
   * Inserts the dependencyManagement section into the pom.xml content.
   *
   * <p>Insertion strategy:
   *
   * <ol>
   *   <li>After {@code </modules>} if it exists
   *   <li>Before {@code </project>} as fallback
   * </ol>
   *
   * @param content the original pom.xml content
   * @param dependencyManagement the dependencyManagement section to insert
   * @return the modified content
   */
  private String insertDependencyManagement(String content, String dependencyManagement) {
    // Strategy 1: Insert after </modules> if it exists
    Matcher modulesMatcher = MODULES_END_PATTERN.matcher(content);
    if (modulesMatcher.find()) {
      int insertPoint = modulesMatcher.end();
      return content.substring(0, insertPoint)
          + "\n"
          + dependencyManagement
          + content.substring(insertPoint);
    }

    // Strategy 2: Insert before </project> as fallback
    Matcher projectMatcher = PROJECT_END_PATTERN.matcher(content);
    if (projectMatcher.find()) {
      int insertPoint = projectMatcher.start();
      return content.substring(0, insertPoint)
          + dependencyManagement
          + "\n"
          + content.substring(insertPoint);
    }

    // If we can't find insertion point, return original content
    return content;
  }
}
