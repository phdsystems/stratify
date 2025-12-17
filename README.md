# Stratify

**Stratified Encapsulation Architecture (SEA) Framework for Java**

Stratify provides the 5-layer SEA pattern with tooling for scaffolding, validation, and architectural compliance.

```
FACADE  (L5) ─ Consumer entry point (only externally visible)
   │
CORE    (L4) ─ Implementation (exports nothing)
   │
API     (L3) ─ Consumer contracts
   │
SPI     (L2) ─ Extension points
   │
COMMON  (L1) ─ Foundation (DTOs, models, errors)
```

## Quick Start

```xml
<dependency>
    <groupId>dev.engineeringlab</groupId>
    <artifactId>stratify-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
@Provider(name = "myProvider", priority = 10)
public class MyProvider implements ProviderBase {
    public String name() { return "myProvider"; }
}

// Discovery
ProviderRegistry<MyProvider> registry = ProviderDiscovery.loadRegistry(MyProvider.class);
MyProvider provider = registry.getDefaultOrThrow();
```

## Modules

| Module | Purpose |
|--------|---------|
| `stratify-annotations` | @SEAModule, @Provider, @Facade, @Internal |
| `stratify-core` | Registry, ProviderDiscovery, Config |
| `stratify-rules` | Zero-dependency SEA architecture rules |
| `stratify-archetype` | Scaffold new SEA modules |

## Documentation

- [Architecture](doc/3-design/architecture.md) - Full SEA pattern documentation
- [Developer Guide](doc/4-development/developer-guide.md) - How to use Stratify

## Requirements

- Java 21+
- Maven 3.9+ or Gradle 8.5+

## License

MIT
