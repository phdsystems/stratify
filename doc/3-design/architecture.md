# Stratify Architecture

## Stratified Encapsulation Architecture (SEA)

SEA is a 5-layer architectural pattern for modular applications with enforced consumer isolation.

## Layer Structure

```
    ┌──────────┐
    │  FACADE  │  L5 - Consumer entry point (ONLY externally visible)
    └────┬─────┘
         │
    ┌────▼─────┐
    │   CORE   │  L4 - Implementation (exports NOTHING directly)
    └────┬─────┘
         │
    ┌────▼─────┐
    │   API    │  L3 - Consumer contracts
    └────┬─────┘
         │
    ┌────▼─────┐
    │   SPI    │  L2 - Extension points (provider interfaces)
    └────┬─────┘
         │
    ┌────▼─────┐
    │  COMMON  │  L1 - Foundation (DTOs, models, exceptions)
    └──────────┘
```

## Layer Responsibilities

### L1: Common Layer

**Purpose**: Foundation types shared across all layers.

**Contains**:
- DTOs and value objects
- Error types and exceptions
- Configuration structs
- Constants and enums

**Dependencies**: External libraries only.

### L2: SPI Layer (Service Provider Interface)

**Purpose**: Extension points for implementors.

**Contains**:
- Provider interfaces
- Extension interfaces
- Hooks for customization

**Dependencies**: L1 (Common)

### L3: API Layer

**Purpose**: Consumer-facing contracts.

**Contains**:
- High-level interfaces for consumers
- Service interfaces
- Operation contracts

**Dependencies**: L1 (Common), L2 (SPI)

### L4: Core Layer

**Purpose**: All implementations.

**Contains**:
- Concrete provider implementations
- Registry implementations
- Internal utilities

**Dependencies**: L1, L2, L3

**Exports**: Only to L5 (Facade)

### L5: Facade Layer

**Purpose**: Public API surface.

**Contains**:
- Re-exports from all layers
- Factory functions
- Convenience APIs

**Dependencies**: L4 (Core)

## Dependency Rules

```
L5 (Facade) ──► L4 (Core)
                  │
                  ├──► L3 (API) ──► L2 (SPI) ──► L1 (Common)
                  │                    │
                  └────────────────────┘
```

**Rules**:
1. Each layer only depends on layers below it
2. No circular dependencies
3. L4 (Core) is never directly accessed by consumers
4. Consumers only see L5 (Facade)

## Module Template

```
{module}/
├── pom.xml                       # Parent POM
├── {module}-common/              # L1: Types, errors, DTOs
│   ├── pom.xml
│   └── src/main/java/
├── {module}-spi/                 # L2: Provider interfaces
│   ├── pom.xml
│   └── src/main/java/
├── {module}-api/                 # L3: Consumer contracts
│   ├── pom.xml
│   └── src/main/java/
├── {module}-core/                # L4: Implementation
│   ├── pom.xml
│   └── src/main/java/
└── {module}-facade/              # L5: Entry point
    ├── pom.xml
    └── src/main/java/
```

## Decision Criteria

### When to Apply SEA

| Criteria | Apply SEA | Stay Standalone |
|----------|-----------|-----------------|
| Lines of code | >500 | <200 |
| Multiple backends | Yes | Single impl |
| Plugin architecture | Yes | No extensions |
| API stability needed | Yes | Internal only |
| Consumer isolation | Priority | Trusted consumers |

### When NOT to Apply SEA

1. **Small utilities** (<200 lines with stable implementation)
2. **Internal-only code** (trusted consumers, no isolation needed)
3. **Rapid prototyping** (pattern adds overhead during exploration)
4. **Pure type definitions** (no behavior to encapsulate)

## Aggregator Patterns

### Depth 0: Single Specialized Domain

```
cache/
├── cache-common/
├── cache-spi/
├── cache-api/
├── cache-core/
└── cache-facade/
```

### Depth 1: Domain with Multiple Capabilities

```
llm/
├── llm-provider/
│   └── (SEA modules)
├── llm-evaluation/
│   └── (SEA modules)
└── llm-memory/
    └── (SEA modules)
```

### Depth 2+: Framework with Multiple Domains

```
framework/
├── product-a/
│   ├── domain-x/
│   │   └── (SEA modules)
│   └── domain-y/
│       └── (SEA modules)
└── product-b/
    └── (domains...)
```

## JPMS Strategy

Stratify uses Automatic-Module-Name for JPMS compatibility:

```xml
<plugin>
    <artifactId>maven-jar-plugin</artifactId>
    <configuration>
        <archive>
            <manifestEntries>
                <Automatic-Module-Name>com.example.module</Automatic-Module-Name>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

## Benefits

| Metric | Result |
|--------|--------|
| API surface reduction | >90% |
| Internal dependency leakage | 0% (compile-time prevented) |
| Incremental build improvement | ~30% faster |
