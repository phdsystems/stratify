# Operations Manual

## Structure Scanner CLI

Scans Maven projects for SEA-4 module structure compliance and remediates violations.

### Setup

```bash
./mvnw compile -pl stratify-structure -q
```

### Commands

#### Scan

```bash
java -cp stratify-structure/target/classes \
  dev.engineeringlab.stratify.structure.cli.StructureScanner \
  scan /path/to/project
```

#### Remediate

```bash
# Dry-run (preview changes)
java -cp stratify-structure/target/classes \
  dev.engineeringlab.stratify.structure.cli.StructureScanner \
  remediate /path/to/project

# Apply changes
java -cp stratify-structure/target/classes \
  dev.engineeringlab.stratify.structure.cli.StructureScanner \
  remediate /path/to/project --apply
```

### Exit Codes

| Code | Meaning |
|------|---------|
| 0 | Project is SEA-4 compliant |
| 1 | Violations found or error |

### Scan Output

```
================================================================================
Stratify Structure Scanner
================================================================================
Scanning: /path/to/project

DISCOVERED MODULES
--------------------------------------------------------------------------------
Module: my-service
  Path: /path/to/project/my-service
  Layers: api=Y, core=Y, facade=Y, spi=Y, common=-, util=-
  Complete: Yes | SEA-4: Yes

VIOLATIONS
--------------------------------------------------------------------------------
None - project is SEA-4 compliant!

================================================================================
Summary: 1 modules, 0 violations
================================================================================
```

### Remediation

The `remediate` command migrates files from forbidden layers to compliant locations.

#### File Routing

| File Type | Target |
|-----------|--------|
| Interfaces (`public interface`) | api |
| Enums (`public enum`) | api |
| Records (`public record`) | api |
| Exceptions (`*Exception.java`) | api |
| DTOs (`*DTO.java`, `*Dto.java`) | api |
| Request/Response (`*Request.java`, `*Response.java`) | api |
| Everything else | core |

#### Remediation Output

```
================================================================================
Stratify Structure Remediation
================================================================================
Project: /path/to/project
Mode: DRY-RUN

REMEDIATE: my-service-common
--------------------------------------------------------------------------------
  [MOVE] dev/example/MyException.java
         interface/DTO/exception -> api
         -> my-service-api/src/main/java/dev/example/MyException.java
  [MOVE] dev/example/MyHelper.java
         implementation -> core
         -> my-service-core/src/main/java/dev/example/MyHelper.java
  [DELETE] my-service-common (will be empty after migration)

================================================================================
Dry-run complete: 2 actions planned
Run with --apply to execute changes
================================================================================
```

### Rules Checked

| Rule | Severity | Description |
|------|----------|-------------|
| SS-001 | ERROR | API module required (`{base}-api`) |
| SS-002 | ERROR | Core module required (`{base}-core`) |
| SS-006 | ERROR | Common layer forbidden (`{base}-common`) |
| SS-007 | ERROR | Util modules are anti-pattern (`{base}-util`) |

### SEA-4 Compliance

A module is SEA-4 compliant when:
- Has `-api` module (public contracts)
- Has `-core` module (implementation)
- Does NOT have `-common` module (forbidden)

Optional layers that don't affect compliance:
- `-facade` - Simplified external API
- `-spi` - Service Provider Interface for extensibility
