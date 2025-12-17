package dev.engineeringlab.stratify.structure.scanner.common.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import org.apache.maven.model.Model;

/** Contains information about a module's structure and configuration. */
@Data
@Builder
public class ModuleInfo {
  /** Maven artifact ID of the module being analyzed. */
  private String artifactId;

  /** Maven group ID of the module being analyzed. */
  private String groupId;

  /** Module name (e.g., "agent", "llm-memory"). */
  private String moduleName;

  /** Base directory of the module. */
  private Path basePath;

  /** Whether this is a parent/aggregator module. */
  @Builder.Default private boolean isParent = false;

  /** Sub-modules found (api, core, facade, spi). */
  @Builder.Default private Map<String, SubModuleInfo> subModules = new HashMap<>();

  /** Package base path (e.g., "dev/engineeringlab/agent"). */
  private String packageBase;

  /** POM model. */
  private Model pomModel;

  /** Code quality plugins found in the module. */
  @Builder.Default private List<PluginInfo> codeQualityPlugins = new ArrayList<>();

  /** Ordered list of module names from parent POM's &lt;modules&gt; section. */
  @Builder.Default private List<String> moduleOrder = new ArrayList<>();

  /** Information about a sub-module. */
  @Data
  @Builder
  public static class SubModuleInfo {
    /** Sub-module name (e.g., "api", "core", "facade", "spi"). */
    private String name;

    /** Path to the sub-module directory. */
    private Path path;

    /** Whether the sub-module exists. */
    private boolean exists;

    /** Maven POM model for this sub-module. */
    private Model pomModel;

    /** Maven artifact ID. */
    private String artifactId;

    /** Maven group ID. */
    private String groupId;

    /** List of dependencies (artifact IDs). */
    @Builder.Default private List<String> dependencies = new ArrayList<>();

    /** List of Java source files in src/main/java. */
    @Builder.Default private List<Path> javaSourceFiles = new ArrayList<>();

    /** Path to the exception package (if exists). */
    private Path exceptionPackagePath;

    /** Code quality plugins configured for this sub-module. */
    @Builder.Default private List<PluginInfo> codeQualityPlugins = new ArrayList<>();

    /** List of test source files in src/test/java. */
    @Builder.Default private List<Path> testSourceFiles = new ArrayList<>();
  }

  /**
   * Checks if API module exists.
   *
   * @return true if API module exists
   */
  public boolean hasApiModule() {
    SubModuleInfo api = subModules.get("api");
    return api != null && api.exists;
  }

  /**
   * Checks if Core module exists.
   *
   * @return true if Core module exists
   */
  public boolean hasCoreModule() {
    SubModuleInfo core = subModules.get("core");
    return core != null && core.exists;
  }

  /**
   * Checks if Facade module exists.
   *
   * @return true if Facade module exists
   */
  public boolean hasFacadeModule() {
    SubModuleInfo facade = subModules.get("facade");
    return facade != null && facade.exists;
  }

  /**
   * Checks if SPI module exists.
   *
   * @return true if SPI module exists
   */
  public boolean hasSpiModule() {
    SubModuleInfo spi = subModules.get("spi");
    return spi != null && spi.exists;
  }

  /**
   * Checks if Common module exists.
   *
   * @return true if Common module exists
   */
  public boolean hasCommonModule() {
    SubModuleInfo common = subModules.get("common");
    return common != null && common.exists;
  }

  /**
   * Checks if Commons module exists (plural form).
   *
   * @return true if Commons module exists
   */
  public boolean hasCommonsModule() {
    SubModuleInfo commons = subModules.get("commons");
    return commons != null && commons.exists;
  }

  /**
   * Checks if Util module exists.
   *
   * @return true if Util module exists
   */
  public boolean hasUtilModule() {
    SubModuleInfo util = subModules.get("util");
    return util != null && util.exists;
  }

  /**
   * Checks if Utils module exists (plural form).
   *
   * @return true if Utils module exists
   */
  public boolean hasUtilsModule() {
    SubModuleInfo utils = subModules.get("utils");
    return utils != null && utils.exists;
  }

  /**
   * Gets API module info.
   *
   * @return the API sub-module info, or null if not found
   */
  public SubModuleInfo getApiModule() {
    return subModules.get("api");
  }

  /**
   * Gets Core module info.
   *
   * @return the Core sub-module info, or null if not found
   */
  public SubModuleInfo getCoreModule() {
    return subModules.get("core");
  }

  /**
   * Gets Facade module info.
   *
   * @return the Facade sub-module info, or null if not found
   */
  public SubModuleInfo getFacadeModule() {
    return subModules.get("facade");
  }

  /**
   * Gets SPI module info.
   *
   * @return the SPI sub-module info, or null if not found
   */
  public SubModuleInfo getSpiModule() {
    return subModules.get("spi");
  }

  /**
   * Gets Common module info.
   *
   * @return the Common sub-module info, or null if not found
   */
  public SubModuleInfo getCommonModule() {
    return subModules.get("common");
  }

  /**
   * Checks if any standalone-eligible module exists (common, commons, util, utils).
   *
   * @return true if any of the standalone-eligible modules exist
   */
  private boolean hasAnyStandaloneEligibleModule() {
    return hasCommonModule() || hasCommonsModule() || hasUtilModule() || hasUtilsModule();
  }

