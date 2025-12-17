package dev.engineeringlab.stratify.structure.remediation.fixer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.PackageDeclaration;
import dev.engineeringlab.stratify.structure.remediation.fixer.ProjectConfigReader.ProjectConfig;
import dev.engineeringlab.stratify.structure.remediation.fixer.SubmoduleDiscovery.ModuleInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Analyzes Java packages across a project to find violations.
 *
 * <p>This analyzer scans all submodules and identifies classes that don't follow the expected
 * package naming convention: {@code namespace.project.module.layer}
 *
 * <p>Example expected patterns:
 *
 * <ul>
 *   <li>agent-api: dev.engineeringlab.architecture.agent.api.*
 *   <li>agent-core: dev.engineeringlab.architecture.agent.core.*
 *   <li>llm-memory-spi: dev.engineeringlab.architecture.llm.memory.spi.*
 * </ul>
 */
public class ProjectPackageAnalyzer {

  // Shared package names that can exist at parent level (without layer suffix)
  private static final Set<String> SHARED_PACKAGES =
      Set.of("exception", "model", "dto", "config", "constant", "util", "common");

  // Layer suffixes
  private static final Set<String> LAYER_SUFFIXES = Set.of("api", "spi", "core", "facade");

  private final ProjectConfigReader configReader;
  private final SubmoduleDiscovery submoduleDiscovery;

  /** Creates an analyzer with default implementations. */
  public ProjectPackageAnalyzer() {
    this(new ProjectConfigReader(), new SubmoduleDiscovery());
  }

  /** Creates an analyzer with custom implementations (for testing). */
  public ProjectPackageAnalyzer(
      ProjectConfigReader configReader, SubmoduleDiscovery submoduleDiscovery) {
    this.configReader = configReader;
    this.submoduleDiscovery = submoduleDiscovery;
  }

  /** Result of package analysis for a single Java file. */
  public record PackageViolation(
      Path javaFile,
      String currentPackage,
      String expectedPackage,
      ModuleInfo module,
      ViolationType type) {
    /** Returns the refactoring needed to fix this violation. */
    public PackageRefactorSuggestion getSuggestion() {
      return new PackageRefactorSuggestion(currentPackage, expectedPackage, javaFile);
    }
  }

  /** Types of package violations. */
  public enum ViolationType {
    /** Package doesn't start with expected base package */
    WRONG_BASE_PACKAGE,
    /** Package missing module name segment */
    MISSING_MODULE_SEGMENT,
    /** Package missing layer segment (api/core/spi) */
    MISSING_LAYER_SEGMENT,
    /** Package has wrong layer segment */
    WRONG_LAYER_SEGMENT,
    /** Package uses legacy/incorrect namespace */
    LEGACY_NAMESPACE
  }

  /** Suggested refactoring to fix a package violation. */
  public record PackageRefactorSuggestion(
      String fromPackage, String toPackage, Path affectedFile) {}

  /** Complete analysis result for a project. */
  public record AnalysisResult(
      ProjectConfig config,
      List<ModuleInfo> modules,
      List<PackageViolation> violations,
      Map<String, List<PackageViolation>> violationsByModule) {
    /** Returns unique package refactors needed (deduplicated by from/to package). */
    public List<PackageRefactorSuggestion> getUniqueRefactors() {
      Map<String, PackageRefactorSuggestion> unique = new HashMap<>();
      for (PackageViolation v : violations) {
        String key = v.currentPackage() + "->" + v.expectedPackage();
        if (!unique.containsKey(key)) {
          unique.put(key, v.getSuggestion());
        }
      }
      return new ArrayList<>(unique.values());
    }

    /** Returns true if there are any violations. */
    public boolean hasViolations() {
      return !violations.isEmpty();
    }

    /** Returns summary statistics. */
    public String getSummary() {
      return String.format(
          "Analyzed %d modules, found %d violations in %d unique packages",
          modules.size(), violations.size(), getUniqueRefactors().size());
    }
  }

