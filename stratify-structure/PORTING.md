# Porting Summary: MS-Engine to Stratify-Structure

## Overview

This document describes the porting of rule infrastructure from the seq-platform MS-Engine to stratify-structure.

## Source Files

The following files were ported from `/home/adentic/seq-platform/`:

### 1. RuleDefinition.java
- **Source**: `architecture/structure/arc-engine/scanner-common/src/main/java/.../rule/RuleDefinition.java`
- **Target**: `/home/adentic/stratify/stratify-structure/src/main/java/dev/engineeringlab/stratify/structure/rule/RuleDefinition.java`
- **Changes**:
  - Converted from Lombok `@Data @Builder` to Java record
  - Added nested `Builder` classes to preserve builder pattern
  - Simplified nested records (removed unused pattern types)
  - Changed package to `dev.engineeringlab.stratify.structure.rule`
  - Used local `Category` and `Severity` enums

### 2. RuleLoader.java
- **Source**: `architecture/structure/arc-engine/scanner-common/src/main/java/.../rule/RuleLoader.java`
- **Target**: `/home/adentic/stratify/stratify-structure/src/main/java/dev/engineeringlab/stratify/structure/rule/RuleLoader.java`
- **Changes**:
  - Added `.properties` file support (zero dependency)
  - Made YAML support optional (detected via reflection)
  - Removed `ComplianceConfig` integration
  - Removed rule override functionality
  - Simplified detection pattern parsing
  - Changed package to `dev.engineeringlab.stratify.structure.rule`
  - Used local model classes

### 3. AbstractStructureRule.java
- **Source**: `architecture/structure/ms-engine/ms-scanner/ms-scanner-core/src/main/java/.../AbstractStructureRule.java`
- **Target**: `/home/adentic/stratify/stratify-structure/src/main/java/dev/engineeringlab/stratify/structure/rule/AbstractStructureRule.java`
- **Changes**:
  - Removed JavaParser dependency and related methods
  - Removed Scanner SPI interface implementation
  - Changed validation target from `ModuleInfo` to `Path`
  - Simplified to use local `StructureViolation` instead of SPI `Violation`
  - Added constructor taking `RuleLoader` parameter
  - Changed package to `dev.engineeringlab.stratify.structure.rule`
  - Used local `Category` and `Severity` enums

## New Files Created

### Model Classes
1. **Severity.java** - Enum for violation severity levels (ERROR, WARNING, INFO)
2. **Category.java** - Enum for violation categories (STRUCTURE, DEPENDENCIES, NAMING)
3. **StructureViolation.java** - Record for structure violations with builder pattern

### Configuration Examples
1. **structure-rules.properties** - Example rules in properties format
2. **structure-rules.yaml** - Example rules in YAML format

### Documentation
1. **README.md** - Comprehensive usage documentation
2. **PORTING.md** - This file

## Key Design Decisions

### 1. Java Records vs Lombok
**Decision**: Use Java records with nested builder classes

**Rationale**:
- Records are native Java 17+ feature (no dependencies)
- Better interoperability with other tools
- Immutability by default
- Cleaner syntax for simple data classes

**Implementation**: Created nested static `Builder` classes within records to preserve builder pattern API compatibility

### 2. Dual Format Support (Properties + YAML)
**Decision**: Support both `.properties` and `.yaml` files

**Rationale**:
- Properties: Zero dependency, always available
- YAML: Better readability for complex rules, optional

**Implementation**:
- Format detection based on file extension
- YAML support detected via reflection (Class.forName)
- Graceful degradation if YAML not available

### 3. No JavaParser Dependency
**Decision**: Remove JavaParser integration

**Rationale**:
- Not needed for basic structure validation
- Reduces dependencies and complexity
- Structure rules work at file/directory level
- Code-level analysis can be done by specialized scanners

### 4. Validation Target: Path vs ModuleInfo
**Decision**: Use `Path` instead of custom `ModuleInfo` class

**Rationale**:
- Simpler, more flexible API
- Path is standard Java API
- Rules can work on any directory structure
- No need for complex module model

### 5. Local Model Classes
**Decision**: Create local `Severity`, `Category`, `StructureViolation`

**Rationale**:
- No dependency on scanner-spi or scanner-common
- Tailored to structure validation needs
- Simplified enum values (removed CRITICAL level)
- Clean separation from runtime validation

## Simplified Patterns

The following detection patterns were removed as they're not needed for basic structure validation:

- **NamingPatterns**: Complex naming convention patterns (40+ fields)
- **ClassPatterns**: JavaParser-based class detection
- **PomPatterns**: Detailed POM validation (kept basic structure patterns)
- **VendorPatterns**: Vendor/tool name detection

These can be added back if needed for specific rule implementations.

## Usage Comparison

### MS-Engine (Original)
```java
// Rules loaded from YAML only
RuleLoader loader = new RuleLoader()
    .loadRequiredFromClasspath("module-structure-rules.yaml");

// Scanner interface
public abstract class AbstractStructureRule implements Scanner<ModuleInfo> {
    @Override
    public List<Violation> scan(ModuleInfo module) {
        // ...
    }
}

// Depends on scanner-spi and scanner-common
```

### Stratify-Structure (Ported)
```java
// Rules from properties or YAML
RuleLoader loader = new RuleLoader()
    .loadFromClasspath("structure-rules.properties")
    .loadFromClasspath("structure-rules.yaml");  // optional

// Simplified abstract class
public abstract class AbstractStructureRule {
    public List<StructureViolation> validate(Path path) {
        // ...
    }

    protected abstract boolean appliesTo(Path path);
    protected abstract List<StructureViolation> doValidate(Path path);
}

// Zero required dependencies (except Java 17+)
```

## Testing

Manual compilation testing performed:
```bash
javac dev/engineeringlab/stratify/structure/model/*.java
javac dev/engineeringlab/stratify/structure/rule/RuleDefinition.java
```

Both compiled successfully with Java 21.

## Next Steps

To complete the integration:

1. **Add Unit Tests**: Test RuleLoader with both formats
2. **Create Example Rules**: Implement 2-3 concrete rule classes
3. **Integration**: Wire into stratify-core scanning infrastructure
4. **Documentation**: Add examples to main Stratify docs

## References

- **Source Repository**: `/home/adentic/seq-platform/`
- **MS-Engine**: `architecture/structure/ms-engine/`
- **Scanner Common**: `architecture/structure/arc-engine/scanner-common/`
- **ADR-005**: Facade-only access pattern (referenced in example rules)

## File Locations

### Stratify-Structure Module
```
/home/adentic/stratify/stratify-structure/
├── pom.xml
├── README.md
├── PORTING.md (this file)
└── src/main/
    ├── java/dev/engineeringlab/stratify/structure/
    │   ├── model/
    │   │   ├── Category.java
    │   │   ├── Severity.java
    │   │   └── StructureViolation.java
    │   └── rule/
    │       ├── AbstractStructureRule.java
    │       ├── RuleDefinition.java
    │       └── RuleLoader.java
    └── resources/
        ├── structure-rules.properties
        └── structure-rules.yaml
```

## Dependencies

### Required
- Java 17+ (for records)

### Optional
- `org.yaml:snakeyaml:2.2` - For YAML support (already in pom.xml)

### Not Required
- Lombok (replaced with records)
- JavaParser (removed)
- Scanner SPI (replaced with local interfaces)
- Scanner Common (replaced with local models)
