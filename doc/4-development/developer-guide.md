# Stratify Developer Guide

## Getting Started

### Installation

Add Stratify to your project:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>dev.engineeringlab</groupId>
            <artifactId>stratify-bom</artifactId>
            <version>1.0.0-SNAPSHOT</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>dev.engineeringlab</groupId>
        <artifactId>stratify-core</artifactId>
    </dependency>
</dependencies>
```

## Core Concepts

### Annotations

#### @Provider

Marks a class as an SPI provider implementation:

```java
@Provider(name = "openai", priority = 10, description = "OpenAI provider")
public class OpenAIProvider implements TextProvider {
    // Implementation
}
```

| Attribute | Description | Default |
|-----------|-------------|---------|
| `name` | Unique identifier | required |
| `priority` | Selection priority (higher = preferred) | 0 |
| `description` | Human-readable description | "" |
| `enabledByDefault` | Auto-enable on discovery | true |

#### @SEAModule

Marks a package/class with its SEA layer:

```java
@SEAModule(layer = Layer.FACADE, name = "my-module")
package com.example.mymodule;
```

#### @Facade

Marks a class as a facade entry point:

```java
@Facade
public final class TextProcessing {
    public static String process(String input) {
        return getDefaultProvider().process(input);
    }
}
```

#### @Internal

Marks types not for external use:

```java
@Internal(reason = "Implementation detail")
public class InternalHelper {
    // Not part of public API
}
```

### Registry

Generic provider storage and retrieval:

```java
// Create registry
Registry<TextProvider> registry = new Registry<>();

// Register providers
registry.register("openai", new OpenAIProvider());
registry.register("anthropic", new AnthropicProvider());

// Retrieve
TextProvider provider = registry.getOrThrow("openai");
Optional<TextProvider> maybe = registry.get("unknown");

// Find by predicate
List<TextProvider> streaming = registry.find(p -> p.supportsStreaming());
```

### ProviderRegistry

Priority-aware registry with automatic annotation extraction:

```java
ProviderRegistry<TextProvider> registry = new ProviderRegistry<>();

// Register using @Provider annotation metadata
registry.register(new OpenAIProvider());     // priority=10
registry.register(new AnthropicProvider());  // priority=5

// Get highest priority
TextProvider best = registry.getDefaultOrThrow();  // Returns OpenAI

// Get specific
TextProvider anthropic = registry.getOrThrow("anthropic");
```

### Provider Discovery

Automatic ServiceLoader-based discovery:

```java
// Discover all implementations
List<TextProvider> providers = ProviderDiscovery.discover(TextProvider.class);

// Load into registry (only @Provider annotated)
ProviderRegistry<TextProvider> registry =
    ProviderDiscovery.loadRegistry(TextProvider.class);

// Find best provider
Optional<TextProvider> best = ProviderDiscovery.findBestProvider(TextProvider.class);
```

**ServiceLoader Setup** (`META-INF/services/com.example.TextProvider`):
```
com.example.providers.OpenAIProvider
com.example.providers.AnthropicProvider
```

### Configuration

Hierarchical configuration with environment overrides:

```java
// Load from default locations
StratifyConfig config = ConfigLoader.load();

// Get values (checks: system prop → env var → config file)
String provider = config.get("provider.default", "openai");
int timeout = config.getInt("provider.timeout", 30000);
boolean debug = config.getBoolean("debug.enabled", false);

// Build programmatically
StratifyConfig config = StratifyConfig.builder()
    .property("provider.default", "openai")
    .property("provider.timeout", "30000")
    .build();
```

**Configuration resolution order**:
1. System property: `stratify.provider.default`
2. Environment variable: `STRATIFY_PROVIDER_DEFAULT`
3. Config file property: `provider.default`

## Creating an SEA Module

### 1. Scaffold Structure

```bash
mvn archetype:generate \
  -DarchetypeGroupId=dev.engineeringlab \
  -DarchetypeArtifactId=stratify-archetype \
  -DgroupId=com.example \
  -DartifactId=text-processor
```

### 2. Define Common Types (L1)

```java
// text-processor-common/src/.../common/model/ProcessRequest.java
public record ProcessRequest(String input, Map<String, Object> options) {}

// text-processor-common/src/.../common/exception/ProcessException.java
public class ProcessException extends RuntimeException {
    private final ErrorCode errorCode;
    // ...
}
```

### 3. Define SPI (L2)

```java
// text-processor-spi/src/.../spi/TextProcessor.java
public interface TextProcessor {
    String name();
    boolean supports(String format);
    ProcessResult process(ProcessRequest request);
}
```

### 4. Define API (L3)

```java
// text-processor-api/src/.../api/TextService.java
public interface TextService {
    ProcessResult process(String input);
    CompletableFuture<ProcessResult> processAsync(String input);
}
```

### 5. Implement Core (L4)

```java
// text-processor-core/src/.../core/DefaultTextProcessor.java
@Provider(name = "default", priority = 0)
public class DefaultTextProcessor implements TextProcessor {
    @Override
    public ProcessResult process(ProcessRequest request) {
        // Implementation
    }
}
```

### 6. Create Facade (L5)

```java
// text-processor-facade/src/.../TextProcessing.java
@Facade
public final class TextProcessing {

    private static final ProviderRegistry<TextProcessor> REGISTRY =
        ProviderDiscovery.loadRegistry(TextProcessor.class);

    public static ProcessResult process(String input) {
        return REGISTRY.getDefaultOrThrow()
            .process(new ProcessRequest(input, Map.of()));
    }

    public static TextProcessor using(String providerName) {
        return REGISTRY.getOrThrow(providerName);
    }
}
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

**Goals**:
- `stratify:validate` - Check SEA compliance (fails build on violations)
- `stratify:scan` - Discover module structure
- `stratify:report` - Generate compliance report

### ArchUnit Rules

```java
@AnalyzeClasses(packages = "com.example.textprocessor")
class ArchitectureTest {

    @ArchTest
    static final ArchRule layerDependencies = SEARules.layerDependencies();

    @ArchTest
    static final ArchRule facadeEncapsulation = SEARules.facadeEncapsulation();

    @ArchTest
    static final ArchRule providerConventions = SEARules.providerConventions();

    @ArchTest
    static final ArchRule noCircularDependencies = SEARules.noCircularDependencies();
}
```

## Best Practices

1. **Keep facades simple** - Static methods, delegation only, no business logic
2. **Use providers for extensibility** - Implement SPI for pluggable behavior
3. **Types in common** - DTOs, errors, and constants go in L1
4. **Interfaces in API** - Consumer contracts go in L3
5. **Hide implementations** - Core (L4) is never directly imported by consumers
6. **Priority for defaults** - Use `@Provider(priority=...)` for default selection
7. **Health checks** - Implement `isHealthy()` for provider availability
