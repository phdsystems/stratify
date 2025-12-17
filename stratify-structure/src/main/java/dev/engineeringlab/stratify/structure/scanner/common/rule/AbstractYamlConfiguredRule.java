package dev.engineeringlab.stratify.structure.scanner.common.rule;

import com.github.javaparser.ast.CompilationUnit;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.util.JavaParserUtil;
import dev.engineeringlab.stratify.structure.scanner.spi.Category;
import dev.engineeringlab.stratify.structure.scanner.spi.Scanner;
import dev.engineeringlab.stratify.structure.scanner.spi.Severity;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Abstract base class for rules that load configuration from YAML files.
 *
 * <p>Provides common functionality for YAML-configured rules including:
 *
 * <ul>
 *   <li>Rule metadata loaded from YAML configuration
 *   <li>Java file parsing with shared JavaParserUtil
 *   <li>Violation creation helpers
 *   <li>Common AST analysis utilities
 *   <li>Detection configuration accessors
 * </ul>
 *
 * <p>Subclasses must provide a {@link RuleLoader} instance that has loaded the appropriate YAML
 * configuration file for their rule category.
 *
 * @see RuleLoader
 * @see RuleDefinition
 */
@Getter
@Slf4j
public abstract class AbstractYamlConfiguredRule implements Scanner<ModuleInfo> {

  protected final String ruleId;
  protected final String name;
  protected final String description;
  protected final Severity severity;
  protected final String fix;
  protected final String reference;
  protected final RuleDefinition ruleDefinition;
  protected boolean enabled = true;

  /**
   * Creates a rule that loads metadata from YAML configuration.
   *
   * @param ruleId the rule ID (e.g., "CRE-001", "STR-001")
   * @param ruleLoader the RuleLoader containing the YAML configuration
   * @param yamlFileName the name of the YAML file for error messages
   * @throws IllegalStateException if rule is not defined in the YAML configuration
   */
  protected AbstractYamlConfiguredRule(String ruleId, RuleLoader ruleLoader, String yamlFileName) {
    this.ruleId = ruleId;
    this.ruleDefinition = ruleLoader.getRule(ruleId);

    if (ruleDefinition == null) {
      throw new IllegalStateException(
          "Rule "
              + ruleId
              + " not found in "
              + yamlFileName
              + ". "
              + "All rules must be defined in YAML configuration.");
    }

    this.name = ruleDefinition.getName();
    this.description = ruleDefinition.getDescription();
    this.severity = mapSeverity(ruleDefinition.getSeverity());
    this.fix = ruleDefinition.getFix();
    this.reference = ruleDefinition.getReference();
    this.enabled = ruleDefinition.isEnabled();
  }

  /** Maps scanner-common Severity to rule-engine-spi Severity. */
  private Severity mapSeverity(
      dev.engineeringlab.stratify.structure.scanner.common.model.Severity scannerSeverity) {
    if (scannerSeverity == null) {
      return Severity.WARNING;
    }
    return switch (scannerSeverity) {
      case CRITICAL -> Severity.CRITICAL;
      case ERROR -> Severity.ERROR;
      case WARNING -> Severity.WARNING;
      case INFO -> Severity.INFO;
    };
  }

  @Override
  public String getScannerId() {
    return ruleId;
  }

  @Override
  public Category getCategory() {
    return Category.QUALITY;
  }

  @Override
  public List<Violation> scan(ModuleInfo module) {
    if (!appliesTo(module)) {
      return List.of();
    }
    return doValidate(module);
  }

  /**
   * Checks if this rule applies to the given module. Override in subclasses to filter by module
   * type.
   *
   * @param module the module to check
   * @return true if this rule should validate the module
   */
  protected boolean appliesTo(ModuleInfo module) {
    return true;
  }

  /**
   * Performs the actual validation. Called only if appliesTo returns true.
   *
   * @param module the module to validate
   * @return list of violations found
   */
  protected abstract List<Violation> doValidate(ModuleInfo module);

  /**
   * Validates all Java files in the module using the provided validator.
   *
   * @param module the module to validate
   * @param validator function to validate each compilation unit
   * @return list of violations found
   */
  protected List<Violation> validateJavaFiles(ModuleInfo module, JavaFileValidator validator) {
    List<Violation> violations = new ArrayList<>();
    List<Path> javaFiles = collectJavaFiles(module);

    for (Path javaFile : javaFiles) {
      Optional<CompilationUnit> cuOpt = parseJavaFile(javaFile);
      if (cuOpt.isPresent()) {
        List<Violation> fileViolations = validator.validate(cuOpt.get(), javaFile);
        violations.addAll(fileViolations);
      }
    }

    return violations;
  }

  /**
   * Collects all Java source files from the module.
   *
   * @param module the module to collect files from
   * @return list of Java file paths
   */
  protected List<Path> collectJavaFiles(ModuleInfo module) {
    List<Path> javaFiles = new ArrayList<>();

    if (module.getSubModules() != null) {
      for (ModuleInfo.SubModuleInfo subModule : module.getSubModules().values()) {
        if (subModule.isExists() && subModule.getJavaSourceFiles() != null) {
          javaFiles.addAll(subModule.getJavaSourceFiles());
        }
      }
    }

    return javaFiles;
  }

