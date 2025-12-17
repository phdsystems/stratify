# Stratify Structure

Rule infrastructure for validating Stratify module architecture and structure.

## Overview

This module provides the core infrastructure for defining and executing structure validation rules. Rules can be defined declaratively in configuration files and executed against module structures to ensure compliance with architectural standards.

## Key Features

- **Zero Dependency Configuration**: Rules can be defined in `.properties` files using standard Java Properties
- **Optional YAML Support**: Enhanced readability with YAML format (requires SnakeYAML on classpath)
- **Java Records**: Modern Java records instead of Lombok for better interoperability
- **Extensible**: Easy to add new rules by extending `AbstractStructureRule`
- **Lightweight**: No JavaParser or other heavy dependencies required

## Architecture

### Core Components

#### 1. RuleDefinition (Record)
Immutable definition of a rule, including:
- Rule metadata (id, name, description)
- Severity and category
- Detection criteria
- Fix instructions and documentation references

#### 2. RuleLoader
Loads rule definitions from configuration files:
- **Properties format**: Zero dependency, always available
- **YAML format**: Optional, detected via reflection

Example:
```java
RuleLoader loader = new RuleLoader()
    .loadFromClasspath("structure-rules.properties")
    .loadFromClasspath("structure-rules.yaml");  // optional
```

#### 3. AbstractStructureRule
Base class for implementing validation rules. Provides:
- Automatic rule metadata loading
- Violation creation helpers
- Standard validation lifecycle

Example:
```java
public class FacadeRequiredRule extends AbstractStructureRule {

    public FacadeRequiredRule(RuleLoader loader) {
        super("MS-001", loader);
    }

    @Override
    protected boolean appliesTo(Path modulePath) {
        return Files.exists(modulePath.resolve("pom.xml"));
    }

    @Override
    protected List<StructureViolation> doValidate(Path modulePath) {
        // Check if facade submodule exists
        Path facadePath = modulePath.resolve("*-facade");
        if (!Files.exists(facadePath)) {
            return List.of(createViolation(
                modulePath.getFileName().toString(),
                "Module missing required facade submodule"
            ));
        }
        return List.of();
    }
}
```

### Model Classes

- **Category**: Enum for violation categories (STRUCTURE, DEPENDENCIES, NAMING)
- **Severity**: Enum for severity levels (ERROR, WARNING, INFO)
- **StructureViolation**: Record representing a rule violation with full metadata

## Configuration Formats

### Properties Format

Zero dependency, simple key-value format:

```properties
# Rule definition
DP-001.name=No implementation dependencies in API
DP-001.description=API modules must not depend on implementation modules
DP-001.category=DEPENDENCIES
DP-001.severity=ERROR
DP-001.enabled=true
DP-001.reason=API must remain stable per ADR-005
DP-001.fix=Remove implementation dependencies from pom.xml
DP-001.reference=docs/adr/ADR-005-facade-only-access.md

# Detection patterns
DP-001.detection.dependencyPatterns.mustNotContain=*-core,*-facade
DP-001.detection.dependencyPatterns.scope=api
```

### YAML Format

More readable for complex rules (requires SnakeYAML):

```yaml
rules:
  DP-001:
    name: "No implementation dependencies in API"
    description: "API modules must not depend on implementation modules"
    category: DEPENDENCIES
    severity: ERROR
    enabled: true
    reason: "API must remain stable per ADR-005"
    fix: "Remove implementation dependencies from pom.xml"
    reference: "docs/adr/ADR-005-facade-only-access.md"
    detection:
      dependencyPatterns:
        mustNotContain:
          - "*-core"
          - "*-facade"
        scope: "api"
```

## Usage

### 1. Define Rules in Configuration

Create `structure-rules.properties` or `structure-rules.yaml` (or both):

```properties
# structure-rules.properties
MS-001.name=Facade module required
MS-001.description=Each module must have a facade submodule
MS-001.category=STRUCTURE
MS-001.severity=ERROR
MS-001.enabled=true
MS-001.reason=Facade pattern ensures controlled access per ADR-005
MS-001.fix=Create a facade submodule with naming pattern {module}-facade
MS-001.reference=docs/adr/ADR-005-facade-only-access.md
```

### 2. Load Rules at Startup

```java
RuleLoader loader = new RuleLoader()
    .loadFromClasspath("structure-rules.properties");

// YAML will be automatically loaded if SnakeYAML is on classpath
if (loader.isYamlSupported()) {
    loader.loadFromClasspath("structure-rules.yaml");
}
```

### 3. Implement Custom Rules

```java
public class MyStructureRule extends AbstractStructureRule {

    public MyStructureRule(RuleLoader loader) {
        super("MS-001", loader);  // Rule ID from configuration
    }

    @Override
    protected boolean appliesTo(Path path) {
        // Only check Maven modules
        return Files.exists(path.resolve("pom.xml"));
    }

    @Override
    protected List<StructureViolation> doValidate(Path path) {
        List<StructureViolation> violations = new ArrayList<>();

        // Your validation logic here
        if (someCondition) {
            violations.add(createViolation(
                path.getFileName().toString(),
                "Violation message",
                "path/to/file.xml"  // optional location
            ));
        }

        return violations;
    }
}
```

### 4. Execute Rules

```java
MyStructureRule rule = new MyStructureRule(loader);
List<StructureViolation> violations = rule.validate(modulePath);

// Report violations
violations.forEach(v -> System.err.println(v));
```

## Migration from MS-Engine

This infrastructure is ported from the MS-Engine scanner-common module with the following changes:

### Changes Made

1. **Lombok to Records**: Converted `@Data @Builder` classes to Java records with nested builder classes
2. **Dual Format Support**: Added `.properties` support alongside `.yaml`
3. **Optional YAML**: YAML support detected via reflection, not required
4. **No JavaParser**: Removed JavaParser dependency (not needed for structure rules)
5. **Simplified Models**: Removed complex detection patterns not needed for basic structure validation
6. **Local Types**: Created local `Severity`, `Category`, and `StructureViolation` instead of depending on scanner-spi

### What Was Kept

- Rule loading infrastructure
- Builder patterns for constructing complex objects
- Configuration-driven rule definitions
- Abstract base class for rule implementation

### What Was Removed

- JavaParser integration
- Complex class/annotation detection patterns
- Scanner SPI dependencies
- ComplianceConfig integration
- Vendor pattern detection
- Method naming patterns

## Dependencies

### Required
- Java 17+ (for records)

### Optional
- SnakeYAML (`org.yaml:snakeyaml`) - For YAML configuration support

## Example Rules

See `src/main/resources/structure-rules.properties` and `src/main/resources/structure-rules.yaml` for example rule definitions.

Common rule patterns:

- **DP-001**: API modules must not depend on implementation modules
- **DP-002**: Core must depend on API
- **MS-001**: Facade module required
- **MS-002**: Standard module structure (common, spi, api, core, facade)
- **MS-003**: Parent POM required
- **NC-001**: Facade package naming conventions

## Contributing

When adding new rules:

1. Define the rule in both `.properties` and `.yaml` files (for examples)
2. Create a rule class extending `AbstractStructureRule`
3. Implement `appliesTo()` and `doValidate()` methods
4. Add tests verifying the rule behavior
5. Document the rule in this README

## References

- **Source**: Ported from `/seq-platform/architecture/structure/ms-engine/`
- **ADR-005**: Facade-only access pattern
- **Related**: stratify-rules module (runtime dependency validation)
