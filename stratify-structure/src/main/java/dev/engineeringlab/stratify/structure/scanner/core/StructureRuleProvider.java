package dev.engineeringlab.stratify.structure.scanner.core;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.rule.*;
import dev.engineeringlab.stratify.structure.scanner.spi.Scanner;
import dev.engineeringlab.stratify.structure.scanner.spi.ScannerProvider;
import java.util.List;

/**
 * Rule provider for module structure rules (MS-*), modular design rules (MD-*), aggregator rules
 * (AG-*), and parent aggregator rules (PA-*).
 *
 * <p>Provides MS-* rules for validating module structure compliance:
 *
 * <ul>
 *   <li>MS-001: API module exists
 *   <li>MS-002: Core module exists
 *   <li>MS-003: Facade module exists
 *   <li>MS-004: SPI module conditional
 *   <li>MS-005: Parent POM packaging
 *   <li>MS-006: No spi subpackage duplication
 *   <li>MS-007: No api subpackage duplication
 *   <li>MS-009: Component completeness
 *   <li>MS-010: Common module first
 *   <li>MS-011: Leaf modules must have parent
 *   <li>MS-012: Leaf module parent must be parent module
 *   <li>MS-013: Parent modules should not have parent
 *   <li>MS-014: Aggregator modules should not contain source code
 *   <li>MS-015: Aggregator modules should not have dependencies
 *   <li>MS-016: All submodule directories must be listed
 *   <li>MS-017: Pure aggregator must use -aggregator suffix
 *   <li>MS-020: Bounded context scope
 *   <li>MS-021: YAML config matches folder structure
 *   <li>MS-022: Module type matches layer structure
 *   <li>MS-023: Module documentation structure
 * </ul>
 *
 * <p>Provides MD-* rules for validating modular design:
 *
 * <ul>
 *   <li>MD-001: Parent aggregator hierarchy (above=pure aggregator, below=leaf modules)
 * </ul>
 *
 * <p>Provides AG-* rules for validating pure aggregator modules:
 *
 * <ul>
 *   <li>AG-001: Aggregator must have config/module.aggregator.yml
 *   <li>AG-002: Aggregator can only contain config/, doc/, pom.xml, and declared modules
 *   <li>AG-003: All module directories must be declared in module.aggregator.yml
 *   <li>AG-004: Declared module types must match actual structure
 *   <li>AG-005: Aggregator must have Maven wrapper (mvnw, mvnw.cmd, .mvn)
 * </ul>
 *
 * <p>Provides PA-* rules for validating parent aggregator modules:
 *
 * <ul>
 *   <li>PA-001: Parent must have config/module.parent.yml
 *   <li>PA-002: Parent can only contain config/, doc/, pom.xml, and declared layers/submodules
 *   <li>PA-003: All layer directories must be declared in module.parent.yml
 *   <li>PA-004: Declared layers must follow valid naming pattern
 * </ul>
 */
public class StructureRuleProvider implements ScannerProvider<ModuleInfo> {

  private final List<Scanner<ModuleInfo>> scanners;

  public StructureRuleProvider() {
    this.scanners =
        List.of(
            // MS-* rules (module structure)
            new MS001ApiModuleExists(),
            new MS002CoreModuleExists(),
            new MS003FacadeModuleExists(),
            new MS004SpiModuleConditional(),
            new MS005ParentPomPackaging(),
            new MS006NoSpiSubpackageDuplication(),
            new MS007NoApiSubpackageDuplication(),
            new MS009ComponentCompleteness(),
            new MS010CommonModuleFirst(),
            new MS011LeafModulesMustHaveParent(),
            new MS012LeafModuleParentMustBeParent(),
            new MS013ParentModulesShouldNotHaveParent(),
            new MS014AggregatorNoSourceCode(),
            new MS015AggregatorNoDependencies(),
            new MS016AllSubmodulesListed(),
            new MS017PureAggregatorSuffix(),
            new MS020BoundedContextScope(),
            new MS021ConfigMatchesFolderStructure(),
            new MS023ModuleDocumentationStructure(),
            // MD-* rules (modular design)
            new MD001ParentAggregatorHierarchy(),
            // AG-* rules (pure aggregator)
            new AG001AggregatorConfigExists(),
            new AG002AggregatorAllowedContents(),
            new AG003AggregatorModulesDeclared(),
            new AG004AggregatorModuleTypesMatch(),
            new AG005AggregatorMavenWrapperExists(),
            // PA-* rules (parent aggregator)
            new PA001ParentConfigExists(),
            new PA002ParentAllowedContents(),
            new PA003ParentLayersDeclared(),
            new PA004ParentLayerTypesValid());
  }

  @Override
  public List<Scanner<ModuleInfo>> getScanners() {
    return scanners;
  }

  @Override
  public Class<ModuleInfo> getTargetType() {
    return ModuleInfo.class;
  }

  @Override
  public String getProviderId() {
    return "structure-rules";
  }

  @Override
  public String getName() {
    return "Module Structure Rules";
  }

  @Override
  public String getDescription() {
    return "Validates module structure compliance including MS-*, MD-*, AG-*, and PA-* rules";
  }

  @Override
  public int getPriority() {
    return 90; // Run after backend-rules (100)
  }
}
