package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-004: SPI module conditional.
 *
 * <p>SPI module should exist if external provider dependencies are detected. This rule warns when
 * provider dependencies (redis, kafka, etc.) are found but no SPI module exists.
 */
public class MS004SpiModuleConditional extends AbstractStructureRule {

  private static final List<String> PROVIDER_INDICATORS =
      List.of(
          "redis",
          "jedis",
          "postgresql",
          "pgvector",
          "pinecone",
          "weaviate",
          "chromadb",
          "qdrant",
          "kafka",
          "rabbitmq",
          "activemq",
          "neo4j",
          "janusgraph");

  public MS004SpiModuleConditional() {
    super("MS-004");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    if (!module.isParent()) {
      return false;
    }
    if (isPureAggregator(module)) {
      return false;
    }
    // Standalone modules don't use the SPI pattern
    return !module.isStandaloneModule();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    boolean hasSpi = module.hasSpiModule();
    boolean needsSpi = detectExternalProviderDependencies(module);

    if (!needsSpi) {
      // No SPI needed
      return List.of();
    }

    if (hasSpi) {
      // Has SPI and needs it
      return List.of();
    }

    // Needs SPI but doesn't have it
    return List.of(
        createViolation(
            module,
            String.format(
                "SPI module '%s-spi' should exist. External provider dependencies detected "
                    + "but no SPI module found. Consider creating an SPI module for pluggable providers.",
                module.getModuleName()),
            module.getBasePath().toString()));
  }

  private boolean detectExternalProviderDependencies(ModuleInfo moduleInfo) {
    for (ModuleInfo.SubModuleInfo subModule : moduleInfo.getSubModules().values()) {
      if (subModule.isExists() && subModule.getDependencies() != null) {
        for (String dep : subModule.getDependencies()) {
          String depLower = dep.toLowerCase();
          for (String indicator : PROVIDER_INDICATORS) {
            if (depLower.contains(indicator)) {
              return true;
            }
          }
        }
      }
    }
    return false;
  }

  private boolean isPureAggregator(ModuleInfo moduleInfo) {
    return !moduleInfo.hasApiModule()
        && !moduleInfo.hasCoreModule()
        && !moduleInfo.hasFacadeModule()
        && !moduleInfo.hasSpiModule();
  }
}
