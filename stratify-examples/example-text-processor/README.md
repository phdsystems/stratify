# Text Processor Example

A complete working example demonstrating the **Stratified Encapsulation Architecture (SEA)** pattern with all 5 layers.

## Overview

This example implements a simple text processing system that converts text to uppercase or lowercase using pluggable processors. It demonstrates:

- Full 5-layer SEA architecture
- Provider-based extensibility with `@Provider` annotation
- ServiceLoader-based provider discovery
- Facade pattern with static API
- Clean separation of concerns across layers

## Architecture

```
example-text-processor/
├── text-processor-common/         (Layer 1: Common)
│   ├── model/ProcessRequest.java
│   ├── model/ProcessResult.java
│   └── exception/ProcessException.java
│
├── text-processor-spi/            (Layer 2: SPI)
│   └── TextProcessor.java
│
├── text-processor-api/            (Layer 3: API)
│   └── TextService.java
│
├── text-processor-core/           (Layer 4: Core)
│   ├── UpperCaseProcessor.java    (@Provider)
│   ├── LowerCaseProcessor.java    (@Provider)
│   ├── DefaultTextService.java
│   └── META-INF/services/...      (ServiceLoader config)
│
└── text-processor-facade/         (Layer 5: Facade)
    └── TextProcessing.java        (@Facade)
```

## Layer Details

### Layer 1: Common (`text-processor-common`)
**Purpose:** Shared data models and exceptions

- `ProcessRequest` - Immutable request record
- `ProcessResult` - Immutable result record
- `ProcessException` - Domain exception

**Dependencies:** None (pure data)

### Layer 2: SPI (`text-processor-spi`)
**Purpose:** Service Provider Interface for extensibility

- `TextProcessor` - Interface for text processing implementations

**Dependencies:** `text-processor-common`

### Layer 3: API (`text-processor-api`)
**Purpose:** Public API contract

- `TextService` - High-level service interface

**Dependencies:** `text-processor-common`

### Layer 4: Core (`text-processor-core`)
**Purpose:** Implementation and providers

- `UpperCaseProcessor` - Uppercase converter with `@Provider(name="uppercase", priority=10)`
- `LowerCaseProcessor` - Lowercase converter with `@Provider(name="lowercase", priority=5)`
- `DefaultTextService` - Service implementation using `ProviderRegistry`
- ServiceLoader configuration in `META-INF/services/`

**Dependencies:** `text-processor-spi`, `text-processor-api`, `stratify-core`

### Layer 5: Facade (`text-processor-facade`)
**Purpose:** Simplified public entry point

- `TextProcessing` - Static facade with `@Facade` annotation
- Automatic provider discovery via `ProviderDiscovery`
- Convenience methods for common operations

**Dependencies:** `text-processor-api`, `text-processor-core`, `stratify-core`

**Artifact ID:** `text-processor` (not `text-processor-facade`)

## Usage Example

```java
import dev.engineeringlab.example.text.TextProcessing;
import dev.engineeringlab.example.text.common.model.ProcessResult;

// Simple facade usage
ProcessResult result = TextProcessing.process("Hello World", "uppercase");
System.out.println(result.processedText());  // "HELLO WORLD"

// List available processors
List<String> processors = TextProcessing.availableProcessors();
System.out.println(processors);  // [uppercase, lowercase]

// Access underlying service for advanced usage
TextService service = TextProcessing.service();
```

## Key SEA Patterns Demonstrated

1. **Layer Isolation**
   - Each layer has clear responsibilities
   - Dependencies flow downward only
   - No circular dependencies

2. **Provider Pattern**
   - Processors annotated with `@Provider`
   - Automatic discovery via ServiceLoader
   - Priority-based selection

3. **Registry Pattern**
   - `ProviderRegistry<TextProcessor>` manages implementations
   - Type-safe provider lookup
   - Priority ordering

4. **Facade Pattern**
   - Simple static API in `TextProcessing`
   - Hides internal complexity
   - Automatic provider initialization

5. **Immutable Data**
   - Records for `ProcessRequest` and `ProcessResult`
   - Validation in record constructors
   - Thread-safe by design

## Building

```bash
cd example-text-processor
mvn clean install
```

## Adding New Processors

To add a new text processor:

1. Implement `TextProcessor` in `text-processor-core`
2. Annotate with `@Provider(name="...", priority=...)`
3. Add to `META-INF/services/dev.engineeringlab.example.text.spi.TextProcessor`

Example:

```java
@Provider(name = "reverse", priority = 8, description = "Reverses text")
public class ReverseProcessor implements TextProcessor {
    @Override
    public String process(String text) {
        return new StringBuilder(text).reverse().toString();
    }

    @Override
    public String getType() {
        return "reverse";
    }
}
```

## Dependencies

- `stratify-annotations` - SEA annotations (`@Provider`, `@Facade`)
- `stratify-core` - Provider discovery and registry
- Java 21+
- SLF4J for logging

## Related Documentation

- [SEA Architecture Guide](../../doc/architecture/sea-pattern.md)
- [Stratify Annotations](../../stratify-annotations/README.md)
- [Provider Discovery](../../stratify-core/README.md)
