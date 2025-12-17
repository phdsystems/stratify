package dev.engineeringlab.stratify.structure.scanner.common.model;

/** Categories of compliance violations. */
public enum ViolationCategory {
  /** Module structure violations (e.g., missing required submodules). */
  MODULE_STRUCTURE("Module Structure"),

  /** Naming convention violations (e.g., incorrect package names, module naming). */
  NAMING_CONVENTIONS("Naming Conventions"),

  /**
   * Exception infrastructure violations (e.g., missing exception packages, incorrect error codes).
   */
  EXCEPTION_INFRASTRUCTURE("Exception Infrastructure"),

  /**
   * Exception propagation violations (e.g., duplicate ResilienceMetadata, UUID generation in API).
   */
  EXCEPTION_PROPAGATION("Exception Propagation"),

  /** Dependency management violations (e.g., incorrect dependency scopes, missing versions). */
  DEPENDENCIES("Dependencies"),

  /** Design pattern violations (e.g., missing @Provider annotations, incorrect usage patterns). */
  DESIGN_PATTERNS("Design Patterns"),

  /** Clean architecture violations (e.g., layer boundary violations). */
  CLEAN_ARCHITECTURE("Clean Architecture"),

  /** SPI provider violations (e.g., missing META-INF/services files). */
  SPI_PROVIDERS("SPI Providers"),

  /** Package organization violations (e.g., files in wrong packages). */
  PACKAGE_ORGANIZATION("Package Organization"),

  /** Code quality violations (e.g., deprecated APIs, unsafe coding practices). */
  CODE_QUALITY("Code Quality"),

  /** Code quality standards violations (e.g., coverage thresholds not met). */
  CODE_QUALITY_STANDARDS("Code Quality Standards"),

  /** Test structure violations (e.g., missing test directories, incorrect test organization). */
  TEST_STRUCTURE("Test Structure"),

  /** Generic test category for test-related violations. */
  TEST("Test"),

  /** SpotBugs exclusion violations (e.g., overly broad exclusions, missing justifications). */
  SPOTBUGS_EXCLUSIONS("SpotBugs Exclusions"),

  /** Static analysis violations (e.g., issues found by static analysis tools). */
  STATIC_ANALYSIS("Static Analysis"),

  /** Infrastructure violations (e.g., configuration issues, build setup problems). */
  INFRASTRUCTURE("Infrastructure"),

  /** Build policy violations (e.g., prohibited skip properties in POMs). */
  BUILD_POLICY("Build Policy"),

  /** Subsystem architecture violations (e.g., missing base interface implementations). */
  SUBSYSTEM_ARCHITECTURE("Subsystem Architecture"),

  /** Modular design violations (e.g., duplicate layers, SRP violations in module structure). */
  MODULAR_DESIGN("Modular Design");

  private final String displayName;

  ViolationCategory(String displayNameParam) {
    this.displayName = displayNameParam;
  }

  public String getDisplayName() {
    return displayName;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
