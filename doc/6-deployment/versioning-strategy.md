# Versioning Strategy

This document describes the versioning strategy for the EngineeringLab framework.

> **Decision Record**: See [ADR-015: Calendar Versioning Strategy](adr/015-calendar-versioning-strategy.md)

## Overview

EngineeringLab uses **Calendar Versioning (CalVer)** for all releases.

## Version Format

```
YYYY.MM.PATCH[-QUALIFIER]
```

| Component | Description | Example |
|-----------|-------------|---------|
| `YYYY` | Release year | `2025` |
| `MM` | Release month (01-12) | `12` |
| `PATCH` | Patch number within month | `0`, `1` |
| `QUALIFIER` | Optional qualifier | `SNAPSHOT`, `RC1` |

## Version Examples

| Version | Type | Description |
|---------|------|-------------|
| `2025.12.0-SNAPSHOT` | Development | Current development snapshot |
| `2025.12.0-RC1` | Pre-release | Release candidate |
| `2025.12.0` | Release | Production release |
| `2025.12.1` | Hotfix | Patch release |

## Release Process

### 1. Prepare Release

```bash
# From development branch
git checkout dev

# Update version to release
mvn versions:set -DnewVersion=2025.12.0 -DprocessAllModules=true
mvn versions:commit

# Commit version change
git add -A
git commit -m "chore: prepare release 2025.12.0"
```

### 2. Create Release

```bash
# Merge to main
git checkout main
git merge dev

# Tag release
git tag -a v2025.12.0 -m "Release 2025.12.0"
git push origin main --tags
```

### 3. Prepare Next Development Cycle

```bash
# Back to dev branch
git checkout dev

# Set next snapshot version
mvn versions:set -DnewVersion=2026.01.0-SNAPSHOT -DprocessAllModules=true
mvn versions:commit

git add -A
git commit -m "chore: prepare next development cycle 2026.01.0-SNAPSHOT"
git push origin dev
```

## Hotfix Process

For urgent fixes to released versions:

```bash
# Create hotfix branch from tag
git checkout -b hotfix/2025.12.1 v2025.12.0

# Apply fix, then update version
mvn versions:set -DnewVersion=2025.12.1 -DprocessAllModules=true

# Commit and tag
git commit -am "fix: critical bug fix"
git tag -a v2025.12.1 -m "Hotfix 2025.12.1"

# Merge to main and dev
git checkout main && git merge hotfix/2025.12.1
git checkout dev && git merge hotfix/2025.12.1
git push origin main dev --tags
```

## Module Versioning

All modules in the framework share the same version:

```
engineeringlab-framework (parent)  → 2025.12.0
├── stratify                       → 2025.12.0
├── adentic-se/llm/llm-provider    → 2025.12.0
├── adentic-se/ratelimit           → 2025.12.0
└── ...                            → 2025.12.0
```

## CI/CD Integration

### GitHub Actions

```yaml
# .github/workflows/release.yml
name: Release
on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Extract version
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT

      - name: Build and publish
        run: |
          mvn -B deploy -DskipTests
        env:
          VERSION: ${{ steps.version.outputs.VERSION }}
```

### Docker Tags

Docker images follow the same versioning:

```bash
# Release
docker build -t engineeringlab/llm-provider:2025.12.0 .
docker tag engineeringlab/llm-provider:2025.12.0 engineeringlab/llm-provider:latest

# Snapshot (use commit SHA for uniqueness)
docker build -t engineeringlab/llm-provider:2025.12.0-SNAPSHOT-abc1234 .
```

## Version Comparison

CalVer versions sort naturally:

```
2024.06.0 < 2024.12.0 < 2025.01.0 < 2025.01.1 < 2025.12.0
```

## CHANGELOG

All releases must include CHANGELOG entries. See [CHANGELOG.md](../../CHANGELOG.md) in the repository root.

Format:
```markdown
## [2025.12.0] - 2025-12-17

### Added
- New feature X

### Changed
- Updated behavior Y

### Fixed
- Bug fix Z

### Removed
- Deprecated feature W
```

## FAQ

**Q: What if we need multiple releases in one month?**
A: Increment the PATCH number: `2025.12.0`, `2025.12.1`, `2025.12.2`

**Q: How do we handle breaking changes?**
A: Document in CHANGELOG, use deprecation warnings, communicate in release notes.

**Q: Can we release on the last day of the month?**
A: Yes, use the current month. The version reflects when development completed, not publication date.
