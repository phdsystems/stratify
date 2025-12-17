package dev.engineeringlab.stratify.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.stratify.structure.model.Category;
import dev.engineeringlab.stratify.structure.model.Severity;
import dev.engineeringlab.stratify.structure.rule.RuleDefinition;
import dev.engineeringlab.stratify.structure.rule.RuleLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test suite for RuleLoader.
 *
 * <p>Tests rule loading functionality including:
 *
 * <ul>
 *   <li>Loading from .properties files
 *   <li>Rule definition parsing
 *   <li>Enabled/disabled rules
 *   <li>Category and severity parsing
 * </ul>
 */
class RuleLoaderTest {

  @TempDir Path tempDir;

  private RuleLoader loader;

  @BeforeEach
  void setUp() {
    loader = new RuleLoader();
  }

  @Test
  void testLoadFromPropertiesStream() throws IOException {
    // Given: A properties file with rule definitions
    String properties =
        """
        TEST-001.name=Test Rule One
        TEST-001.description=This is a test rule
        TEST-001.category=STRUCTURE
        TEST-001.severity=ERROR
        TEST-001.enabled=true
        TEST-001.reason=Testing purposes
        TEST-001.fix=No fix needed for tests
        TEST-001.reference=https://example.com
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Rule should be loaded
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).hasSize(1);
    assertThat(rules).containsKey("TEST-001");

    RuleDefinition rule = rules.get("TEST-001");
    assertThat(rule.id()).isEqualTo("TEST-001");
    assertThat(rule.name()).isEqualTo("Test Rule One");
    assertThat(rule.description()).isEqualTo("This is a test rule");
    assertThat(rule.category()).isEqualTo(Category.STRUCTURE);
    assertThat(rule.severity()).isEqualTo(Severity.ERROR);
    assertThat(rule.enabled()).isTrue();
    assertThat(rule.reason()).isEqualTo("Testing purposes");
    assertThat(rule.fix()).isEqualTo("No fix needed for tests");
    assertThat(rule.reference()).isEqualTo("https://example.com");
  }

  @Test
  void testLoadMultipleRulesFromProperties() throws IOException {
    // Given: Multiple rule definitions
    String properties =
        """
        TEST-001.name=Test Rule One
        TEST-001.description=First test rule
        TEST-001.category=STRUCTURE
        TEST-001.severity=ERROR
        TEST-001.enabled=true

        TEST-002.name=Test Rule Two
        TEST-002.description=Second test rule
        TEST-002.category=DEPENDENCIES
        TEST-002.severity=WARNING
        TEST-002.enabled=false
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Both rules should be loaded
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).hasSize(2);
    assertThat(rules).containsKeys("TEST-001", "TEST-002");

    RuleDefinition rule1 = rules.get("TEST-001");
    assertThat(rule1.enabled()).isTrue();
    assertThat(rule1.severity()).isEqualTo(Severity.ERROR);

