# ADR-016: Automated Release Workflow

## Status

Accepted

## Date

2025-12-17

## Context

Manual release processes are error-prone and time-consuming. Key issues:

1. **Inconsistency**: Version numbers may not be updated in all places
2. **Human error**: Forgetting to tag, push, or update versions
3. **Multi-repo coordination**: Releasing multiple repositories requires repetitive steps
4. **Audit trail**: Manual releases lack clear documentation of who released what and when

The framework uses CalVer versioning (see [ADR-015](015-calendar-versioning-strategy.md)) and needs an automated workflow to:
- Update Maven versions across all modules
- Create git tags
- Publish artifacts
- Generate release notes

## Decision

Adopt **GitHub Actions** for automated releases with a manual trigger (`workflow_dispatch`).

### Workflow Design

```yaml
# .github/workflows/release.yml
name: Release

on:
  workflow_dispatch:
    inputs:
      version:
        description: 'Release version (CalVer: YYYY.MM.PATCH)'
        required: true
        type: string
      dry-run:
        description: 'Dry run (no push)'
        required: false
        type: boolean
        default: false

env:
  JAVA_VERSION: '21'

jobs:
  validate:
    runs-on: ubuntu-latest
    steps:
      - name: Validate version format
        run: |
          if ! [[ "${{ inputs.version }}" =~ ^[0-9]{4}\.[0-9]{2}\.[0-9]+$ ]]; then
            echo "Error: Version must be in CalVer format YYYY.MM.PATCH"
            exit 1
          fi

  release:
    needs: validate
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
          token: ${{ secrets.RELEASE_TOKEN }}

      - name: Set up JDK
        uses: actions/setup-java@v4
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: 'temurin'
          cache: maven

      - name: Configure Git
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"

      - name: Update version
        run: |
          mvn versions:set -DnewVersion=${{ inputs.version }} -DprocessAllModules=true
          mvn versions:commit

      - name: Build and verify
        run: mvn clean verify -DskipTests

      - name: Commit version change
        run: |
          git add -A
          git commit -m "chore: release ${{ inputs.version }}"

      - name: Create tag
        run: git tag -a "v${{ inputs.version }}" -m "Release ${{ inputs.version }}"

      - name: Push (if not dry-run)
        if: ${{ !inputs.dry-run }}
        run: |
          git push origin HEAD
          git push origin "v${{ inputs.version }}"

      - name: Publish to Maven Central
        if: ${{ !inputs.dry-run }}
        run: mvn deploy -DskipTests
        env:
          MAVEN_USERNAME: ${{ secrets.MAVEN_USERNAME }}
          MAVEN_PASSWORD: ${{ secrets.MAVEN_PASSWORD }}
          MAVEN_GPG_PASSPHRASE: ${{ secrets.MAVEN_GPG_PASSPHRASE }}

  post-release:
    needs: release
    if: ${{ !inputs.dry-run }}
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.ref }}

      - name: Set next snapshot version
        run: |
          # Calculate next month's snapshot
          NEXT_YEAR=$(date -d "next month" +%Y)
          NEXT_MONTH=$(date -d "next month" +%m)
          NEXT_VERSION="${NEXT_YEAR}.${NEXT_MONTH}.0-SNAPSHOT"

          mvn versions:set -DnewVersion=$NEXT_VERSION -DprocessAllModules=true
          mvn versions:commit

          git add -A
          git commit -m "chore: prepare next development cycle $NEXT_VERSION"
          git push origin HEAD

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          tag_name: v${{ inputs.version }}
          generate_release_notes: true
```

### Required Secrets

| Secret | Description |
|--------|-------------|
| `RELEASE_TOKEN` | GitHub PAT with repo write access |
| `MAVEN_USERNAME` | Maven Central username |
| `MAVEN_PASSWORD` | Maven Central password |
| `MAVEN_GPG_PASSPHRASE` | GPG key passphrase for signing |

### Usage

1. Navigate to Actions → Release workflow
2. Click "Run workflow"
3. Enter version (e.g., `2025.12.0`)
4. Optionally enable dry-run to test
5. Click "Run workflow"

### Multi-Repository Releases

For coordinated releases across `stratify` and `swe-framework`:

```yaml
# In swe-framework repo
- name: Trigger stratify release
  uses: actions/github-script@v7
  with:
    github-token: ${{ secrets.RELEASE_TOKEN }}
    script: |
      await github.rest.actions.createWorkflowDispatch({
        owner: 'phdsystems',
        repo: 'stratify',
        workflow_id: 'release.yml',
        ref: 'main',
        inputs: { version: '${{ inputs.version }}' }
      })
```

## Rationale

### Why GitHub Actions over alternatives

| Option | Pros | Cons |
|--------|------|------|
| **GitHub Actions** | Native integration, free for public repos, workflow_dispatch UI | GitHub-specific |
| Maven Release Plugin | Maven-native, well-documented | Complex configuration, poor error handling |
| Shell scripts | Simple, portable | No UI, no audit trail, manual execution |
| Jenkins | Powerful, self-hosted | Maintenance overhead, separate infrastructure |

GitHub Actions chosen for:
1. **Native integration**: Direct access to repo, tags, releases
2. **Manual trigger UI**: Easy version input without CLI access
3. **Audit trail**: All runs logged with inputs and outputs
4. **Secrets management**: Secure credential handling
5. **Reusable workflows**: Can share across repositories

### Why workflow_dispatch

- **Intentional releases**: Prevents accidental releases from automated triggers
- **Version control**: Human decides exact version number
- **Dry-run capability**: Test before actual release
- **No branch restrictions**: Can release from any branch if needed

## Consequences

### Positive

- Consistent, repeatable releases
- Clear audit trail of all releases
- Reduced human error
- Faster release cycle
- Automatic next-snapshot preparation

### Negative

- GitHub dependency for releases
- Requires secret management setup
- Initial workflow configuration effort

### Migration

1. Create workflow file in each repository
2. Configure required secrets
3. Test with dry-run
4. Document in repository README

## Testing

### Local Testing

Simulate the release process locally without pushing:

```bash
# 1. Update versions (dry-run simulation)
./mvnw versions:set -DnewVersion=2025.12.1 -DprocessAllModules=true

# 2. Verify build passes
./mvnw clean verify -DskipTests

# 3. Check what would be committed
git status
git diff --stat

# 4. Revert changes (don't actually release)
git checkout .
./mvnw versions:revert
```

### GitHub Dry-Run Testing

Test the full workflow on GitHub without pushing changes:

1. Push workflow file to repository
2. Navigate to **Actions** → **Release**
3. Click **Run workflow**
4. Enter test version (e.g., `2025.12.1`)
5. Check **Dry run** checkbox ✓
6. Click **Run workflow**

The workflow will:
- ✅ Validate version format
- ✅ Update pom.xml versions
- ✅ Build and verify
- ✅ Create commit (local)
- ✅ Create tag (local)
- ❌ Skip push (dry-run)
- ❌ Skip GitHub release (dry-run)

Review the workflow logs to verify each step succeeded.

### Checklist Before Production Release

- [ ] Dry-run completed successfully
- [ ] All tests pass locally
- [ ] CHANGELOG updated
- [ ] No uncommitted changes on release branch
- [ ] `RELEASE_TOKEN` secret configured in repo settings

## References

- [ADR-015: Calendar Versioning Strategy](015-calendar-versioning-strategy.md)
- [GitHub Actions workflow_dispatch](https://docs.github.com/en/actions/using-workflows/events-that-trigger-workflows#workflow_dispatch)
- [Maven Versions Plugin](https://www.mojohaus.org/versions-maven-plugin/)
