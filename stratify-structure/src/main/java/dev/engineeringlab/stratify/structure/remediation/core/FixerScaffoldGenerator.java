package dev.engineeringlab.stratify.structure.remediation.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates scaffolding for new structure fixers including both implementation and test classes.
 *
 * <p>Usage:
 *
 * <pre>
 * FixerScaffoldGenerator generator = new FixerScaffoldGenerator();
 * generator.generate(
 *     "SEA-4-099",                 // ruleId
 *     "MyNewFixer",                // fixer class name
 *     "module",                    // package subpath (e.g., module, code, pom, dependency)
 *     "Fixes something important", // description
 *     projectRoot                  // path to stratify-structure module
 * );
 * </pre>
 *
 * <p>This will generate:
 *
 * <ul>
 *   <li>{@code src/main/java/.../fixer/core/{subpath}/MyNewFixer.java}
 *   <li>{@code src/test/java/.../fixer/core/{subpath}/MyNewFixerTest.java}
 * </ul>
 */
public class FixerScaffoldGenerator {

  private static final String BASE_PACKAGE =
      "dev.engineeringlab.stratify.structure.remediation.fixer";
  private static final String MAIN_PATH = "src/main/java";
  private static final String TEST_PATH = "src/test/java";

  /**
   * Generates a new fixer and its test class.
   *
   * @param ruleId the compliance rule ID (e.g., "SEA-4-001", "SEA-4-002")
   * @param fixerName the fixer class name (e.g., "MyNewFixer")
   * @param packageSubpath the package subpath (e.g., "module", "code", "pom")
   * @param description a brief description of what the fixer does
   * @param moduleRoot the path to the stratify-structure module root
   * @return result containing paths to generated files
   * @throws IOException if file generation fails
   */
  public GenerationResult generate(
      String ruleId, String fixerName, String packageSubpath, String description, Path moduleRoot)
      throws IOException {

    String fullPackage = BASE_PACKAGE + "." + packageSubpath;
    String packagePath = fullPackage.replace(".", "/");

    // Generate fixer class
    Path fixerPath =
        moduleRoot.resolve(MAIN_PATH).resolve(packagePath).resolve(fixerName + ".java");
    String fixerContent = generateFixerClass(ruleId, fixerName, fullPackage, description);

    Files.createDirectories(fixerPath.getParent());
    Files.writeString(fixerPath, fixerContent);

    // Generate test class
    Path testPath =
        moduleRoot.resolve(TEST_PATH).resolve(packagePath).resolve(fixerName + "Test.java");
    String testContent = generateTestClass(ruleId, fixerName, fullPackage, description);

    Files.createDirectories(testPath.getParent());
    Files.writeString(testPath, testContent);

    return new GenerationResult(fixerPath, testPath, fixerName, ruleId);
  }

  private String generateFixerClass(
      String ruleId, String fixerName, String fullPackage, String description) {
    return """
package %s;

import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Fixer for %s: %s.
 *
 * <p>TODO: Add detailed documentation about what this fixer does.
 */
public class %s extends AbstractStructureFixer {

    private static final String[] SUPPORTED_RULES = {"%s"};
    private static final int PRIORITY = 50; // Adjust priority as needed

    public %s() {
        setPriority(PRIORITY);
    }

    @Override
    public String getName() {
        return "%s";
    }

    @Override
    public String getDescription() {
        return "%s";
    }

    @Override
    public String[] getAllSupportedRules() {
        return SUPPORTED_RULES;
    }

    @Override
    public FixResult fix(StructureViolation violation, FixerContext context) {
        String ruleId = violation.ruleId();
        if (!"%s".equals(ruleId)) {
            return FixResult.skipped(violation, "Not a %s violation");
        }

        try {
            Path moduleRoot = context.moduleRoot();

            // Check preconditions
            Path pomPath = moduleRoot.resolve("pom.xml");
            if (!Files.exists(pomPath)) {
                return FixResult.skipped(violation, "No pom.xml found in module root");
            }

            List<Path> modifiedFiles = new ArrayList<>();
            List<String> diffs = new ArrayList<>();

            if (context.dryRun()) {
                diffs.add("+ Would apply fix for %s");

                return FixResult.builder()
                        .violation(violation)
                        .status(FixStatus.DRY_RUN)
                        .description("Would fix %s violation")
                        .diffs(diffs)
                        .build();
            }

            // TODO: Implement the actual fix logic here
            // 1. Read relevant files
            // 2. Apply transformations
            // 3. Write changes back
            // 4. Add modified files to modifiedFiles list

            return FixResult.builder()
                    .violation(violation)
                    .status(FixStatus.FIXED)
                    .description("Fixed %s violation")
                    .modifiedFiles(modifiedFiles)
                    .build();

        } catch (Exception e) {
            return FixResult.failed(violation,
                    "Failed to fix %s: " + e.getMessage());
        }
    }
}
"""
        .formatted(
            fullPackage,
            ruleId,
            description,
            fixerName,
            ruleId,
            fixerName,
            fixerName,
            description,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId);
  }

