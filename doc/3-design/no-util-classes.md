# No *Util Classes

## The Problem

Classes named `*Util`, `*Utils`, `*Helper`, or `*Manager` are anti-patterns. The name itself is a code smell.

**"Util" means "I don't know where this belongs."**

When you create `StringUtils`, you're admitting you couldn't find a proper home for these methods. This leads to:

## Why *Util is Harmful

### 1. No Cohesion

```java
// What do these have in common? Nothing.
public class StringUtils {
    public static String capitalize(String s) { ... }
    public static String truncate(String s, int len) { ... }
    public static boolean isEmail(String s) { ... }
    public static String toSnakeCase(String s) { ... }
}
```

These are unrelated operations dumped in one place because they all take `String`.

### 2. Grows Unbounded

Util classes attract more methods over time. "It's already a dumping ground, one more won't hurt." Six months later: 47 methods, 800 lines.

### 3. Primitive Obsession

Util classes often indicate missing domain objects:

```java
// Bad: StringUtils.isValidEmail(email)
// Good: Email.parse(string) throws InvalidEmailException

// Bad: DateUtils.addDays(date, 5)
// Good: date.plusDays(5)
```

### 4. Discoverability

Where's the method to parse a URL? `UrlUtils`? `StringUtils`? `NetworkUtils`? `HttpUtils`?

With proper design: `Url.parse(string)` — obvious.

### 5. Testing Difficulty

Static utility methods are hard to mock. Classes with behavior can be injected and tested.

## The Alternative

### Put Methods Where They Belong

| Instead of | Use |
|------------|-----|
| `StringUtils.capitalize(s)` | `s.capitalize()` or `Name.of(s)` |
| `CollectionUtils.isEmpty(list)` | `list.isEmpty()` |
| `DateUtils.format(date)` | `date.format(pattern)` |
| `FileUtils.readLines(path)` | `Files.readAllLines(path)` |

### Create Domain Objects

```java
// Bad
String email = "user@example.com";
if (EmailUtils.isValid(email)) {
    EmailUtils.send(email, subject, body);
}

// Good
Email email = Email.parse("user@example.com");  // Throws if invalid
email.send(message);
```

### Use Meaningful Names

If you must have a class with static factory methods, name it after what it creates:

```java
// Bad
RuleUtils.createLayerRule(...)

// Good
Rules.commonLayer(...)    // Returns a Rule
Dates.parse(...)          // Returns a Date
Paths.get(...)            // Returns a Path
```

## Exceptions

Some `*s` (plural) classes are acceptable when they're factory/companion classes for a type:

- `java.nio.file.Files` — operations on `Path`
- `java.util.Collections` — operations on `Collection`
- `java.util.Arrays` — operations on arrays

These are cohesive (all methods relate to one type) and bounded (won't grow randomly).

## In Stratify

We use `Rules` (not `RuleUtils`) because:

1. **Cohesive** — All methods return `Rule` for architecture validation
2. **Bounded** — Only 7 specific SEA layer rules
3. **Discoverable** — `Rules.commonLayer(...)` is obvious
4. **Proper name** — It's a factory for `Rule` objects, not a dumping ground

```java
// Clear intent
Rules.commonLayer("com.example").enforce("com.example");
Rules.all("com.example").forEach(rule -> rule.enforce(classes));
```

## Summary

> "Util" is not a name. It's an admission of design failure.

Before creating `*Util`:
1. Can this be an instance method on an existing class?
2. Should this be a new domain object?
3. Is there a better name that describes what this class *does*?

If you can't answer these, the code needs more design thought — not a Util class.
