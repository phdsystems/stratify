# stratify-structure Backlog

Port remaining MS-Engine rules to stratify-structure module.

## Status Legend
- `[ ]` Not started
- `[~]` In progress
- `[x]` Completed
- `[!]` Blocked/Conflicts

---

## Completed (7 rules)

| ID | Rule | Stratify Class |
|----|------|----------------|
| MS-001 | API Module Exists | `ApiModuleExists` |
| MS-002 | Core Module Exists | `CoreModuleExists` |
| MS-003 | Facade Module Exists | `FacadeModuleExists` |
| MS-004 | SPI Module Conditional | `SpiModuleConditional` |
| MS-009 | Component Completeness | `ComponentCompleteness` |
| SEA-4 | No Common Layer | `NoCommonLayer` (new) |
| SEA-106 | No Util Modules | `NoUtilModules` (new) |

---

## Backlog (17 rules)

### Category 1: POM Configuration (4 rules)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P1 | MS-005 | ParentPomPackaging | Parent/aggregator POMs must use `packaging=pom`, not `jar` |
| [ ] P2 | MS-011 | LeafModulesMustHaveParent | Leaf modules (-api, -core, -facade, -spi) must declare a parent POM |
| [ ] P2 | MS-012 | LeafModuleParentMustBeParent | Leaf module parent artifactId must end with `-parent` suffix |
| [ ] P3 | MS-013 | ParentModulesShouldNotHaveParent | Parent modules (-parent) should be standalone without parent |

### Category 2: Aggregator Rules (6 rules)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P1 | MS-014 | AggregatorNoSourceCode | Aggregator modules should not contain src/main/java or src/test/java |
| [ ] P1 | MS-015 | AggregatorNoDependencies | Aggregator modules should not declare dependencies, only dependencyManagement |
| [ ] P2 | MS-016 | AllSubmodulesListed | All subdirectories with pom.xml must be listed in `<modules>` |
| [ ] P2 | MS-017 | PureAggregatorSuffix | Pure aggregators must have artifactId ending with `-aggregator` |
| [ ] P2 | MS-025 | PureAggregatorNoDependencies | Pure aggregators (-aggregator) must not have `<dependencies>` |
| [ ] P2 | MS-026 | PureAggregatorNoDepManagement | Pure aggregators must not have `<dependencyManagement>` |

### Category 3: Parent-Child Relationships (3 rules)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P1 | MS-024 | ParentCannotContainParent | Parent aggregators must not contain other parent aggregators |
| [ ] P2 | MS-027 | ParentMustHaveDepManagement | Parent modules (-parent) must have `<dependencyManagement>` |
| [ ] P2 | MS-028 | CrossValidateParentChild | Validate parent-child relationships match expected types |

### Category 4: Package Structure (2 rules)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P2 | MS-006 | NoSpiSubpackageDuplication | Avoid nested `.../spi/spi/...` packages |
| [ ] P2 | MS-007 | NoApiSubpackageDuplication | Avoid nested `.../api/api/...` packages |

### Category 5: Advanced/Config (2 rules)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P3 | MS-020 | BoundedContextScope | Detect excessive `.getParent()` chains (3+) reaching outside bounded context |
| [ ] P3 | MS-021 | ConfigMatchesFolderStructure | Validate bootstrap.yaml matches actual folder structure |

### Category 6: Documentation (1 rule)

| Priority | ID | Rule | Description |
|----------|-----|------|-------------|
| [ ] P3 | MS-023 | ModuleDocumentationStructure | Validate doc structure: README.md, doc/3-design, doc/4-development, doc/6-operations |

---

## Excluded (1 rule)

| ID | Rule | Reason |
|----|------|--------|
| MS-010 | CommonModuleFirst | **Conflicts with SEA-4** - We prohibit common modules entirely |

---

## Implementation Order

### Phase 1: Core POM Validation (P1)
1. `MS-005` ParentPomPackaging
2. `MS-014` AggregatorNoSourceCode
3. `MS-015` AggregatorNoDependencies
4. `MS-024` ParentCannotContainParent

### Phase 2: Module Relationships (P2)
5. `MS-011` LeafModulesMustHaveParent
6. `MS-012` LeafModuleParentMustBeParent
7. `MS-016` AllSubmodulesListed
8. `MS-017` PureAggregatorSuffix
9. `MS-025` PureAggregatorNoDependencies
10. `MS-026` PureAggregatorNoDepManagement
11. `MS-027` ParentMustHaveDepManagement
12. `MS-028` CrossValidateParentChild
13. `MS-006` NoSpiSubpackageDuplication
14. `MS-007` NoApiSubpackageDuplication

### Phase 3: Advanced Rules (P3)
15. `MS-013` ParentModulesShouldNotHaveParent
16. `MS-020` BoundedContextScope
17. `MS-021` ConfigMatchesFolderStructure
18. `MS-023` ModuleDocumentationStructure

---

## Notes

- All rules must be SEA-4 aligned (no common layer support)
- Use Java records for model classes
- Support both properties (default) and YAML (optional) configuration
- File-based scanning by default, Maven Model optional
- Descriptive class names (e.g., `ParentPomPackaging.java`, not `MS005ParentPomPackaging.java`)