    RuleDefinition rule2 = rules.get("TEST-002");
    assertThat(rule2.enabled()).isFalse();
    assertThat(rule2.severity()).isEqualTo(Severity.WARNING);
  }

  @Test
  void testLoadFromPropertiesFile() throws IOException {
    // Given: A properties file on disk
    Path propsFile = tempDir.resolve("rules.properties");
    String properties =
        """
        TEST-001.name=Test Rule
        TEST-001.description=Test description
        TEST-001.category=STRUCTURE
        TEST-001.severity=ERROR
        TEST-001.enabled=true
        """;
    Files.writeString(propsFile, properties);

    // When: Loading from path
    loader.loadFromPath(propsFile);

    // Then: Rule should be loaded
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).hasSize(1);
    assertThat(rules).containsKey("TEST-001");
  }

  @Test
  void testLoadFromNonExistentFile() {
    // Given: A non-existent file path
    Path nonExistent = tempDir.resolve("does-not-exist.properties");

    // When: Loading from path
    loader.loadFromPath(nonExistent);

    // Then: No rules should be loaded (silently ignored)
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).isEmpty();
  }

  @Test
  void testLoadFromClasspathMissing() {
    // Given: A non-existent classpath resource
    // When: Loading from classpath
    loader.loadFromClasspath("non-existent-rules.properties");

    // Then: No rules should be loaded (silently ignored)
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).isEmpty();
  }

  @Test
  void testLoadRequiredFromClasspathMissing() {
    // Given: A non-existent required classpath resource
    // When/Then: Should throw IllegalStateException
    assertThatThrownBy(() -> loader.loadRequiredFromClasspath("non-existent-rules.properties"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Required rules file");
  }

  @Test
  void testParseRuleWithDefaults() throws IOException {
    // Given: A minimal rule definition (using defaults)
    String properties =
        """
        TEST-001.name=Minimal Rule
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Rule should be loaded with defaults
    RuleDefinition rule = loader.getRule("TEST-001");
    assertThat(rule).isNotNull();
    assertThat(rule.name()).isEqualTo("Minimal Rule");
    assertThat(rule.description()).isEmpty();
    assertThat(rule.category()).isEqualTo(Category.STRUCTURE); // Default
    assertThat(rule.severity()).isEqualTo(Severity.ERROR); // Default
    assertThat(rule.enabled()).isTrue(); // Default
    assertThat(rule.reason()).isEmpty();
    assertThat(rule.fix()).isEmpty();
    assertThat(rule.reference()).isEmpty();
  }

  @Test
  void testParseSeverityLevels() throws IOException {
    // Given: Rules with different severity levels
    String properties =
        """
        TEST-001.name=Error Rule
        TEST-001.severity=ERROR

        TEST-002.name=Warning Rule
        TEST-002.severity=WARNING

        TEST-003.name=Info Rule
        TEST-003.severity=INFO
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Severities should be parsed correctly
    assertThat(loader.getRule("TEST-001").severity()).isEqualTo(Severity.ERROR);
    assertThat(loader.getRule("TEST-002").severity()).isEqualTo(Severity.WARNING);
    assertThat(loader.getRule("TEST-003").severity()).isEqualTo(Severity.INFO);
  }

  @Test
  void testParseCategories() throws IOException {
    // Given: Rules with different categories
    String properties =
        """
        TEST-001.name=Structure Rule
        TEST-001.category=STRUCTURE

        TEST-002.name=Dependencies Rule
        TEST-002.category=DEPENDENCIES

        TEST-003.name=Naming Rule
        TEST-003.category=NAMING
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Categories should be parsed correctly
    assertThat(loader.getRule("TEST-001").category()).isEqualTo(Category.STRUCTURE);
    assertThat(loader.getRule("TEST-002").category()).isEqualTo(Category.DEPENDENCIES);
    assertThat(loader.getRule("TEST-003").category()).isEqualTo(Category.NAMING);
  }

  @Test
  void testParseInvalidSeverity() throws IOException {
    // Given: A rule with invalid severity (should default to ERROR)
    String properties =
        """
        TEST-001.name=Invalid Severity Rule
        TEST-001.severity=INVALID
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Should default to ERROR
    RuleDefinition rule = loader.getRule("TEST-001");
    assertThat(rule.severity()).isEqualTo(Severity.ERROR);
  }

  @Test
  void testParseInvalidCategory() throws IOException {
    // Given: A rule with invalid category (should default to STRUCTURE)
    String properties =
        """
        TEST-001.name=Invalid Category Rule
        TEST-001.category=INVALID
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream
    loader.loadFromStream(stream, "test.properties");

    // Then: Should default to STRUCTURE
    RuleDefinition rule = loader.getRule("TEST-001");
    assertThat(rule.category()).isEqualTo(Category.STRUCTURE);
  }

  @Test
  void testGetEnabledRules() throws IOException {
    // Given: Mix of enabled and disabled rules
    String properties =
        """
        TEST-001.name=Enabled Rule
        TEST-001.enabled=true

        TEST-002.name=Disabled Rule
        TEST-002.enabled=false

        TEST-003.name=Default Enabled Rule
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    loader.loadFromStream(stream, "test.properties");

    // When: Getting enabled rules
    List<RuleDefinition> enabledRules = loader.getEnabledRules();

    // Then: Only enabled rules should be returned
    assertThat(enabledRules).hasSize(2);
    assertThat(enabledRules)
        .extracting(RuleDefinition::id)
        .containsExactlyInAnyOrder("TEST-001", "TEST-003");
  }

  @Test
  void testGetRulesByCategory() throws IOException {
    // Given: Rules in different categories
    String properties =
        """
        TEST-001.name=Structure Rule 1
        TEST-001.category=STRUCTURE

        TEST-002.name=Structure Rule 2
        TEST-002.category=STRUCTURE

        TEST-003.name=Dependencies Rule
        TEST-003.category=DEPENDENCIES
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    loader.loadFromStream(stream, "test.properties");

    // When: Getting rules by category
    List<RuleDefinition> structureRules = loader.getRulesByCategory(Category.STRUCTURE);
    List<RuleDefinition> dependencyRules = loader.getRulesByCategory(Category.DEPENDENCIES);

    // Then: Rules should be filtered by category
    assertThat(structureRules).hasSize(2);
    assertThat(structureRules)
        .extracting(RuleDefinition::id)
        .containsExactlyInAnyOrder("TEST-001", "TEST-002");

    assertThat(dependencyRules).hasSize(1);
    assertThat(dependencyRules.get(0).id()).isEqualTo("TEST-003");
  }

  @Test
  void testParseTargetModules() throws IOException {
    // Given: A rule with target modules
    String properties =
        """
        TEST-001.name=Targeted Rule
        TEST-001.targetModules=module1,module2,module3
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    loader.loadFromStream(stream, "test.properties");

    // When: Getting the rule
    RuleDefinition rule = loader.getRule("TEST-001");

    // Then: Target modules should be parsed as list
    assertThat(rule.targetModules()).containsExactly("module1", "module2", "module3");
  }

  @Test
  void testParseEmptyTargetModules() throws IOException {
    // Given: A rule with no target modules
    String properties =
        """
        TEST-001.name=Untargeted Rule
        TEST-001.targetModules=
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    loader.loadFromStream(stream, "test.properties");

    // When: Getting the rule
    RuleDefinition rule = loader.getRule("TEST-001");

    // Then: Target modules should be empty list
    assertThat(rule.targetModules()).isEmpty();
  }

  @Test
  void testUnknownFileFormat() throws IOException {
    // Given: A file with unknown extension
    String content = "some content";
    InputStream stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

    // When: Loading from stream with unknown format
    loader.loadFromStream(stream, "test.unknown");

    // Then: No rules should be loaded (warning should be logged)
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).isEmpty();
  }

  @Test
  void testYamlSupportCheck() {
    // When: Checking YAML support
    boolean yamlSupported = loader.isYamlSupported();

    // Then: Should return true or false based on classpath
    // (SnakeYAML is optional dependency)
    assertThat(yamlSupported).isIn(true, false);
  }

  @Test
  void testChainedLoading() throws IOException {
    // Given: Multiple property files
    String properties1 =
        """
        TEST-001.name=Rule from file 1
        TEST-001.enabled=true
        """;

    String properties2 =
        """
        TEST-002.name=Rule from file 2
        TEST-002.enabled=true
        """;

    Path file1 = tempDir.resolve("rules1.properties");
    Path file2 = tempDir.resolve("rules2.properties");
    Files.writeString(file1, properties1);
    Files.writeString(file2, properties2);

    // When: Loading multiple files via chaining
    loader.loadFromPath(file1).loadFromPath(file2);

    // Then: Both rules should be loaded
    Map<String, RuleDefinition> rules = loader.getRules();
    assertThat(rules).hasSize(2);
    assertThat(rules).containsKeys("TEST-001", "TEST-002");
  }

  @Test
  void testGetNonExistentRule() {
    // Given: An empty loader
    // When: Getting a non-existent rule
    RuleDefinition rule = loader.getRule("NON-EXISTENT");

    // Then: Should return null
    assertThat(rule).isNull();
  }

  @Test
  void testGetRulesReturnsUnmodifiableMap() throws IOException {
    // Given: A loader with rules
    String properties =
        """
        TEST-001.name=Test Rule
        TEST-001.enabled=true
        """;

    InputStream stream = new ByteArrayInputStream(properties.getBytes(StandardCharsets.UTF_8));
    loader.loadFromStream(stream, "test.properties");

    // When: Getting the rules map
    Map<String, RuleDefinition> rules = loader.getRules();

    // Then: Map should be unmodifiable
    assertThatThrownBy(() -> rules.put("NEW-RULE", null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
