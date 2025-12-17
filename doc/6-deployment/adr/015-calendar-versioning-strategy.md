# ADR-015: Calendar Versioning (CalVer) Strategy

## Status

Accepted

## Date

2025-12-17

## Context

The EngineeringLab framework needs a consistent versioning strategy for releases. Key considerations:

1. **Predictability**: Users should understand when releases happen
2. **Simplicity**: Version numbers should be easy to understand and compare
3. **Tooling compatibility**: Must work with Maven, Docker, and CI/CD pipelines
4. **Multi-module support**: Strategy must work across all framework modules

Previously using `0.x.x-SNAPSHOT` during development without a formal release versioning strategy.

## Decision

Adopt **Calendar Versioning (CalVer)** with the following format:

```
YYYY.MM.PATCH[-QUALIFIER]
```

### Version Components

| Component | Description | Example |
|-----------|-------------|---------|
| `YYYY` | Full year of release | `2025` |
| `MM` | Month of release (zero-padded) | `01`, `12` |
| `PATCH` | Incremental patch number within month | `0`, `1`, `2` |
| `QUALIFIER` | Optional: `SNAPSHOT`, `RC1`, `BETA` | `-SNAPSHOT` |

### Examples

| Version | Meaning |
|---------|---------|
| `2025.12.0` | First release of December 2025 |
| `2025.12.1` | Second release of December 2025 (patch) |
| `2025.12.0-SNAPSHOT` | Development snapshot for December 2025 |
| `2025.12.0-RC1` | Release candidate 1 for December 2025 |

### Release Workflow

1. **Development**: `YYYY.MM.0-SNAPSHOT`
2. **Release candidate**: `YYYY.MM.0-RC1`
3. **Release**: `YYYY.MM.0`
4. **Hotfix**: `YYYY.MM.1`
5. **Next cycle**: `YYYY.{MM+1}.0-SNAPSHOT`

### Maven Commands

```bash
# Set release version
mvn versions:set -DnewVersion=2025.12.0

# After release, set next snapshot
mvn versions:set -DnewVersion=2026.01.0-SNAPSHOT

# Update all child modules
mvn versions:set -DnewVersion=2025.12.0 -DprocessAllModules=true
```

### Git Tagging

```bash
# Tag release
git tag -a v2025.12.0 -m "Release 2025.12.0"
git push origin v2025.12.0
```

## Rationale

### Why CalVer over SemVer

| Aspect | CalVer | SemVer |
|--------|--------|--------|
| Version meaning | When it was released | What changed |
| Major bumps | Yearly (automatic) | Breaking changes (subjective) |
| User expectations | Time-based freshness | API compatibility |
| Decision burden | Low (date-driven) | High (classify changes) |

CalVer is preferred because:

1. **Reduces bikeshedding**: No debates about "is this a major or minor change?"
2. **Clear freshness signal**: Users immediately know how old a version is
3. **Predictable cadence**: Aligns with monthly/quarterly release planning
4. **Framework nature**: As an internal framework, API stability is managed through deprecation cycles rather than major version bumps

### Adopted by

- Ubuntu (`24.04`)
- Python (`3.12`)
- pip (`24.0`)
- Black (`24.10.0`)
- many infrastructure tools

## Consequences

### Positive

- Simplified release decision-making
- Clear age indicator for versions
- Natural alignment with time-based release cycles
- Easy to automate in CI/CD

### Negative

- No semantic indication of breaking changes (must use CHANGELOG)
- Requires discipline to maintain CHANGELOG
- Less familiar to some developers

### Migration

Current `0.2.0-SNAPSHOT` will transition to `2025.12.0-SNAPSHOT` or next month's version when ready for first CalVer release.

## References

- [CalVer.org](https://calver.org/)
- [Semantic Versioning](https://semver.org/) (for comparison)
