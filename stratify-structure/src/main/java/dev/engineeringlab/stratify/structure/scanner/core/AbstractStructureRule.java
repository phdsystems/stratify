package dev.engineeringlab.stratify.structure.scanner.core;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.rule.RuleDefinition;
import dev.engineeringlab.stratify.structure.scanner.common.rule.RuleLoader;
import dev.engineeringlab.stratify.structure.scanner.spi.Category;
import dev.engineeringlab.stratify.structure.scanner.spi.Scanner;
import dev.engineeringlab.stratify.structure.scanner.spi.Severity;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

/**
 * Abstract base class for module structure rules (MS-*).
 *
 * <p>Provides common functionality for structure rules including:
 *
 * <ul>
 *   <li>Rule metadata loaded from YAML configuration
 *   <li>Violation creation helpers
 *   <li>Applicability checking (parent vs leaf modules)
 * </ul>
 *
 * <p>Rule metadata (name, description, severity, fix, reference) is loaded from
 * module-structure-rules.yaml. YAML configuration is required.
 */
@Getter
public abstract class AbstractStructureRule implements Scanner<ModuleInfo> {

  /** Shared RuleLoader instance for all structure rules. */
  private static final RuleLoader RULE_LOADER =
      new RuleLoader().loadRequiredFromClasspath("module-structure-rules.yaml");

  protected static final int MAX_VIOLATIONS = 3;
  protected static final JavaParser JAVA_PARSER = new JavaParser();

  protected final String scannerId;
  protected final String name;
  protected final String description;
  protected final Severity severity;
  protected final String fix;
  protected final String reference;
  protected final RuleDefinition ruleDefinition;
  protected boolean enabled = true;

  /**
   * Creates a structure rule that loads metadata from YAML configuration.
   *
   * @param scannerId the scanner ID (e.g., "MS-001")
   * @throws IllegalStateException if rule is not defined in module-structure-rules.yaml
   */
  protected AbstractStructureRule(String scannerId) {
    this.scannerId = scannerId;
    this.ruleDefinition = RULE_LOADER.getRule(scannerId);

    if (ruleDefinition == null) {
      throw new IllegalStateException(
          "Rule "
              + scannerId
              + " not found in module-structure-rules.yaml. "
              + "All structure rules must be defined in YAML configuration.");
    }

    this.name = ruleDefinition.getName();
    this.description = ruleDefinition.getDescription();
    this.severity = mapSeverity(ruleDefinition.getSeverity());
    this.fix = ruleDefinition.getFix();
    this.reference = ruleDefinition.getReference();
    this.enabled = ruleDefinition.isEnabled();
  }

  /** Maps scanner-common Severity to scanner-spi Severity. */
  private Severity mapSeverity(
      dev.engineeringlab.stratify.structure.scanner.common.model.Severity scannerSeverity) {
    if (scannerSeverity == null) {
      return Severity.ERROR;
    }
    return switch (scannerSeverity) {
      case CRITICAL -> Severity.CRITICAL;
      case ERROR -> Severity.ERROR;
      case WARNING -> Severity.WARNING;
      case INFO -> Severity.INFO;
    };
  }

  @Override
  public Category getCategory() {
    return Category.STRUCTURE;
  }

  /** Creates a violation for this rule. */
  protected Violation createViolation(ModuleInfo module, String message) {
    return Violation.builder()
        .scannerId(scannerId)
        .scannerName(name)
        .target(module.getArtifactId())
        .message(message)
        .severity(severity)
        .category(getCategory())
        .fix(fix)
        .reference(reference)
        .build();
  }

  /** Creates a violation with a specific location. */
  protected Violation createViolation(ModuleInfo module, String message, String location) {
    return Violation.builder()
        .scannerId(scannerId)
        .scannerName(name)
        .target(module.getArtifactId())
        .message(message)
        .severity(severity)
        .category(getCategory())
        .fix(fix)
        .reference(reference)
        .location(location)
        .build();
  }

  /**
   * Checks if this rule applies to the given module. Override in subclasses to filter by module
   * type (parent, leaf, etc.)
   */
  protected abstract boolean appliesTo(ModuleInfo module);

  @Override
  public List<Violation> scan(ModuleInfo module) {
    if (!appliesTo(module)) {
      return List.of();
    }
    return doValidate(module);
  }

  /** Performs the actual validation. Called only if appliesTo returns true. */
  protected abstract List<Violation> doValidate(ModuleInfo module);

  /** Parses a Java source file and returns the CompilationUnit. */
  protected Optional<CompilationUnit> parseJavaFile(Path javaFile) {
    try {
      ParseResult<CompilationUnit> parseResult = JAVA_PARSER.parse(javaFile);
      if (parseResult.isSuccessful() && parseResult.getResult().isPresent()) {
        return parseResult.getResult();
      }
    } catch (IOException e) {
      // Skip file if can't read or parse
    }
    return Optional.empty();
  }
}