  /**
   * Parses a Java file into a CompilationUnit using shared JavaParserUtil.
   *
   * @param file the file to parse
   * @return Optional containing the CompilationUnit, or empty if parsing failed
   */
  protected Optional<CompilationUnit> parseJavaFile(Path file) {
    return JavaParserUtil.parse(file);
  }

  /**
   * Creates a violation for this rule.
   *
   * @param target the target entity (e.g., class name)
   * @param message the violation message
   * @return a new Violation instance
   */
  protected Violation createViolation(String target, String message) {
    return Violation.builder()
        .scannerId(ruleId)
        .scannerName(name)
        .target(target)
        .message(message)
        .severity(severity)
        .category(getCategory())
        .fix(fix)
        .reference(reference)
        .build();
  }

  /**
   * Creates a violation with a specific location.
   *
   * @param target the target entity (e.g., class name)
   * @param message the violation message
   * @param location the location string (e.g., file path)
   * @return a new Violation instance
   */
  protected Violation createViolation(String target, String message, String location) {
    return Violation.builder()
        .scannerId(ruleId)
        .scannerName(name)
        .target(target)
        .message(message)
        .severity(severity)
        .category(getCategory())
        .fix(fix)
        .reference(reference)
        .location(location)
        .build();
  }

  /**
   * Creates a violation with a Path location.
   *
   * @param target the target entity (e.g., class name)
   * @param message the violation message
   * @param location the file path
   * @return a new Violation instance
   */
  protected Violation createViolation(String target, String message, Path location) {
    return createViolation(target, message, location.toString());
  }

  /**
   * Gets a threshold value from YAML configuration.
   *
   * <p>Note: Thresholds are not currently supported in RuleDefinition. This method returns the
   * default value.
   *
   * @param key the threshold key
   * @param defaultValue the default value if not configured
   * @return the threshold value (currently always the default)
   */
  protected int getThreshold(String key, int defaultValue) {
    // Thresholds are not yet supported in RuleDefinition
    return defaultValue;
  }

  /**
   * Gets a detection configuration value from YAML.
   *
   * <p>Note: Generic detection config is not currently supported. This method returns the default
   * value.
   *
   * @param key the configuration key
   * @param defaultValue the default value if not configured
   * @param <T> the type of the configuration value
   * @return the configuration value (currently always the default)
   */
  @SuppressWarnings("unchecked")
  protected <T> T getDetectionConfig(String key, T defaultValue) {
    // Generic detection config is not yet supported
    return defaultValue;
  }

  /**
   * Gets a list from detection configuration.
   *
   * <p>Supports the following known list fields:
   *
   * <ul>
   *   <li>pathPatterns - patterns for file paths
   *   <li>filePatterns - patterns for file names
   *   <li>vendorPatterns - patterns for vendor directories
   * </ul>
   *
   * @param key the list key
   * @param defaultValue the default value if not configured
   * @return the list value, or the default if not found
   */
  @SuppressWarnings("unchecked")
  protected List<String> getDetectionList(String key, List<String> defaultValue) {
    if (ruleDefinition != null && ruleDefinition.getDetection() != null) {
      var detection = ruleDefinition.getDetection();
      // Check known list fields
      if ("pathPatterns".equals(key) && detection.getPathPatterns() != null) {
        return detection.getPathPatterns();
      }
      if ("filePatterns".equals(key) && detection.getFilePatterns() != null) {
        return detection.getFilePatterns();
      }
      if ("vendorPatterns".equals(key) && detection.getVendorPatterns() != null) {
        return detection.getVendorPatterns();
      }
    }
    return defaultValue;
  }

  /**
   * Gets a boolean from detection configuration.
   *
   * <p>Supports the following known boolean fields from classPatterns:
   *
   * <ul>
   *   <li>detectRecords - whether to detect record classes
   *   <li>detectInterfaces - whether to detect interfaces
   *   <li>detectEnums - whether to detect enums
   * </ul>
   *
   * @param key the boolean key
   * @param defaultValue the default value if not configured
   * @return the boolean value, or the default if not found
   */
  protected boolean getDetectionBoolean(String key, boolean defaultValue) {
    if (ruleDefinition != null && ruleDefinition.getDetection() != null) {
      var detection = ruleDefinition.getDetection();
      var classPatterns = detection.getClassPatterns();
      if (classPatterns != null) {
        if ("detectRecords".equals(key)) {
          return classPatterns.isDetectRecords();
        }
        if ("detectInterfaces".equals(key)) {
          return classPatterns.isDetectInterfaces();
        }
        if ("detectEnums".equals(key)) {
          return classPatterns.isDetectEnums();
        }
      }
    }
    return defaultValue;
  }

  /** Functional interface for validating a Java file. */
  @FunctionalInterface
  protected interface JavaFileValidator {
    /**
     * Validates a compilation unit.
     *
     * @param cu the parsed compilation unit
     * @param file the source file path
     * @return list of violations found
     */
    List<Violation> validate(CompilationUnit cu, Path file);
  }
}
