# No *Common Packages

## The Problem

Packages named `common`, `shared`, `util`, or `base` are anti-patterns. The name itself is a code smell.

**"Common" means "I don't know where this belongs."**

When you create `com.example.common`, you're admitting you couldn't find a proper home for these classes. This leads to:

## Why *Common is Harmful

### 1. No Cohesion

```
com.example.common/
    StringUtils.java
    DateHelper.java
    ConfigLoader.java
    HttpClient.java
    ValidationException.java
```

These are unrelated classes dumped in one place because they're "used by multiple modules."

### 2. Grows Unbounded

Common packages attract more classes over time. "It's already a dumping ground, one more won't hurt." Six months later: 47 classes with no relationship to each other.

### 3. Circular Dependency Magnet

Everything depends on `common`. Eventually `common` needs something from another module. Now you have cycles.

```
app → common → app  // Disaster
```

### 4. Discoverability

Where's the configuration loader? `common`? `util`? `config`? `shared`?

With proper design: `config.YamlConfigurationLoader` — obvious.

### 5. Violates Single Responsibility

A package should have one reason to change. `common` has as many reasons as it has classes.

## The Alternative

### Put Classes Where They Belong

| Instead of | Use |
|------------|-----|
| `common.config.ConfigLoader` | `config.ConfigLoader` |
| `common.provider.Providers` | `provider.Providers` |
| `common.exception.BaseException` | `error.StratifyException` |
| `common.util.StringUtils` | Don't. See [no-util-classes.md](no-util-classes.md) |

### Create Cohesive Packages

```
// Bad: Dumping ground
com.example.common/
    ConfigLoader.java
    Providers.java
    StratifyException.java

// Good: Cohesive packages
com.example.config/
    ConfigLoader.java
    YamlConfigurationLoader.java

com.example.provider/
    Providers.java
    ProviderDiscovery.java

com.example.error/
    StratifyException.java
    ErrorCode.java
```

### Name Packages by Purpose

The package name should describe what the classes *do*, not how they're *used*:

- `config` — Configuration loading and management
- `provider` — Provider discovery and injection
- `error` — Error handling and exceptions
- `registry` — Component registration

## In Stratify

We eliminated `common` packages:

| Before | After |
|--------|-------|
| `stratify.common.config` | `stratify.config` |
| `stratify.common.provider` | `stratify.provider` |
| `common.util.config` | `stratify.config` |

Each package is now:
1. **Cohesive** — All classes serve one purpose
2. **Bounded** — Clear scope, won't grow randomly
3. **Discoverable** — `config.YamlConfigurationLoader` is obvious
4. **Proper name** — Describes what it does, not where it's used

## Summary

> "Common" is not a purpose. It's an admission of design failure.

Before creating a `common` package:
1. What do these classes have in common besides being reused?
2. What is the single responsibility of this package?
3. Is there a better name that describes what these classes *do*?

If the only answer is "they're used by multiple modules," the classes need proper homes — not a dumping ground.
