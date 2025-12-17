package dev.engineeringlab.stratify.structure.scanner.common.rule;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import dev.engineeringlab.stratify.structure.scanner.common.config.ComplianceConfig;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.Violation;
import dev.engineeringlab.stratify.structure.scanner.common.util.JavaParserUtil;
import dev.engineeringlab.stratify.structure.scanner.common.util.PomScanner;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.maven.model.Model;

/**
 * Generic rule engine that evaluates YAML-defined rules against modules.
 *
 * <p>The rule engine supports different types of rules:
 *
 * <ul>
 *   <li>File/class-based rules (NC-027, NC-028) - detect patterns in Java files
 *   <li>Dependency rules (DP-009) - check POM dependencies
 *   <li>Module structure rules - validate module organization
 * </ul>
 *
 * <p>Rules are loaded from YAML and evaluated dynamically based on their detection criteria.
 * Placeholder variables like {groupId}, {namespace}, {base} are resolved from configuration.
 */
public class RuleEngine {

  private static final int MAX_VIOLATIONS_TO_LIST = 3;

  private final RuleLoader ruleLoader;
  private final PomScanner pomScanner;
  private final ComplianceConfig config;

  /**
   * Creates a new rule engine with the given rule loader and default configuration.
   *
   * @param ruleLoader the loader containing rule definitions
   */
  public RuleEngine(RuleLoader ruleLoader) {
    this.ruleLoader = ruleLoader;
    this.pomScanner = new PomScanner();
    this.config = new ComplianceConfig();
  }

  /**
   * Creates a new rule engine with the given rule loader and configuration.
   *
   * @param ruleLoader the loader containing rule definitions
   * @param config the compliance configuration
   */
  public RuleEngine(RuleLoader ruleLoader, ComplianceConfig config) {
    this.ruleLoader = ruleLoader;
    this.pomScanner = new PomScanner();
    this.config = config != null ? config : new ComplianceConfig();
  }

  /**
   * Creates a new rule engine with the given rule loader, POM scanner, and configuration.
   *
   * @param ruleLoader the loader containing rule definitions
   * @param pomScanner the POM scanner for dependency analysis
   * @param config the compliance configuration
   */
  public RuleEngine(RuleLoader ruleLoader, PomScanner pomScanner, ComplianceConfig config) {
    this.ruleLoader = ruleLoader;
    this.pomScanner = pomScanner;
    this.config = config != null ? config : new ComplianceConfig();
  }

  /**
   * Evaluates all enabled rules against a module.
   *
   * @param moduleInfo the module to evaluate
   * @return list of violations found
   */
  public List<Violation> evaluate(ModuleInfo moduleInfo) {
    List<Violation> violations = new ArrayList<>();

    for (RuleDefinition rule : ruleLoader.getEnabledRules()) {
      Violation violation = evaluateRule(rule, moduleInfo);
      if (violation != null) {
        violations.add(violation);
      }
    }

    return violations;
  }

  /**
   * Evaluates a specific rule against a module.
   *
   * @param ruleId the rule ID to evaluate
   * @param moduleInfo the module to evaluate
   * @return violation if found, null otherwise
   */
  public Violation evaluateRule(String ruleId, ModuleInfo moduleInfo) {
    RuleDefinition rule = ruleLoader.getRule(ruleId);
    if (rule == null) {
      return null;
    }
    return evaluateRule(rule, moduleInfo);
  }

  /** Evaluates a rule definition against a module. */
  private Violation evaluateRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    if (!rule.isEnabled()) {
      return null;
    }

    RuleDefinition.DetectionCriteria detection = rule.getDetection();
    if (detection == null) {
      return null;
    }

    // Determine rule type and evaluate accordingly
    if (detection.getDependencyPatterns() != null
        && (!detection.getDependencyPatterns().getMustNotContain().isEmpty()
            || !detection.getDependencyPatterns().getMustContain().isEmpty()
            || !detection.getDependencyPatterns().getRequiresSiblings().isEmpty()
            || detection.getDependencyPatterns().getScope() != null)) {
      return evaluateDependencyRule(rule, moduleInfo);
    }

    if (!detection.getVendorPatterns().isEmpty()) {
      return evaluateVendorRule(rule, moduleInfo);
    }

    if (detection.getClassPatterns() != null || !detection.getPathPatterns().isEmpty()) {
      return evaluateClassRule(rule, moduleInfo);
    }

    if (detection.getPomPatterns() != null) {
      return evaluatePomRule(rule, moduleInfo);
    }

    if (detection.getNamingPatterns() != null) {
      return evaluateNamingRule(rule, moduleInfo);
    }

