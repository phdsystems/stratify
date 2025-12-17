package dev.engineeringlab.stratify.structure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.engineeringlab.stratify.structure.model.StructureViolation;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import dev.engineeringlab.stratify.structure.rule.impl.ComponentCompleteness;
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
 * Test suite for ComponentCompleteness rule.
 *
 * <p>Tests component completeness validation including:
 *
 * <ul>
 *   <li>Complete components (api + core)
 *   <li>Orphan api modules (no core)
 *   <li>Orphan core modules (no api)
 *   <li>SEA-4 pattern (no common in pattern)
 * </ul>
 */
class ComponentCompletenessTest {

  @TempDir Path tempDir;

  private ComponentCompleteness rule;
  private RuleLoader ruleLoader;

  @BeforeEach
  void setUp() throws IOException {
    // Create a minimal rule loader with test rule definition
    ruleLoader = new RuleLoader();
    String properties =
        """
        SEA-105.name=Component Completeness
        SEA-105.description=Ensures components have required layers
        SEA-105.category=STRUCTURE
        SEA-105.severity=ERROR
        SEA-105.enabled=true
        SEA-105.reason=Components must be complete
        SEA-105.fix=Add missing api or core module
        """;
    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    ruleLoader.loadFromStream(stream, "test.properties");

    rule = new ComponentCompleteness(ruleLoader);
  }

  @Test
  void testCompleteComponent() throws IOException {
    // Given: A complete component with api and core
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
  void testCompleteComponentWithOptionalLayers() throws IOException {
    // Given: A complete component with api, core, facade, and spi
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

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations should be reported
    assertThat(violations).isEmpty();
  }

  @Test
  void testOrphanApiModule() throws IOException {
    // Given: An api module without corresponding core
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation for missing core
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-105");
    assertThat(violation.message()).contains("text-processor-api exists");
    assertThat(violation.message()).contains("text-processor-core is missing");
  }

  @Test
  void testOrphanCoreModule() throws IOException {
    // Given: A core module without corresponding api
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path coreModule = componentDir.resolve("text-processor-core");
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation for missing api
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-105");
    assertThat(violation.message()).contains("text-processor-core exists");
    assertThat(violation.message()).contains("text-processor-api is missing");
  }

  @Test
  void testOrphanSpiModule() throws IOException {
    // Given: An spi module without corresponding api
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path spiModule = componentDir.resolve("text-processor-spi");
    Files.createDirectories(spiModule);
    Files.createFile(spiModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation for missing api
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-105");
    assertThat(violation.message()).contains("text-processor-spi exists");
    assertThat(violation.message()).contains("text-processor-api is missing");
  }

  @Test
  void testOrphanFacadeModule() throws IOException {
    // Given: A facade module without corresponding api
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path facadeModule = componentDir.resolve("text-processor-facade");
    Files.createDirectories(facadeModule);
    Files.createFile(facadeModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violation for missing api
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-105");
    assertThat(violation.message()).contains("text-processor-facade exists");
    assertThat(violation.message()).contains("text-processor-api is missing");
  }

  @Test
  void testMultipleIncompleteComponents() throws IOException {
    // Given: Multiple incomplete components
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    // text-processor: api only (missing core)
    Path textApiModule = componentDir.resolve("text-processor-api");
    Files.createDirectories(textApiModule);
    Files.createFile(textApiModule.resolve("pom.xml"));

    // user-service: core only (missing api)
    Path userCoreModule = componentDir.resolve("user-service-core");
    Files.createDirectories(userCoreModule);
    Files.createFile(userCoreModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violations for both components
    assertThat(violations).hasSize(1); // Single violation with multiple issues
    StructureViolation violation = violations.get(0);
    assertThat(violation.message()).contains("text-processor-api exists");
    assertThat(violation.message()).contains("text-processor-core is missing");
    assertThat(violation.message()).contains("user-service-core exists");
    assertThat(violation.message()).contains("user-service-api is missing");
  }

  @Test
  void testMixedCompleteAndIncompleteComponents() throws IOException {
    // Given: Mix of complete and incomplete components
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    // Complete component
    Path textApiModule = componentDir.resolve("text-processor-api");
    Path textCoreModule = componentDir.resolve("text-processor-core");
    Files.createDirectories(textApiModule);
    Files.createFile(textApiModule.resolve("pom.xml"));
    Files.createDirectories(textCoreModule);
    Files.createFile(textCoreModule.resolve("pom.xml"));

    // Incomplete component
    Path userApiModule = componentDir.resolve("user-service-api");
    Files.createDirectories(userApiModule);
    Files.createFile(userApiModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should only report violation for incomplete component
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.message()).contains("user-service-api exists");
    assertThat(violation.message()).contains("user-service-core is missing");
    assertThat(violation.message()).doesNotContain("text-processor");
  }

  @Test
  void testCommonLayerNotRequiredForCompleteness() throws IOException {
    // Given: A component with api, core, and common (deprecated but complete)
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Path coreModule = componentDir.resolve("text-processor-core");
    // Note: common is not in the pattern checked by ComponentCompleteness
    // (it only checks api, core, spi, facade)

    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (common is not checked by this rule)
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
  void testDirectoryWithoutLayerModules() throws IOException {
    // Given: A directory with modules but no layer pattern
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path randomModule = componentDir.resolve("random-module");
    Files.createDirectories(randomModule);
    Files.createFile(randomModule.resolve("pom.xml"));

    // When: Validating the directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations should be reported
    assertThat(violations).isEmpty();
  }

  @Test
  void testModuleWithoutPomXml() throws IOException {
    // Given: A directory with layer pattern but no pom.xml
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Files.createDirectories(apiModule);
    // No pom.xml created

    // When: Validating the directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (module is not considered valid without pom.xml)
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
  void testViolationContainsCorrectMetadata() throws IOException {
    // Given: An incomplete component
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path apiModule = componentDir.resolve("text-processor-api");
    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Violation should contain correct metadata
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.ruleId()).isEqualTo("SEA-105");
    assertThat(violation.ruleName()).isEqualTo("Component Completeness");
    assertThat(violation.target()).isEqualTo("components");
    assertThat(violation.location()).contains(componentDir.toString());
    assertThat(violation.fix()).isEqualTo("Add missing api or core module");
  }

  @Test
  void testSea4PatternExcludesCommon() throws IOException {
    // Given: A component with common layer (not checked by this rule)
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path commonModule = componentDir.resolve("text-processor-common");
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: No violations (common-only is not tracked by ComponentCompleteness)
    // Note: The NoCommonLayer rule handles common layer detection
    assertThat(violations).isEmpty();
  }

  @Test
  void testMultipleViolationTypesInSameComponent() throws IOException {
    // Given: A component with spi and facade but no api or core
    Path componentDir = tempDir.resolve("components");
    Files.createDirectories(componentDir);

    Path spiModule = componentDir.resolve("text-processor-spi");
    Path facadeModule = componentDir.resolve("text-processor-facade");
    Files.createDirectories(spiModule);
    Files.createFile(spiModule.resolve("pom.xml"));
    Files.createDirectories(facadeModule);
    Files.createFile(facadeModule.resolve("pom.xml"));

    // When: Validating the component directory
    List<StructureViolation> violations = rule.validate(componentDir);

    // Then: Should report violations for both spi and facade missing api
    assertThat(violations).hasSize(1);
    StructureViolation violation = violations.get(0);
    assertThat(violation.message()).contains("text-processor-spi exists");
    assertThat(violation.message()).contains("text-processor-facade exists");
    assertThat(violation.message()).contains("text-processor-api is missing");
  }
}