  /**
   * Analyzes the entire project for package violations.
   *
   * @param projectRoot the root directory of the project
   * @return the analysis result
   */
  public AnalysisResult analyzeProject(Path projectRoot) {
    // Read configuration
    ProjectConfig config = configReader.readConfig(projectRoot);
    String basePackage = config.getBasePackage();

    // Discover all modules
    List<ModuleInfo> modules = submoduleDiscovery.discoverLeafModules(projectRoot);

    // Analyze each module
    List<PackageViolation> allViolations = new ArrayList<>();
    Map<String, List<PackageViolation>> violationsByModule = new HashMap<>();

    for (ModuleInfo module : modules) {
      List<PackageViolation> moduleViolations = analyzeModule(module, basePackage);
      allViolations.addAll(moduleViolations);
      if (!moduleViolations.isEmpty()) {
        violationsByModule.put(module.artifactId(), moduleViolations);
      }
    }

    return new AnalysisResult(config, modules, allViolations, violationsByModule);
  }

  /**
   * Analyzes a single module for package violations.
   *
   * @param module the module to analyze
   * @param basePackage the expected base package (namespace.project)
   * @return list of violations in this module
   */
  public List<PackageViolation> analyzeModule(ModuleInfo module, String basePackage) {
    List<PackageViolation> violations = new ArrayList<>();

    Path srcMainJava = module.path().resolve("src/main/java");
    if (!Files.isDirectory(srcMainJava)) {
      return violations;
    }

    String expectedPrefix = module.getExpectedPackagePrefix(basePackage);
    String parentLevelPackage = calculateParentLevelPackage(module, basePackage);

    try (Stream<Path> paths = Files.walk(srcMainJava)) {
      paths
          .filter(p -> p.toString().endsWith(".java"))
          .filter(p -> !p.getFileName().toString().equals("package-info.java"))
          .forEach(
              javaFile -> {
                analyzeJavaFile(javaFile, expectedPrefix, parentLevelPackage, module, basePackage)
                    .ifPresent(violations::add);
              });
    } catch (IOException e) {
      // Skip modules that can't be scanned
    }

    return violations;
  }

  /** Analyzes a single Java file for package violations. */
  private Optional<PackageViolation> analyzeJavaFile(
      Path javaFile,
      String expectedPrefix,
      String parentLevelPackage,
      ModuleInfo module,
      String basePackage) {

    try {
      CompilationUnit cu = StaticJavaParser.parse(javaFile);
      Optional<PackageDeclaration> packageDecl = cu.getPackageDeclaration();

      if (packageDecl.isEmpty()) {
        return Optional.empty(); // Default package - different rule
      }

      String currentPackage = packageDecl.get().getNameAsString();

      // Check if package is valid
      ViolationType violationType =
          checkViolation(currentPackage, expectedPrefix, parentLevelPackage, module, basePackage);

      if (violationType != null) {
        String expectedPackage =
            calculateExpectedPackage(currentPackage, expectedPrefix, parentLevelPackage, module);

        return Optional.of(
            new PackageViolation(javaFile, currentPackage, expectedPackage, module, violationType));
      }

    } catch (Exception e) {
      // Skip files that can't be parsed
    }

    return Optional.empty();
  }