  /**
   * Checks if this is a standalone core module.
   *
   * <p>A standalone core module is one where only -core exists without -api or -spi. This is a
   * valid pattern - the full 4-layer facade pattern (api/spi/core/facade) is only required when API
   * contracts are needed.
   *
   * @return true if only core module exists without api/spi
   */
  public boolean isStandaloneCoreModule() {
    return hasCoreModule()
        && !hasApiModule()
        && !hasSpiModule()
        && !hasAnyStandaloneEligibleModule();
  }

  /**
   * Checks if this is a standalone common module.
   *
   * <p>A standalone common module is one where only -common exists without -api, -spi, or -core.
   * This is a valid pattern for shared utilities that don't require the full facade pattern.
   *
   * @return true if only common module exists without api/spi/core
   */
  public boolean isStandaloneCommonModule() {
    return hasCommonModule()
        && !hasApiModule()
        && !hasSpiModule()
        && !hasCoreModule()
        && !hasCommonsModule()
        && !hasUtilModule()
        && !hasUtilsModule();
  }

  /**
   * Checks if this is a standalone commons module (plural form).
   *
   * <p>A standalone commons module is one where only -commons exists without -api, -spi, or -core.
   * This is a valid pattern for shared utilities that don't require the full facade pattern.
   *
   * @return true if only commons module exists without api/spi/core
   */
  public boolean isStandaloneCommonsModule() {
    return hasCommonsModule()
        && !hasApiModule()
        && !hasSpiModule()
        && !hasCoreModule()
        && !hasCommonModule()
        && !hasUtilModule()
        && !hasUtilsModule();
  }

  /**
   * Checks if this is a standalone util module.
   *
   * <p>A standalone util module is one where only -util exists without -api, -spi, or -core. This
   * is a valid pattern for utility classes that don't require the full facade pattern.
   *
   * @return true if only util module exists without api/spi/core
   */
  public boolean isStandaloneUtilModule() {
    return hasUtilModule()
        && !hasApiModule()
        && !hasSpiModule()
        && !hasCoreModule()
        && !hasCommonModule()
        && !hasCommonsModule()
        && !hasUtilsModule();
  }

  /**
   * Checks if this is a standalone utils module (plural form).
   *
   * <p>A standalone utils module is one where only -utils exists without -api, -spi, or -core. This
   * is a valid pattern for utility classes that don't require the full facade pattern.
   *
   * @return true if only utils module exists without api/spi/core
   */
  public boolean isStandaloneUtilsModule() {
    return hasUtilsModule()
        && !hasApiModule()
        && !hasSpiModule()
        && !hasCoreModule()
        && !hasCommonModule()
        && !hasCommonsModule()
        && !hasUtilModule();
  }

  /**
   * Checks if this is a standalone module (core-only, common-only, commons-only, util-only, or
   * utils-only).
   *
   * <p>Standalone modules are valid patterns where the full 4-layer facade pattern is not required.
   *
   * @return true if this is a standalone module
   */
  public boolean isStandaloneModule() {
    return isStandaloneCoreModule()
        || isStandaloneCommonModule()
        || isStandaloneCommonsModule()
        || isStandaloneUtilModule()
        || isStandaloneUtilsModule();
  }

  /** Suffixes that identify leaf modules. */
  private static final java.util.Set<String> LEAF_SUFFIXES =
      java.util.Set.of(
          "-api", "-core", "-facade", "-spi", "-common", "-commons", "-util", "-utils");

  /**
   * Checks if any child module in moduleOrder has a leaf suffix.
   *
   * <p>This handles cases where children are named differently from the parent base name (e.g.,
   * parent "swq-scanner-aggregator" with children "swq-api", "swq-core"). This is useful for
   * determining if a module is a parent aggregator when the children don't follow the standard
   * {base}-api naming pattern.
   *
   * @return true if any child in moduleOrder ends with a leaf suffix
   */
  public boolean hasAnyLeafChildren() {
    if (moduleOrder == null || moduleOrder.isEmpty()) {
      return false;
    }
    for (String childName : moduleOrder) {
      for (String suffix : LEAF_SUFFIXES) {
        if (childName.endsWith(suffix)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Checks if this module has layer submodules, either through standard naming or any leaf
   * children.
   *
   * <p>This is a comprehensive check that covers both:
   *
   * <ul>
   *   <li>Standard naming: {base}-api, {base}-core, {base}-facade, {base}-spi
   *   <li>Non-standard naming: any child ending with -api, -core, -facade, -spi, etc.
   * </ul>
   *
   * @return true if this module has any layer submodules
   */
  public boolean hasAnyLayerModules() {
    // Check standard naming in subModules map
    if (hasApiModule() || hasCoreModule() || hasFacadeModule() || hasSpiModule()) {
      return true;
    }
    // Check for any leaf children in moduleOrder
    return hasAnyLeafChildren();
  }

  /**
   * Gets the standalone module type description.
   *
   * @return description of the standalone module type, or null if not standalone
   */
  public String getStandaloneModuleType() {
    if (isStandaloneCoreModule()) {
      return "core";
    } else if (isStandaloneCommonModule()) {
      return "common";
    } else if (isStandaloneCommonsModule()) {
      return "commons";
    } else if (isStandaloneUtilModule()) {
      return "util";
    } else if (isStandaloneUtilsModule()) {
      return "utils";
    }
    return null;
  }
}
