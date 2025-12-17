package dev.engineeringlab.stratify.structure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.structure.model.Severity;
import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import dev.engineeringlab.stratify.structure.rule.impl.NoCommonLayer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test suite for NoCommonLayer rule (SEA-4).
 *
 * <p>Tests the prohibition of {base}-common modules including:
 *
 * <ul>
 *   <li>Modules without common (should pass)
 *   <li>Modules with common (should fail with ERROR)
 *   <li>Multiple common modules detection
 *   <li>Mixed scenarios with compliant and non-compliant modules
 * </ul>
 */
class NoCommonLayerTest {

  @TempDir Path tempDir;

  private NoCommonLayer rule;
  private RuleLoader ruleLoader;

  @BeforeEach
  void setUp() throws IOException {
    // Create a minimal rule loader with test rule definition
    ruleLoader = new RuleLoader();
    String properties =
        """
        SEA-4.name=No Common Layer
        SEA-4.description=Prohibits use of common layer modules
        SEA-4.category=STRUCTURE
        SEA-4.severity=ERROR
        SEA-4.enabled=true
        SEA-4.reason=Common layer is deprecated in SEA-4
        SEA-4.fix=Move shared code to api or core modules
        """;
    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    ruleLoader.loadFromStream(stream, "test.properties");

    rule = new NoCommonLayer(ruleLoader);
  }

  @Test
  void testModuleWithoutCommon() throws IOException {
    // Given: A component without common layer (compliant)
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Path coreModule = componentDir.resolve("text-processor-core");
    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations should be reported
    assertThat(violations).isEmpty();
  }

  @Test
  void testModuleWithCommon() throws IOException {
    // Given: A component with common layer (non-compliant)
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report ERROR violation
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-4");
    assertThat(violation.severity()).isEqualTo(Severity.ERROR);
    assertThat(violation.target()).isEqualTo("text-processor-common");
    assertThat(violation.message()).contains("text-processor-common");
    assertThat(violation.message()).contains("deprecated '-common' layer pattern");
    assertThat(violation.message()).contains("SEA-4");
  }