  private String generateTestClass(
      String ruleId, String fixerName, String fullPackage, String description) {
    return """
package %s;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.structure.remediation.api.FixerConfig;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link %s}.
 *
 * <p>Tests validate that %s violations are handled correctly.
 */
class %sTest {

    private %s fixer;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        fixer = new %s();
        tempDir = Files.createTempDirectory("%s-test");
    }

    @AfterEach
    void tearDown() {
        if (tempDir != null) {
            try {
                Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            // ignore
                        }
                    });
            } catch (IOException e) {
                // ignore
            }
        }
    }

    @Nested
    @DisplayName("Fixer Metadata")
    class MetadataTests {

        @Test
        @DisplayName("Should return correct name")
        void shouldReturnCorrectName() {
            assertThat(fixer.getName()).isEqualTo("%s");
        }

        @Test
        @DisplayName("Should support %s")
        void shouldSupportRule() {
            String[] rules = fixer.getAllSupportedRules();
            assertThat(rules).contains("%s");
        }

        @Test
        @DisplayName("Should be able to fix %s violations")
        void shouldCanFix() {
            StructureViolation violation = StructureViolation.builder()
                .ruleId("%s")
                .message("Test violation")
                .build();
            assertThat(fixer.canFix(violation)).isTrue();
        }

        @Test
        @DisplayName("Should not be able to fix other violations")
        void shouldNotCanFixOtherViolations() {
            StructureViolation violation = StructureViolation.builder()
                .ruleId("OTHER-001")
                .message("Other violation")
                .build();
            assertThat(fixer.canFix(violation)).isFalse();
        }
    }

    @Nested
    @DisplayName("Skip Conditions")
    class SkipConditionTests {

        @Test
        @DisplayName("Should skip non-supported rule violations")
        void shouldSkipNonSupportedRules() {
            StructureViolation violation = StructureViolation.builder()
                .ruleId("OTHER-001")
                .message("Wrong rule")
                .build();

            FixerContext context = createContext(false, tempDir);
            FixResult result = fixer.fix(violation, context);

            assertThat(result.status()).isEqualTo(FixStatus.SKIPPED);
            assertThat(result.description()).contains("Not a %s");
        }

        @Test
        @DisplayName("Should skip when no pom.xml exists")
        void shouldSkipWhenNoPomExists() throws IOException {
            Path moduleDir = tempDir.resolve("no-pom-module");
            Files.createDirectories(moduleDir);

            StructureViolation violation = StructureViolation.builder()
                .ruleId("%s")
                .message("Test violation")
                .location(moduleDir)
                .build();

            FixerContext context = createContext(false, moduleDir);
            FixResult result = fixer.fix(violation, context);

            assertThat(result.status()).isEqualTo(FixStatus.SKIPPED);
            assertThat(result.description()).contains("No pom.xml found");
        }
    }

    @Nested
    @DisplayName("%s: Fix Scenarios")
    class FixScenarioTests {

        @Test
        @DisplayName("Should fix violation")
        void shouldFixViolation() throws IOException {
            // Given: A module with the violation
            Path moduleDir = tempDir.resolve("test-module");
            Files.createDirectories(moduleDir);
            Files.writeString(moduleDir.resolve("pom.xml"), \"\"\"
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>dev.engineeringlab</groupId>
                    <artifactId>test-module</artifactId>
                    <version>1.0.0</version>
                </project>
                \"\"\");

            StructureViolation violation = StructureViolation.builder()
                .ruleId("%s")
                .message("Test violation")
                .location(moduleDir)
                .build();

            FixerContext context = createContext(false, moduleDir);
            FixResult result = fixer.fix(violation, context);

            // TODO: Update assertions based on actual fix behavior
            assertThat(result.status()).isIn(FixStatus.FIXED, FixStatus.SKIPPED);
        }
    }

    @Nested
    @DisplayName("Dry Run Mode")
    class DryRunTests {

        @Test
        @DisplayName("Should not modify files in dry run mode")
        void shouldNotModifyFilesInDryRun() throws IOException {
            Path moduleDir = tempDir.resolve("dry-run-module");
            Files.createDirectories(moduleDir);
            Files.writeString(moduleDir.resolve("pom.xml"), \"\"\"
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>dev.engineeringlab</groupId>
                    <artifactId>dry-run-module</artifactId>
                    <version>1.0.0</version>
                </project>
                \"\"\");

            String originalContent = Files.readString(moduleDir.resolve("pom.xml"));

            StructureViolation violation = StructureViolation.builder()
                .ruleId("%s")
                .message("Test violation")
                .location(moduleDir)
                .build();

            FixerContext context = createContext(true, moduleDir); // dry run = true
            FixResult result = fixer.fix(violation, context);

            assertThat(result.status()).isIn(FixStatus.DRY_RUN, FixStatus.SKIPPED);

            // Verify file was not modified
            String currentContent = Files.readString(moduleDir.resolve("pom.xml"));
            assertThat(currentContent).isEqualTo(originalContent);
        }
    }

    private FixerContext createContext(boolean dryRun, Path moduleRoot) {
        FixerConfig config = new FixerConfig();

        return FixerContext.builder()
            .projectRoot(tempDir)
            .moduleRoot(moduleRoot)
            .dryRun(dryRun)
            .javaVersion("21")
            .classpath(List.of())
            .config(config)
            .logger(msg -> {}) // Silent for tests
            .build();
    }
}
"""
        .formatted(
            fullPackage,
            fixerName,
            ruleId,
            fixerName,
            fixerName,
            fixerName,
            fixerName.toLowerCase(),
            fixerName,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId,
            ruleId);
  }

  /** Result of scaffold generation. */
  public record GenerationResult(Path fixerPath, Path testPath, String fixerName, String ruleId) {
    @Override
    public String toString() {
      return String.format(
          "Generated fixer for %s:%n  Fixer: %s%n  Test:  %s", ruleId, fixerPath, testPath);
    }
  }
}
