# Stratify

**Stratified Encapsulation Architecture (SEA) Framework for Java**

Stratify is a framework for building modular Java applications with enforced consumer isolation. It implements the 5-layer SEA pattern with tooling for scaffolding, validation, and architectural compliance.

## The SEA Pattern

SEA organizes code into five layers with strict dependency rules:

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

**Key Principle:** Consumers only interact with the FACADE layer. All internal complexity is hidden.

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>dev.engineeringlab</groupId>
    <artifactId>stratify-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Create a Provider

```java
@Provider(name = "default", priority = 10)
public class DefaultTextProvider implements TextProvider, ProviderBase {

    @Override
    public String name() {
        return "default";
    }

    @Override
    public String process(String input) {
        return input.toUpperCase();
    }
}
```

### 3. Discover and Use Providers

```java
// Automatic discovery
ProviderRegistry<TextProvider> registry =
    ProviderDiscovery.loadRegistry(TextProvider.class);

// Get the highest priority provider
TextProvider provider = registry.getDefaultOrThrow();
String result = provider.process("hello");
```

## Modules

| Module | Description |
|--------|-------------|
| `stratify-annotations` | Compile-time annotations (@SEAModule, @Provider, @Facade, @Internal) |
| `stratify-core` | Runtime support (Registry, Provider discovery, Configuration) |
| `stratify-plugin-maven` | Maven plugin for SEA compliance validation |
| `stratify-plugin-gradle` | Gradle plugin for SEA compliance validation |
| `stratify-archunit` | Pre-built ArchUnit rules for SEA validation |
| `stratify-archetype` | Maven archetype for scaffolding SEA modules |
| `stratify-examples` | Reference implementations |

## Scaffolding a New SEA Module

```bash
mvn archetype:generate \
  -DarchetypeGroupId=dev.engineeringlab \
  -DarchetypeArtifactId=stratify-archetype \
  -DarchetypeVersion=1.0.0-SNAPSHOT \
  -DgroupId=com.example \
  -DartifactId=my-module
```

This creates:
```
my-module/
├── pom.xml                    # Parent POM
├── my-module-common/          # L1: Foundation
├── my-module-spi/             # L2: Extension points
├── my-module-api/             # L3: Contracts
├── my-module-core/            # L4: Implementation
└── my-module-facade/          # L5: Entry point
```

## Validation

### Maven Plugin

```xml
<plugin>
    <groupId>dev.engineeringlab</groupId>
    <artifactId>stratify-plugin-maven</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>validate</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

### ArchUnit Rules (in tests)

```java
@AnalyzeClasses(packages = "com.example.mymodule")
class ArchitectureTest {

    @ArchTest
    static final ArchRule layerDependencies = SEARules.layerDependencies();

    @ArchTest
    static final ArchRule facadeEncapsulation = SEARules.facadeEncapsulation();
}
```

## Benefits

| Metric | Result |
|--------|--------|
| API surface reduction | >90% |
| Consumer isolation | Compile-time enforced |
| Build validation | <5s |

## Requirements

- Java 21+
- Maven 3.9+ or Gradle 8.5+

## License

MIT License - see [LICENSE](LICENSE)

## Contributing

Contributions welcome! Please read the [contributing guidelines](CONTRIBUTING.md) first.