  @Test
  void testModuleWithCommonAndOtherLayers() throws IOException {
    // Given: A component with api, core, and common
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Path coreModule = componentDir.resolve("text-processor-core");
    Path commonModule = componentDir.resolve("text-processor-common");

    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation only for common module
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.target()).isEqualTo("text-processor-common");
    assertThat(violation.severity()).isEqualTo(Severity.ERROR);
  }

  @Test
  void testMultipleCommonModules() throws IOException {
    // Given: Multiple components with common layers
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path textCommonModule = componentDir.resolve("text-processor-common");
    Path userCommonModule = componentDir.resolve("user-service-common");

    Files.createDirectories(textCommonModule);
    Files.createFile(textCommonModule.resolve("pom.xml"));
    Files.createDirectories(userCommonModule);
    Files.createFile(userCommonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violations for both common modules
    assertThat(violations).hasSize(2);
    assertThat(violations)
        .extracting(StructureViolation::target)
        .containsExactlyInAnyOrder("text-processor-common", "user-service-common");
    assertThat(violations).allMatch(v -> v.severity() == Severity.ERROR);
  }

  @Test
  void testMixedCompliantAndNonCompliant() throws IOException {
    // Given: Mix of compliant and non-compliant components
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    // Compliant component (no common)
    Path textApiModule = componentDir.resolve("text-processor-api");
    Path textCoreModule = componentDir.resolve("text-processor-core");
    Files.createDirectories(textApiModule);
    Files.createFile(textApiModule.resolve("pom.xml"));
    Files.createDirectories(textCoreModule);
    Files.createFile(textCoreModule.resolve("pom.xml"));

    // Non-compliant component (has common)
    Path userCommonModule = componentDir.resolve("user-service-common");
    Files.createDirectories(userCommonModule);
    Files.createFile(userCommonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should only report violation for non-compliant component
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.target()).isEqualTo("user-service-common");
    assertThat(violation.severity()).isEqualTo(Severity.ERROR);
  }

  @Test
  void testCommonDirectoryWithoutPomXml() throws IOException {
    // Given: A common directory without pom.xml (not a Maven module)
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    // No pom.xml created

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (not a valid Maven module)
    assertThat(violations).isEmpty();
  }

  @Test
  void testEmptyDirectory() throws IOException {
    // Given: An empty directory
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    // When: Validating the empty directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations should be reported
    assertThat(violations).isEmpty();
  }

  @Test
  void testDirectoryWithoutCommonModules() throws IOException {
    // Given: A directory with various modules but no common
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Path coreModule = componentDir.resolve("text-processor-core");
    Path facadeModule = componentDir.resolve("text-processor-facade");
    Path spiModule = componentDir.resolve("text-processor-spi");

    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));
    Files.createDirectories(facadeModule);
    Files.createFile(facadeModule.resolve("pom.xml"));
    Files.createDirectories(spiModule);
    Files.createFile(spiModule.resolve("pom.xml"));

    // When: Validating the directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations should be reported
    assertThat(violations).isEmpty();
  }

  @Test
  void testValidateFile() throws IOException {
    // Given: A file instead of directory
    Path file = tempDir.resolve("file.txt");
    Files.createFile(file);

    // When: Validating the file
    List<StructureViolation> violations = rule.validate(file);

    // Then: No violations (rule doesn't apply to files)
    assertThat(violations).isEmpty();
  }

  @Test
  void testViolationContainsFixSuggestion() throws IOException {
    // Given: A component with common layer
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Violation should contain fix suggestion
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.message()).contains("text-processor-api");
    assertThat(violation.message()).contains("text-processor-core");
    assertThat(violation.message()).contains("Move shared contracts");
    assertThat(violation.message()).contains("shared utilities");
  }

  @Test
  void testViolationContainsCorrectMetadata() throws IOException {
    // Given: A component with common layer
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Violation should contain correct metadata
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-4");
    assertThat(violation.ruleName()).isEqualTo("No Common Layer");
    assertThat(violation.target()).isEqualTo("text-processor-common");
    assertThat(violation.severity()).isEqualTo(Severity.ERROR);
    assertThat(violation.location()).contains(commonModule.toString());
    assertThat(violation.fix()).isEqualTo("Move shared code to api or core modules");
  }

  @Test
  void testCommonInModuleNameButNotSuffix() throws IOException {
    // Given: A module with "common" in name but not as suffix
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path module = componentDir.resolve("common-utilities-api");
    Files.createDirectories(module);
    Files.createFile(module.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (common is not a suffix)
    assertThat(violations).isEmpty();
  }

  @Test
  void testNestedCommonModules() throws IOException {
    // Given: Nested structure with common module
    Path parentDir = tempDir.resolve("parent");
    Path componentDir = parentDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the parent directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation for nested common module
    assertThat(violations).hasSize(1);
    assertThat(violations.get(0).target()).isEqualTo("text-processor-common");
  }

  @Test
  void testRuleNotApplicableToNonDirectories() throws IOException {
    // Given: A file
    Path file = tempDir.resolve("test.txt");
    Files.createFile(file);

    // When: Validating the file
    List<StructureViolation> violations = rule.validate(file);

    // Then: No violations (rule doesn't apply)
    assertThat(violations).isEmpty();
  }

  @Test
  void testCommonModuleExtractsBaseName() throws IOException {
    // Given: A common module with complex base name
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("my-long-module-name-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should extract base name correctly in message
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.message()).contains("my-long-module-name-api");
    assertThat(violation.message()).contains("my-long-module-name-core");
  }

  @Test
  void testDirectoryNameEndingWithCommonButNoModules() throws IOException {
    // Given: Directory ending with "-common" but containing no pom.xml
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path notAModule = componentDir.resolve("just-a-common-folder");
    Files.createDirectories(notAModule);

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (not a Maven module, doesn't end with -common)
    assertThat(violations).isEmpty();
  }
}