  /**
   * Checks if a package violates the naming convention.
   *
   * @return the violation type, or null if valid
   */
  private ViolationType checkViolation(
      String currentPackage,
      String expectedPrefix,
      String parentLevelPackage,
      ModuleInfo module,
      String basePackage) {

    // Valid: starts with expected prefix (e.g., dev.engineeringlab.architecture.agent.api)
    if (currentPackage.startsWith(expectedPrefix)) {
      // Check for incorrect layer suffix after expected prefix
      String afterPrefix = currentPackage.substring(expectedPrefix.length());
      if (afterPrefix.startsWith(".")) {
        String firstSegment = afterPrefix.substring(1).split("\\.")[0];
        if (LAYER_SUFFIXES.contains(firstSegment) && !firstSegment.equals(module.type().suffix())) {
          return ViolationType.WRONG_LAYER_SEGMENT;
        }
      }
      return null; // Valid
    }

    // Valid: shared package at parent level (e.g., dev.engineeringlab.architecture.agent.exception)
    if (isValidSharedPackage(currentPackage, parentLevelPackage)) {
      return null;
    }

    // Valid: API/SPI modules can use parent-level package directly (JDBC pattern)
    if (isApiOrSpiModule(module) && currentPackage.equals(parentLevelPackage)) {
      return null;
    }

    // Determine violation type
    if (!currentPackage.startsWith(basePackage)) {
      // Check if it's a legacy namespace (e.g., dev.engineeringlab.sea instead of architecture)
      if (currentPackage.startsWith("dev.engineeringlab.")) {
        return ViolationType.LEGACY_NAMESPACE;
      }
      return ViolationType.WRONG_BASE_PACKAGE;
    }

    // Package starts with base but not with expected prefix
    String moduleName = module.extractModuleName();
    if (moduleName != null && !currentPackage.contains("." + moduleName.replace(".", "."))) {
      return ViolationType.MISSING_MODULE_SEGMENT;
    }

    return ViolationType.MISSING_LAYER_SEGMENT;
  }

  /** Calculates the expected package for a file based on its current package. */
  private String calculateExpectedPackage(
      String currentPackage, String expectedPrefix, String parentLevelPackage, ModuleInfo module) {

    // Try to preserve domain-specific suffixes (service, repository, etc.)
    String[] currentParts = currentPackage.split("\\.");

    // Find where the "domain" part starts (after base package segments)
    int domainStart = findDomainStart(currentParts);

    if (domainStart >= currentParts.length) {
      // No domain found, use expected prefix directly
      return expectedPrefix;
    }

    // Check if domain is a shared package type
    String domainPart = currentParts[domainStart];
    if (SHARED_PACKAGES.contains(domainPart)) {
      // Shared packages go at parent level
      StringBuilder result = new StringBuilder(parentLevelPackage);
      for (int i = domainStart; i < currentParts.length; i++) {
        result.append(".").append(currentParts[i]);
      }
      return result.toString();
    }

    // Regular domain packages go under the layer
    StringBuilder result = new StringBuilder(expectedPrefix);
    for (int i = domainStart; i < currentParts.length; i++) {
      // Skip layer suffixes that might be in wrong position
      if (!LAYER_SUFFIXES.contains(currentParts[i])) {
        result.append(".").append(currentParts[i]);
      }
    }

    return result.toString();
  }

  /** Finds where the domain part starts in a package name. */
  private int findDomainStart(String[] parts) {
    // Common patterns: com.company.module.domain or dev.engineeringlab.project.module.layer.domain
    // Look for known domain indicators
    for (int i = 0; i < parts.length; i++) {
      String part = parts[i].toLowerCase();
      // Domain indicators
      if (part.equals("service")
          || part.equals("repository")
          || part.equals("controller")
          || part.equals("handler")
          || part.equals("provider")
          || part.equals("impl")
          || SHARED_PACKAGES.contains(part)) {
        return i;
      }
    }

    // Fallback: assume domain starts after 4th segment (tld.company.project.module.domain)
    return Math.min(4, parts.length);
  }

  /** Calculates the parent-level package (without layer suffix). */
  private String calculateParentLevelPackage(ModuleInfo module, String basePackage) {
    String moduleName = module.extractModuleName();
    if (moduleName == null || moduleName.isEmpty()) {
      return basePackage;
    }
    return basePackage + "." + moduleName;
  }

  /** Checks if a package is a valid shared package at parent level. */
  private boolean isValidSharedPackage(String packageName, String parentLevelPackage) {
    if (!packageName.startsWith(parentLevelPackage + ".")) {
      return false;
    }

    String afterParent = packageName.substring(parentLevelPackage.length() + 1);
    String firstSegment = afterParent.split("\\.")[0];

    return SHARED_PACKAGES.contains(firstSegment);
  }

  /** Checks if a module is an API or SPI module. */
  private boolean isApiOrSpiModule(ModuleInfo module) {
    return module.type() == SubmoduleDiscovery.ModuleType.API
        || module.type() == SubmoduleDiscovery.ModuleType.SPI;
  }
}