    return null;
  }

  /**
   * Evaluates a naming convention rule (NC-*).
   *
   * <p>Naming rules check Maven coordinates, Java packages, and class names against patterns
   * defined in YAML configuration.
   */
  private Violation evaluateNamingRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    RuleDefinition.NamingPatterns patterns = rule.getDetection().getNamingPatterns();

    // Scope-based rules (e.g., NC-001, NC-002 for facade)
    String scope = patterns.getScope();
    if (scope != null) {
      return evaluateScopedNamingRule(rule, moduleInfo, patterns, scope);
    }

    // GroupId convention (NC-003)
    if (patterns.getGroupId() != null) {
      return evaluateGroupIdRule(rule, moduleInfo, patterns);
    }

    // ArtifactId patterns (NC-005, NC-008)
    if (patterns.getArtifactIdMustNotStartWith() != null
        || patterns.getArtifactIdMustNotContain() != null) {
      return evaluateArtifactIdRule(rule, moduleInfo, patterns);
    }

    // Parent module naming (NC-016)
    if (patterns.getParentModuleMustEndWith() != null) {
      return evaluateParentNamingRule(rule, moduleInfo, patterns);
    }

    // Submodule suffixes (NC-018)
    if (!patterns.getSubmodulesMustHaveSuffix().isEmpty()) {
      return evaluateSubmoduleSuffixRule(rule, moduleInfo, patterns);
    }

    // Module names must not end with (NC-019)
    if (!patterns.getModulesMustNotEndWith().isEmpty()) {
      return evaluateModuleNameRule(rule, moduleInfo, patterns);
    }

    // Aggregator suffix (NC-030)
    if (!patterns.getAggregatorMustEndWith().isEmpty()) {
      return evaluateAggregatorSuffixRule(rule, moduleInfo, patterns);
    }

    // Package patterns (NC-004, NC-007, NC-021)
    if (patterns.getPackagePrefix() != null
        || patterns.getPackagesMustStartWith() != null
        || patterns.getPackagesMustContain() != null) {
      return evaluatePackageRule(rule, moduleInfo, patterns);
    }

    // Singular names (NC-010, NC-011, NC-020)
    if (patterns.isClassesMustBeSingular()
        || patterns.isPackagesMustBeSingular()
        || patterns.isArtifactIdMustBeSingular()) {
      return evaluateSingularNamesRule(rule, moduleInfo, patterns);
    }

    // Models/Exceptions placement (NC-027, NC-028)
    if (patterns.getModelsMustBeIn() != null || patterns.getExceptionsMustBeIn() != null) {
      return evaluatePlacementRule(rule, moduleInfo, patterns);
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - no applicable naming checks");
  }

  /** Evaluates a scoped naming rule (e.g., NC-001 for facade). */
  private Violation evaluateScopedNamingRule(
      RuleDefinition rule,
      ModuleInfo moduleInfo,
      RuleDefinition.NamingPatterns patterns,
      String scope) {

    ModuleInfo.SubModuleInfo scopedModule = getScopedModule(moduleInfo, scope);
    if (scopedModule == null || !scopedModule.isExists()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "No " + scope + " module found - skipping " + rule.getName());
    }

    String artifactId = scopedModule.getArtifactId();
    Path modulePath = scopedModule.getPath();

    // NC-001: mustNotEndWith for artifactId
    if (patterns.getMustNotEndWith() != null && artifactId != null) {
      if (artifactId.endsWith(patterns.getMustNotEndWith())) {
        return Violation.builder()
            .ruleId(rule.getId())
            .category(rule.getCategory())
            .severity(rule.getSeverity())
            .description(rule.getDescription())
            .location(modulePath != null ? modulePath.resolve("pom.xml").toString() : null)
            .expected("ArtifactId must NOT end with '" + patterns.getMustNotEndWith() + "'")
            .found("ArtifactId is '" + artifactId + "'")
            .reason(rule.getReason())
            .fix(rule.getFix())
            .reference(rule.getReference())
            .build();
      }
    }

    // NC-002: directoryMustEndWith
    if (patterns.getDirectoryMustEndWith() != null && modulePath != null) {
      String dirName = modulePath.getFileName().toString();
      if (!dirName.endsWith(patterns.getDirectoryMustEndWith())) {
        return Violation.builder()
            .ruleId(rule.getId())
            .category(rule.getCategory())
            .severity(rule.getSeverity())
            .description(rule.getDescription())
            .location(modulePath.toString())
            .expected("Directory must end with '" + patterns.getDirectoryMustEndWith() + "'")
            .found("Directory is '" + dirName + "'")
            .reason(rule.getReason())
            .fix(rule.getFix())
            .reference(rule.getReference())
            .build();
      }
    }

    return Violation.passed(
        rule.getId(),
        rule.getCategory(),
        rule.getName() + " - " + scope + " module naming is correct");
  }

  /** Evaluates groupId convention rule (NC-003). */
  private Violation evaluateGroupIdRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    String expectedGroupId = patterns.getGroupId();
    String actualGroupId = moduleInfo.getGroupId();

    if (actualGroupId == null || !actualGroupId.equals(expectedGroupId)) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("GroupId must be '" + expectedGroupId + "'")
          .found("GroupId is '" + actualGroupId + "'")
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - groupId is correct");
  }

  /** Evaluates artifactId pattern rules (NC-005, NC-008). */
  private Violation evaluateArtifactIdRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    String artifactId = moduleInfo.getArtifactId();
    if (artifactId == null) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "No artifactId found - skipping " + rule.getName());
    }

    // Check mustNotStartWith
    if (patterns.getArtifactIdMustNotStartWith() != null
        && artifactId.startsWith(patterns.getArtifactIdMustNotStartWith())) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected(
              "ArtifactId must NOT start with '" + patterns.getArtifactIdMustNotStartWith() + "'")
          .found("ArtifactId is '" + artifactId + "'")
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    // Check mustNotContain
    if (patterns.getArtifactIdMustNotContain() != null
        && artifactId.contains(patterns.getArtifactIdMustNotContain())) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("ArtifactId must NOT contain '" + patterns.getArtifactIdMustNotContain() + "'")
          .found("ArtifactId is '" + artifactId + "'")
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - artifactId is correct");
  }

  /** Evaluates parent module naming rule (NC-016). */
  private Violation evaluateParentNamingRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "Not a parent module - skipping " + rule.getName());
    }

    String artifactId = moduleInfo.getArtifactId();
    String requiredSuffix = patterns.getParentModuleMustEndWith();

    if (artifactId != null && !artifactId.endsWith(requiredSuffix)) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Parent artifactId must end with '" + requiredSuffix + "'")
          .found("ArtifactId is '" + artifactId + "'")
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - parent naming is correct");
  }

  /** Evaluates submodule suffix rule (NC-018). */
  private Violation evaluateSubmoduleSuffixRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "Not a parent module - skipping " + rule.getName());
    }

    List<String> requiredSuffixes = patterns.getSubmodulesMustHaveSuffix();
    List<String> violations = new ArrayList<>();

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      String artifactId = subModule.getArtifactId();
      if (artifactId == null) continue;

      boolean hasSuffix = requiredSuffixes.stream().anyMatch(artifactId::endsWith);
      if (!hasSuffix) {
        violations.add(artifactId);
      }
    }

    if (!violations.isEmpty()) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Submodules must have suffix: " + String.join(", ", requiredSuffixes))
          .found("Invalid: " + String.join(", ", violations))
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - submodule suffixes are correct");
  }

  /** Evaluates module names must not end with rule (NC-019). */
  private Violation evaluateModuleNameRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "Not a parent module - skipping " + rule.getName());
    }

    List<String> forbiddenSuffixes = patterns.getModulesMustNotEndWith();
    List<String> violations = new ArrayList<>();

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      String artifactId = subModule.getArtifactId();
      if (artifactId == null) continue;

      for (String suffix : forbiddenSuffixes) {
        if (artifactId.endsWith(suffix)) {
          violations.add(artifactId + " (ends with " + suffix + ")");
          break;
        }
      }
    }

    if (!violations.isEmpty()) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Modules must NOT end with: " + String.join(", ", forbiddenSuffixes))
          .found("Violations: " + String.join(", ", violations))
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - module names are correct");
  }

  /** Evaluates aggregator suffix rule (NC-030). */
  private Violation evaluateAggregatorSuffixRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {

    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Not an aggregator module - skipping " + rule.getName());
    }

    String artifactId = moduleInfo.getArtifactId();
    List<String> requiredSuffixes = patterns.getAggregatorMustEndWith();

    boolean hasSuffix = requiredSuffixes.stream().anyMatch(artifactId::endsWith);
    if (!hasSuffix) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Aggregator artifactId must end with: " + String.join(" or ", requiredSuffixes))
          .found("ArtifactId is '" + artifactId + "'")
          .reason(rule.getReason())
          .fix(rule.getFix())
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - aggregator suffix is correct");
  }

  /** Evaluates package naming rules (NC-004, NC-007, NC-021). */
  private Violation evaluatePackageRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {
    // This would require scanning Java source files for package declarations
    // For now, return passed - the Java rule classes handle this
    return Violation.passed(
        rule.getId(),
        rule.getCategory(),
        rule.getName() + " - delegated to Java rule implementation");
  }

  /** Evaluates singular naming rules (NC-010, NC-011, NC-020). */
  private Violation evaluateSingularNamesRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {
    // This would require analyzing class/package names for plurals
    // For now, return passed - the Java rule classes handle this
    return Violation.passed(
        rule.getId(),
        rule.getCategory(),
        rule.getName() + " - delegated to Java rule implementation");
  }

  /** Evaluates placement rules (NC-027, NC-028). */
  private Violation evaluatePlacementRule(
      RuleDefinition rule, ModuleInfo moduleInfo, RuleDefinition.NamingPatterns patterns) {
    // This would require analyzing class locations and types
    // For now, return passed - the Java rule classes handle this
    return Violation.passed(
        rule.getId(),
        rule.getCategory(),
        rule.getName() + " - delegated to Java rule implementation");
  }

  /** Evaluates a class/file-based rule (e.g., NC-027, NC-028). */
  private Violation evaluateClassRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    List<String> foundViolations = new ArrayList<>();
    RuleDefinition.DetectionCriteria detection = rule.getDetection();

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      if (!subModule.isExists() || subModule.getJavaSourceFiles() == null) {
        continue;
      }

      String artifactId = subModule.getArtifactId();
      if (artifactId == null) {
        continue;
      }

      // Check if this submodule matches target patterns
      if (!matchesTargetModules(artifactId, rule.getTargetModules())) {
        continue;
      }

      for (Path javaFile : subModule.getJavaSourceFiles()) {
        String pathStr = javaFile.toString().replace("\\", "/");

        // Check path patterns
        boolean matchesPath =
            detection.getPathPatterns().isEmpty()
                || detection.getPathPatterns().stream().anyMatch(pathStr::contains);

        // Check file patterns
        boolean matchesFile =
            detection.getFilePatterns().isEmpty()
                || detection.getFilePatterns().stream()
                    .anyMatch(pattern -> matchesFilePattern(javaFile, pattern));

        if (!matchesPath && !matchesFile) {
          continue;
        }

        // Parse and check class patterns
        if (detection.getClassPatterns() != null) {
          List<String> classViolations =
              checkClassPatterns(javaFile, artifactId, detection.getClassPatterns());
          foundViolations.addAll(classViolations);
        }
      }
    }

    if (foundViolations.isEmpty()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "No violations found for " + rule.getName());
    }

    return buildViolation(rule, moduleInfo, foundViolations);
  }

  /** Checks class patterns against a Java file. */
  private List<String> checkClassPatterns(
      Path javaFile, String artifactId, RuleDefinition.ClassPatterns patterns) {
    List<String> violations = new ArrayList<>();

    Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(javaFile);
    if (cuOpt.isEmpty()) {
      return violations;
    }

    CompilationUnit cu = cuOpt.get();

    // Check for records
    if (patterns.isDetectRecords()) {
      cu.findAll(com.github.javaparser.ast.body.RecordDeclaration.class)
          .forEach(record -> violations.add(artifactId + ": " + record.getNameAsString()));
    }

    // Check classes
    for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
      if (clazz.isInterface() && !patterns.isDetectInterfaces()) {
        continue;
      }

      String className = clazz.getNameAsString();

      // Check annotations
      boolean hasAnnotation =
          patterns.getAnnotations().stream()
              .anyMatch(ann -> clazz.getAnnotationByName(ann).isPresent());

      // Check suffixes
      boolean hasSuffix = patterns.getSuffixes().stream().anyMatch(className::endsWith);

      // Check extends
      boolean extendsMatch =
          patterns.getExtendsClasses().isEmpty()
              || patterns.getExtendsClasses().stream()
                  .anyMatch(parent -> JavaParserUtil.extendsClass(clazz, parent));

      if (hasAnnotation || hasSuffix) {
        if (patterns.getExtendsClasses().isEmpty() || extendsMatch) {
          violations.add(artifactId + ": " + className);
        }
      } else if (!patterns.getExtendsClasses().isEmpty() && extendsMatch) {
        violations.add(artifactId + ": " + className);
      }
    }

    return violations;
  }

  /** Evaluates a dependency-based rule (e.g., DP-001 to DP-010). */
  private Violation evaluateDependencyRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    RuleDefinition.DependencyPatterns depPatterns = rule.getDetection().getDependencyPatterns();
    String scope = depPatterns.getScope();
    String condition = depPatterns.getCondition();

    // Check condition before evaluating
    if (condition != null && !checkCondition(condition, moduleInfo)) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Condition not met (" + condition + ") - skipping " + rule.getName());
    }

    // If scope is specified, find that specific submodule
    if (scope != null) {
      return evaluateScopedDependencyRule(rule, moduleInfo, depPatterns, scope);
    }

    // First, check if the module itself matches targetModules (for leaf modules like plugins)
    String moduleArtifactId = moduleInfo.getArtifactId();
    if (moduleArtifactId != null
        && matchesTargetModules(moduleArtifactId, rule.getTargetModules())
        && moduleInfo.getPomModel() != null) {
      return evaluateDependencyPatternsForPom(
          rule,
          depPatterns,
          moduleInfo.getPomModel(),
          moduleInfo.getBasePath().resolve("pom.xml").toString(),
          moduleInfo,
          moduleArtifactId);
    }

    // Otherwise, find the target submodule
    ModuleInfo.SubModuleInfo targetModule = null;
    String targetArtifactId = null;

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      String artifactId = subModule.getArtifactId();
      if (artifactId != null && matchesTargetModules(artifactId, rule.getTargetModules())) {
        targetModule = subModule;
        targetArtifactId = artifactId;
        break;
      }
    }

    if (targetModule == null || targetModule.getPomModel() == null) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "Target module not found - skipping " + rule.getName());
    }

    return evaluateDependencyPatternsForPom(
        rule,
        depPatterns,
        targetModule.getPomModel(),
        targetModule.getPath().resolve("pom.xml").toString(),
        moduleInfo,
        targetArtifactId);
  }

  /** Evaluates a scope-based dependency rule (e.g., DP-001 to DP-006). */
  private Violation evaluateScopedDependencyRule(
      RuleDefinition rule,
      ModuleInfo moduleInfo,
      RuleDefinition.DependencyPatterns depPatterns,
      String scope) {

    // Find the scoped submodule
    ModuleInfo.SubModuleInfo scopedModule = getScopedModule(moduleInfo, scope);
    if (scopedModule == null || scopedModule.getPomModel() == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          scope.toUpperCase() + " module not found - skipping " + rule.getName());
    }

    String targetArtifactId = scopedModule.getArtifactId();
    String pomLocation = scopedModule.getPath().resolve("pom.xml").toString();
    Model pomModel = scopedModule.getPomModel();

    // Check requiresSiblings patterns
    List<String> requiresSiblings = depPatterns.getRequiresSiblings();
    if (requiresSiblings != null && !requiresSiblings.isEmpty()) {
      for (String siblingPattern : requiresSiblings) {
        String resolvedSibling = resolvePattern(siblingPattern, moduleInfo, targetArtifactId);
        if (!pomScanner.hasDependency(pomModel, resolvedSibling)) {
          return Violation.builder()
              .ruleId(rule.getId())
              .category(rule.getCategory())
              .severity(rule.getSeverity())
              .description(rule.getDescription())
              .location(pomLocation)
              .expected("Required sibling dependency: " + resolvedSibling)
              .found("Dependency not found in " + targetArtifactId)
              .reason(rule.getReason())
              .fix(resolvePattern(rule.getFix(), moduleInfo, targetArtifactId))
              .reference(rule.getReference())
              .build();
        }
      }
    }

    // Evaluate standard patterns
    return evaluateDependencyPatternsForPom(
        rule, depPatterns, pomModel, pomLocation, moduleInfo, targetArtifactId);
  }

  /** Gets the submodule for a given scope (api, core, facade, spi). */
  private ModuleInfo.SubModuleInfo getScopedModule(ModuleInfo moduleInfo, String scope) {
    switch (scope.toLowerCase()) {
      case "api":
        return moduleInfo.hasApiModule() ? moduleInfo.getApiModule() : null;
      case "core":
        return moduleInfo.hasCoreModule() ? moduleInfo.getCoreModule() : null;
      case "facade":
        return moduleInfo.hasFacadeModule() ? moduleInfo.getFacadeModule() : null;
      case "spi":
        return moduleInfo.hasSpiModule() ? moduleInfo.getSpiModule() : null;
      default:
        return null;
    }
  }

  /** Checks if a condition is met. */
  private boolean checkCondition(String condition, ModuleInfo moduleInfo) {
    switch (condition.toLowerCase()) {
      case "hasspi":
        return moduleInfo.hasSpiModule();
      case "hasapi":
        return moduleInfo.hasApiModule();
      case "hascore":
        return moduleInfo.hasCoreModule();
      case "hasfacade":
        return moduleInfo.hasFacadeModule();
      default:
        return true; // Unknown condition - assume met
    }
  }

  /** Evaluates dependency patterns against a POM model. */
  private Violation evaluateDependencyPatternsForPom(
      RuleDefinition rule,
      RuleDefinition.DependencyPatterns depPatterns,
      Model pomModel,
      String pomLocation,
      ModuleInfo moduleInfo,
      String targetArtifactId) {

    List<String> exceptions = depPatterns.getExceptions();

    // Check mustNotContain patterns
    for (String pattern : depPatterns.getMustNotContain()) {
      String resolvedPattern = resolvePattern(pattern, moduleInfo, targetArtifactId);
      String foundDep = findMatchingDependency(pomModel, resolvedPattern, exceptions);
      if (foundDep != null) {
        return Violation.builder()
            .ruleId(rule.getId())
            .category(rule.getCategory())
            .severity(rule.getSeverity())
            .description(rule.getDescription())
            .location(pomLocation)
            .expected(rule.getDescription())
            .found("Forbidden dependency: " + foundDep)
            .reason(rule.getReason())
            .fix(resolvePattern(rule.getFix(), moduleInfo, targetArtifactId))
            .reference(rule.getReference())
            .build();
      }
    }

    // Check mustContain patterns (with wildcard support)
    for (String pattern : depPatterns.getMustContain()) {
      String resolvedPattern = resolvePattern(pattern, moduleInfo, targetArtifactId);
      if (!hasDependencyMatching(pomModel, resolvedPattern)) {
        return Violation.builder()
            .ruleId(rule.getId())
            .category(rule.getCategory())
            .severity(rule.getSeverity())
            .description(rule.getDescription())
            .location(pomLocation)
            .expected("Required dependency matching: " + resolvedPattern)
            .found("Dependency not found")
            .reason(rule.getReason())
            .fix(resolvePattern(rule.getFix(), moduleInfo, targetArtifactId))
            .reference(rule.getReference())
            .build();
      }
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - no violations found");
  }

  /** Checks if POM has a dependency matching the pattern (supports wildcards). */
  private boolean hasDependencyMatching(Model pomModel, String pattern) {
    if (pomModel == null || pomModel.getDependencies() == null) {
      return false;
    }

    for (org.apache.maven.model.Dependency dep : pomModel.getDependencies()) {
      String artifactId = dep.getArtifactId();
      if (artifactId == null) continue;

      if (matchesWildcardPattern(artifactId, pattern)) {
        return true;
      }
    }
    return false;
  }

  /** Matches an artifact ID against a wildcard pattern. Supports: *suffix, prefix*, *contains* */
  private boolean matchesWildcardPattern(String artifactId, String pattern) {
    if (pattern.startsWith("*") && pattern.endsWith("*")) {
      // Contains pattern like "*logging*"
      String contains = pattern.substring(1, pattern.length() - 1);
      return artifactId.contains(contains);
    } else if (pattern.startsWith("*")) {
      // Suffix pattern like "*-core"
      return artifactId.endsWith(pattern.substring(1));
    } else if (pattern.endsWith("*")) {
      // Prefix pattern like "spring-*"
      return artifactId.startsWith(pattern.substring(0, pattern.length() - 1));
    } else {
      // Exact match
      return artifactId.equals(pattern);
    }
  }

  /**
   * Finds a dependency matching the pattern, excluding exceptions. Pattern can be exact match or
   * wildcard (e.g., "*-core").
   *
   * @return the matching dependency artifactId, or null if none found
   */
  private String findMatchingDependency(Model pomModel, String pattern, List<String> exceptions) {
    if (pomModel == null || pomModel.getDependencies() == null) {
      return null;
    }

    for (org.apache.maven.model.Dependency dep : pomModel.getDependencies()) {
      String artifactId = dep.getArtifactId();
      if (artifactId == null) continue;

      // Check if dependency matches pattern
      boolean matches;
      if (pattern.startsWith("*")) {
        // Wildcard suffix pattern like "*-core"
        String suffix = pattern.substring(1);
        matches = artifactId.endsWith(suffix);
      } else if (pattern.endsWith("*")) {
        // Wildcard prefix pattern like "spring-*"
        String prefix = pattern.substring(0, pattern.length() - 1);
        matches = artifactId.startsWith(prefix);
      } else {
        // Exact match
        matches = artifactId.equals(pattern);
      }

      if (matches) {
        // Check if it's an exception
        boolean isException =
            exceptions != null
                && exceptions.stream()
                    .anyMatch(
                        exc ->
                            artifactId.equals(exc) || artifactId.matches(exc.replace("*", ".*")));
        if (!isException) {
          return artifactId;
        }
      }
    }

    return null;
  }

  /**
   * Evaluates a POM structure rule (e.g., MS-010 to MS-017).
   *
   * <p>Supports:
   *
   * <ul>
   *   <li>Module order patterns - e.g., ensuring *-common is first (MS-010)
   *   <li>Require parent - e.g., leaf modules must have parent (MS-011)
   *   <li>Parent artifactId suffix - e.g., parent must end with -parent (MS-012)
   *   <li>Require no parent - e.g., parent modules should not have parent (MS-013)
   *   <li>No source code - e.g., aggregator modules have no src/ (MS-014)
   *   <li>No dependencies - e.g., aggregators use dependencyManagement only (MS-015)
   *   <li>All submodules listed - e.g., all pom.xml subdirs in modules (MS-016)
   *   <li>Pure aggregator suffix - e.g., pure aggregators end with -aggregator (MS-017)
   * </ul>
   */
  private Violation evaluatePomRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    RuleDefinition.PomPatterns pomPatterns = rule.getDetection().getPomPatterns();
    if (pomPatterns == null) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "No POM patterns to check for " + rule.getName());
    }

    // Check requireParent (MS-011) - applies to leaf modules
    if (pomPatterns.isRequireParent()) {
      return evaluateRequireParent(rule, moduleInfo);
    }

    // Check requireNoParent (MS-013) - applies to parent modules
    if (pomPatterns.isRequireNoParent()) {
      return evaluateRequireNoParent(rule, moduleInfo);
    }

    // Check parentArtifactIdSuffix (MS-012) - applies to leaf modules
    if (pomPatterns.getParentArtifactIdSuffix() != null) {
      return evaluateParentArtifactIdSuffix(
          rule, moduleInfo, pomPatterns.getParentArtifactIdSuffix());
    }

    // Check noSourceCode (MS-014) - applies to aggregator modules
    if (pomPatterns.isNoSourceCode()) {
      return evaluateNoSourceCode(rule, moduleInfo);
    }

    // Check noDependencies (MS-015) - applies to aggregator modules
    if (pomPatterns.isNoDependencies()) {
      return evaluateNoDependencies(rule, moduleInfo);
    }

    // Check allSubmodulesListed (MS-016) - applies to aggregator/parent modules
    if (pomPatterns.isAllSubmodulesListed()) {
      return evaluateAllSubmodulesListed(rule, moduleInfo);
    }

    // Check pureAggregatorSuffix (MS-017) - applies to pure aggregator modules
    if (pomPatterns.getPureAggregatorSuffix() != null) {
      return evaluatePureAggregatorSuffix(rule, moduleInfo, pomPatterns.getPureAggregatorSuffix());
    }

    // Check artifactIdMustEndWith (NC-030) - applies to aggregator modules
    if (pomPatterns.getArtifactIdMustEndWith() != null
        && !pomPatterns.getArtifactIdMustEndWith().isEmpty()) {
      return evaluateArtifactIdMustEndWith(
          rule, moduleInfo, pomPatterns.getArtifactIdMustEndWith());
    }

    // Check module order pattern (MS-010) - applies to parent modules
    List<String> moduleOrderPatterns = pomPatterns.getModuleOrder();
    if (moduleOrderPatterns != null && !moduleOrderPatterns.isEmpty()) {
      return evaluateModuleOrder(rule, moduleInfo, moduleOrderPatterns);
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - no applicable checks");
  }

  /** Evaluates MS-011: Leaf modules must have a parent POM. */
  private Violation evaluateRequireParent(RuleDefinition rule, ModuleInfo moduleInfo) {
    // This rule applies to leaf modules, not parent modules
    if (moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - parent modules don't need a parent check");
    }

    // Check if module has a parent declared
    Model pomModel = moduleInfo.getPomModel();
    if (pomModel == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no POM model available");
    }

    if (pomModel.getParent() == null) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Leaf module must declare a <parent> section")
          .found("No parent declared in " + moduleInfo.getArtifactId())
          .reason(rule.getReason())
          .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - parent is declared");
  }

  /** Evaluates MS-012: Leaf module parent artifactId must end with a specific suffix. */
  private Violation evaluateParentArtifactIdSuffix(
      RuleDefinition rule, ModuleInfo moduleInfo, String requiredSuffix) {
    // This rule applies to leaf modules, not parent modules
    if (moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "Skipping " + rule.getName() + " - not a leaf module");
    }

    Model pomModel = moduleInfo.getPomModel();
    if (pomModel == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no POM model available");
    }

    if (pomModel.getParent() == null) {
      // No parent - MS-011 will catch this
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no parent declared (checked by MS-011)");
    }

    String parentArtifactId = pomModel.getParent().getArtifactId();
    if (parentArtifactId == null || !parentArtifactId.endsWith(requiredSuffix)) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Parent artifactId must end with '" + requiredSuffix + "'")
          .found("Parent artifactId is '" + parentArtifactId + "'")
          .reason(rule.getReason())
          .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(),
        rule.getCategory(),
        rule.getName() + " - parent artifactId ends with " + requiredSuffix);
  }

  /** Evaluates MS-013: Parent modules should not have a parent POM. */
  private Violation evaluateRequireNoParent(RuleDefinition rule, ModuleInfo moduleInfo) {
    // This rule applies to parent modules only
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not a parent module");
    }

    Model pomModel = moduleInfo.getPomModel();
    if (pomModel == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no POM model available");
    }

    if (pomModel.getParent() != null) {
      String parentArtifactId = pomModel.getParent().getArtifactId();
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("Parent module should not declare a <parent> section")
          .found("Parent declared: " + parentArtifactId)
          .reason(rule.getReason())
          .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - no parent declared (standalone)");
  }

  /** Evaluates MS-014: Aggregator modules should not contain source code. */
  private Violation evaluateNoSourceCode(RuleDefinition rule, ModuleInfo moduleInfo) {
    // This rule applies to aggregator modules (parent modules)
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not an aggregator module");
    }

    Path basePath = moduleInfo.getBasePath();
    if (basePath == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no base path available");
    }

    List<String> foundSourceDirs = new ArrayList<>();
    Path srcMainJava = basePath.resolve("src/main/java");
    Path srcTestJava = basePath.resolve("src/test/java");

    if (java.nio.file.Files.exists(srcMainJava)) {
      foundSourceDirs.add("src/main/java");
    }
    if (java.nio.file.Files.exists(srcTestJava)) {
      foundSourceDirs.add("src/test/java");
    }

    if (foundSourceDirs.isEmpty()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), rule.getName() + " - no source directories found");
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().toString())
        .expected("Aggregator module should not contain source directories")
        .found("Found source directories: " + String.join(", ", foundSourceDirs))
        .reason(rule.getReason())
        .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
        .reference(rule.getReference())
        .build();
  }

  /** Evaluates MS-015: Aggregator modules should not have direct dependencies. */
  private Violation evaluateNoDependencies(RuleDefinition rule, ModuleInfo moduleInfo) {
    // This rule applies to aggregator modules (parent modules)
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not an aggregator module");
    }

    Model pomModel = moduleInfo.getPomModel();
    if (pomModel == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no POM model available");
    }

    List<org.apache.maven.model.Dependency> dependencies = pomModel.getDependencies();
    if (dependencies == null || dependencies.isEmpty()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), rule.getName() + " - no direct dependencies declared");
    }

    // Build list of found dependencies
    List<String> depNames = new ArrayList<>();
    for (org.apache.maven.model.Dependency dep : dependencies) {
      depNames.add(dep.getGroupId() + ":" + dep.getArtifactId());
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
        .expected("Aggregator module should only use <dependencyManagement>, not <dependencies>")
        .found(
            "Found "
                + depNames.size()
                + " direct dependencies: "
                + String.join(", ", depNames.subList(0, Math.min(3, depNames.size())))
                + (depNames.size() > 3 ? "..." : ""))
        .reason(rule.getReason())
        .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
        .reference(rule.getReference())
        .build();
  }

  /** Evaluates MS-016: All submodule directories must be listed in modules section. */
  private Violation evaluateAllSubmodulesListed(RuleDefinition rule, ModuleInfo moduleInfo) {
    // This rule applies to aggregator/parent modules
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not an aggregator module");
    }

    Path basePath = moduleInfo.getBasePath();
    if (basePath == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no base path available");
    }

    // Get listed modules from POM
    final List<String> listedModules =
        moduleInfo.getModuleOrder() != null ? moduleInfo.getModuleOrder() : new ArrayList<>();

    // Find all subdirectories containing pom.xml
    List<String> unlistedModules = new ArrayList<>();
    try {
      java.nio.file.Files.list(basePath)
          .filter(java.nio.file.Files::isDirectory)
          .filter(dir -> java.nio.file.Files.exists(dir.resolve("pom.xml")))
          .map(dir -> dir.getFileName().toString())
          .filter(dirName -> !listedModules.contains(dirName))
          .forEach(unlistedModules::add);
    } catch (java.io.IOException e) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - could not read directory");
    }

    if (unlistedModules.isEmpty()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          rule.getName() + " - all submodule directories are listed");
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
        .expected("All subdirectories with pom.xml should be listed in <modules>")
        .found("Unlisted submodules: " + String.join(", ", unlistedModules))
        .reason(rule.getReason())
        .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
        .reference(rule.getReference())
        .build();
  }

  /** Evaluates MS-017: Pure aggregator modules must have artifactId ending with specific suffix. */
  private Violation evaluatePureAggregatorSuffix(
      RuleDefinition rule, ModuleInfo moduleInfo, String requiredSuffix) {
    // This rule only applies to parent modules
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not a parent module");
    }

    // Check if this is a pure aggregator (no api/core/facade/spi submodules)
    boolean isPureAggregator = !hasStandardLayerSubmodules(moduleInfo);

    if (!isPureAggregator) {
      // This is a component parent (has api/core/facade/spi), not a pure aggregator
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          rule.getName() + " - not a pure aggregator (has standard layer submodules)");
    }

    // Pure aggregator - check if artifactId ends with required suffix
    String artifactId = moduleInfo.getArtifactId();
    if (artifactId != null && artifactId.endsWith(requiredSuffix)) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          rule.getName() + " - artifactId correctly ends with " + requiredSuffix);
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
        .expected("Pure aggregator artifactId must end with '" + requiredSuffix + "'")
        .found("ArtifactId is '" + artifactId + "'")
        .reason(rule.getReason())
        .fix(resolvePattern(rule.getFix(), moduleInfo, moduleInfo.getArtifactId()))
        .reference(rule.getReference())
        .build();
  }

  /** Evaluates NC-030: Aggregator module artifactId must end with one of the specified suffixes. */
  private Violation evaluateArtifactIdMustEndWith(
      RuleDefinition rule, ModuleInfo moduleInfo, List<String> requiredSuffixes) {
    // This rule only applies to aggregator/parent modules
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not an aggregator module");
    }

    String artifactId = moduleInfo.getArtifactId();
    if (artifactId == null) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no artifactId available");
    }

    // Check if artifactId ends with any of the required suffixes
    for (String suffix : requiredSuffixes) {
      if (artifactId.endsWith(suffix)) {
        return Violation.passed(
            rule.getId(),
            rule.getCategory(),
            rule.getName() + " - artifactId correctly ends with '" + suffix + "'");
      }
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
        .expected(
            "Aggregator artifactId must end with one of: " + String.join(", ", requiredSuffixes))
        .found("ArtifactId is '" + artifactId + "'")
        .reason(rule.getReason())
        .fix(resolvePattern(rule.getFix(), moduleInfo, artifactId))
        .reference(rule.getReference())
        .build();
  }

  /** Checks if a parent module has standard layer submodules (api, core, facade, spi). */
  private boolean hasStandardLayerSubmodules(ModuleInfo moduleInfo) {
    if (moduleInfo.getSubModules() == null || moduleInfo.getSubModules().isEmpty()) {
      return false;
    }

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      String artifactId = subModule.getArtifactId();
      if (artifactId != null) {
        if (artifactId.endsWith("-api")
            || artifactId.endsWith("-core")
            || artifactId.endsWith("-facade")
            || artifactId.endsWith("-spi")) {
          return true;
        }
      }
    }
    return false;
  }

  /** Evaluates MS-010: Module order in parent POM. */
  private Violation evaluateModuleOrder(
      RuleDefinition rule, ModuleInfo moduleInfo, List<String> moduleOrderPatterns) {
    // Only evaluate for parent modules
    if (!moduleInfo.isParent()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - not a parent module");
    }

    List<String> actualOrder = moduleInfo.getModuleOrder();
    if (actualOrder == null || actualOrder.isEmpty()) {
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "Skipping " + rule.getName() + " - no modules declared");
    }

    // For MS-010, the pattern is "*-common" which means common module should be first
    String firstPattern = moduleOrderPatterns.get(0);
    String firstActual = actualOrder.isEmpty() ? "" : actualOrder.get(0);

    // Check if a common module exists in the order
    String commonModuleName = findMatchingModule(actualOrder, firstPattern);
    if (commonModuleName == null) {
      // No common module exists, rule doesn't apply
      return Violation.passed(
          rule.getId(),
          rule.getCategory(),
          "No module matching '" + firstPattern + "' found - rule not applicable");
    }

    // Check if the common module is first
    if (!matchesPattern(firstActual, firstPattern)) {
      return Violation.builder()
          .ruleId(rule.getId())
          .category(rule.getCategory())
          .severity(rule.getSeverity())
          .description(rule.getDescription())
          .location(moduleInfo.getBasePath().resolve("pom.xml").toString())
          .expected("First module should match pattern: " + firstPattern)
          .found("First module is: " + firstActual + " (expected: " + commonModuleName + ")")
          .reason(rule.getReason())
          .fix(resolvePattern(rule.getFix(), moduleInfo, firstActual))
          .reference(rule.getReference())
          .build();
    }

    return Violation.passed(
        rule.getId(), rule.getCategory(), rule.getName() + " - module order is correct");
  }

  /** Finds a module in the list that matches the given pattern. */
  private String findMatchingModule(List<String> modules, String pattern) {
    for (String module : modules) {
      if (matchesPattern(module, pattern)) {
        return module;
      }
    }
    return null;
  }

  /** Checks if a module name matches a pattern. Supports wildcard patterns like "*-common". */
  private boolean matchesPattern(String moduleName, String pattern) {
    // Special case for "*-common" pattern to also match simple "common"
    if (pattern.equals("*-common")) {
      return moduleName.endsWith("-common") || moduleName.equals("common");
    }
    if (pattern.startsWith("*")) {
      return moduleName.endsWith(pattern.substring(1));
    }
    if (pattern.endsWith("*")) {
      return moduleName.startsWith(pattern.substring(0, pattern.length() - 1));
    }
    return moduleName.equals(pattern);
  }

  /**
   * Evaluates a vendor/tool name rule (e.g., NC-029). Checks package names, class names, and
   * artifact IDs for hardcoded vendor names.
   */
  private Violation evaluateVendorRule(RuleDefinition rule, ModuleInfo moduleInfo) {
    List<String> foundViolations = new ArrayList<>();
    List<String> vendorPatterns = rule.getDetection().getVendorPatterns();

    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      if (!subModule.isExists()) {
        continue;
      }

      String artifactId = subModule.getArtifactId();
      if (artifactId == null) {
        continue;
      }

      // Check if this submodule matches target patterns
      if (!matchesTargetModules(artifactId, rule.getTargetModules())) {
        continue;
      }

      // Check artifact ID for vendor names
      for (String vendor : vendorPatterns) {
        if (containsVendorName(artifactId, vendor)) {
          foundViolations.add("artifactId '" + artifactId + "' contains '" + vendor + "'");
        }
      }

      // Check Java source files for vendor names in package/class names
      if (subModule.getJavaSourceFiles() != null) {
        for (Path javaFile : subModule.getJavaSourceFiles()) {
          List<String> fileViolations = checkVendorPatternsInFile(javaFile, vendorPatterns);
          foundViolations.addAll(fileViolations);
        }
      }
    }

    if (foundViolations.isEmpty()) {
      return Violation.passed(
          rule.getId(), rule.getCategory(), "No vendor names found for " + rule.getName());
    }

    return buildViolation(rule, moduleInfo, foundViolations);
  }

  /** Checks a Java file for vendor patterns in package and class names. */
  private List<String> checkVendorPatternsInFile(Path javaFile, List<String> vendorPatterns) {
    List<String> violations = new ArrayList<>();

    Optional<CompilationUnit> cuOpt = JavaParserUtil.parse(javaFile);
    if (cuOpt.isEmpty()) {
      return violations;
    }

    CompilationUnit cu = cuOpt.get();

    // Check package name
    cu.getPackageDeclaration()
        .ifPresent(
            pkg -> {
              String packageName = pkg.getNameAsString();
              for (String vendor : vendorPatterns) {
                if (containsVendorName(packageName, vendor)) {
                  violations.add("package '" + packageName + "' contains '" + vendor + "'");
                }
              }
            });

    // Check class names
    for (ClassOrInterfaceDeclaration clazz : cu.findAll(ClassOrInterfaceDeclaration.class)) {
      String className = clazz.getNameAsString();
      for (String vendor : vendorPatterns) {
        if (containsVendorName(className, vendor)) {
          violations.add("class '" + className + "' contains '" + vendor + "'");
        }
      }
    }

    return violations;
  }

  /**
   * Checks if a name contains a vendor pattern as a word boundary. Uses case-insensitive matching
   * and checks for word boundaries to avoid false positives. For example, "java" should match
   * "JavaConfig" but not "javascript".
   */
  private boolean containsVendorName(String name, String vendor) {
    String lowerName = name.toLowerCase();
    String lowerVendor = vendor.toLowerCase();

    int index = lowerName.indexOf(lowerVendor);
    if (index < 0) {
      return false;
    }

    // Check for word boundary at start
    boolean startBoundary = index == 0 || !Character.isLetterOrDigit(lowerName.charAt(index - 1));

    // Check for word boundary at end
    int endIndex = index + lowerVendor.length();
    boolean endBoundary =
        endIndex >= lowerName.length()
            || !Character.isLetterOrDigit(lowerName.charAt(endIndex))
            || Character.isUpperCase(name.charAt(endIndex)); // CamelCase boundary

    return startBoundary && endBoundary;
  }

  /** Checks if an artifact ID matches any of the target module patterns. */
  private boolean matchesTargetModules(String artifactId, List<String> targetModules) {
    if (targetModules == null || targetModules.isEmpty()) {
      return true; // No filter means match all
    }

    for (String pattern : targetModules) {
      if (pattern.startsWith("*") && artifactId.endsWith(pattern.substring(1))) {
        return true;
      }
      if (pattern.endsWith("*")
          && artifactId.startsWith(pattern.substring(0, pattern.length() - 1))) {
        return true;
      }
      if (pattern.equals(artifactId)) {
        return true;
      }
    }
    return false;
  }

  /** Checks if a file matches a pattern. */
  private boolean matchesFilePattern(Path file, String pattern) {
    String fileName = file.getFileName().toString();
    if (pattern.startsWith("*")) {
      return fileName.endsWith(pattern.substring(1));
    }
    if (pattern.endsWith("*")) {
      return fileName.startsWith(pattern.substring(0, pattern.length() - 1));
    }
    return fileName.equals(pattern);
  }

  /**
   * Resolves pattern placeholders using configuration values.
   *
   * <p>Supported placeholders:
   *
   * <ul>
   *   <li>{groupId} - Maven groupId from configuration
   *   <li>{namespace} - Java package namespace from configuration
   *   <li>{base} - Base name extracted from artifact ID
   *   <li>{module} - Module name
   *   <li>{base}-api, {base}-common, etc. - Derived artifact names
   * </ul>
   */
  private String resolvePattern(String pattern, ModuleInfo moduleInfo, String artifactId) {
    if (pattern == null) {
      return "";
    }

    String base = extractBaseName(artifactId);
    String namespace = config.getNamingNamespace();
    String project = config.getProject();

    return pattern
        .replace("{groupId}", namespace + "." + project)
        .replace("{namespace}", namespace)
        .replace("{base}", base)
        .replace("{module}", moduleInfo.getModuleName())
        .replace("{base}-api", base + "-api")
        .replace("{base}-common", base + "-common")
        .replace("{base}-spi", base + "-spi")
        .replace("{base}-core", base + "-core");
  }

  /** Extracts base name from artifact ID. */
  private String extractBaseName(String artifactId) {
    String[] suffixes = {"-api", "-core", "-spi", "-facade", "-common"};
    for (String suffix : suffixes) {
      if (artifactId.endsWith(suffix)) {
        return artifactId.substring(0, artifactId.length() - suffix.length());
      }
    }
    return artifactId;
  }

  /** Builds a violation from found issues. */
  private Violation buildViolation(
      RuleDefinition rule, ModuleInfo moduleInfo, List<String> foundViolations) {
    String foundStr =
        foundViolations.size()
            + " violation(s): "
            + String.join(
                ", ",
                foundViolations.subList(
                    0, Math.min(MAX_VIOLATIONS_TO_LIST, foundViolations.size())));

    if (foundViolations.size() > MAX_VIOLATIONS_TO_LIST) {
      foundStr += "...";
    }

    return Violation.builder()
        .ruleId(rule.getId())
        .category(rule.getCategory())
        .severity(rule.getSeverity())
        .description(rule.getDescription())
        .location(moduleInfo.getBasePath().toString())
        .expected(rule.getDescription())
        .found(foundStr)
        .reason(rule.getReason())
        .fix(rule.getFix())
        .reference(rule.getReference())
        .build();
  }
}
