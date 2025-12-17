package dev.engineeringlab.stratify.structure.scanner.facade;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo.SubModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.common.model.PluginInfo;
import dev.engineeringlab.stratify.structure.scanner.common.util.PomScanner;
import dev.engineeringlab.stratify.structure.scanner.core.StructureRuleProvider;
import dev.engineeringlab.stratify.structure.scanner.spi.Scanner;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.Model;
import org.apache.maven.project.MavenProject;

/**
 * Facade for scanning module structure and validating MS-* and MD-* rules.
 *
 * <p>This scanner analyzes Maven multi-module projects to:
 *
 * <ul>
 *   <li>Build {@link ModuleInfo} from MavenProject
 *   <li>Validate module structure rules (MS-001 to MS-023)
 *   <li>Validate modular design rules (MD-001)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * ModuleStructureScanner scanner = new ModuleStructureScanner(mavenProject);
 * ModuleInfo moduleInfo = scanner.scan();
 * List&lt;Violation&gt; violations = scanner.validate(moduleInfo);
 * </pre>
 */
public class ModuleStructureScanner {

  private static final String[] SUB_MODULE_SUFFIXES = {
    "-api", "-core", "-facade", "-spi", "-common", "-commons", "-util", "-utils"
  };

  private final MavenProject project;
  private final PomScanner pomScanner;
  private final StructureRuleProvider ruleProvider;

  /**
   * Creates a ModuleStructureScanner for the given Maven project.
   *
   * @param project the Maven project to scan
   */
  public ModuleStructureScanner(MavenProject project) {
    this.project = project;
    this.pomScanner = new PomScanner();
    this.ruleProvider = new StructureRuleProvider();
  }

  /**
   * Scans the Maven project and builds ModuleInfo.
   *
   * @return populated ModuleInfo for the project
   */
  public ModuleInfo scan() {
    Path basePath = project.getBasedir().toPath();
    Model pomModel = pomScanner.readPom(basePath.resolve("pom.xml"));

    String artifactId = project.getArtifactId();
    String groupId = project.getGroupId();
    String moduleName = extractModuleName(artifactId);
    boolean isParent = "pom".equals(project.getPackaging());

    ModuleInfo.ModuleInfoBuilder builder =
        ModuleInfo.builder()
            .artifactId(artifactId)
            .groupId(groupId)
            .moduleName(moduleName)
            .basePath(basePath)
            .isParent(isParent)
            .pomModel(pomModel);

    if (isParent) {
      // Scan submodules for parent/aggregator projects
      Map<String, SubModuleInfo> subModules = scanSubModules(basePath, artifactId);
      builder.subModules(subModules);

      // Get module order from POM
      List<String> moduleOrder = pomScanner.getModules(pomModel);
      builder.moduleOrder(moduleOrder);
    } else {
      // For leaf modules, set package base
      String packageBase = groupId.replace('.', '/') + "/" + moduleName.replace("-", "/");
      builder.packageBase(packageBase);
    }

    // Scan code quality plugins
    List<PluginInfo> plugins = pomScanner.scanCodeQualityPlugins(pomModel);
    builder.codeQualityPlugins(plugins);

    return builder.build();
  }

  /**
   * Validates the module structure against all MS-* and MD-* rules.
   *
   * @param moduleInfo the module information to validate
   * @return list of violations found
   */
  public List<Violation> validate(ModuleInfo moduleInfo) {
    List<Violation> violations = new ArrayList<>();

    for (Scanner<ModuleInfo> scanner : ruleProvider.getScanners()) {
      violations.addAll(scanner.scan(moduleInfo));
    }

    return violations;
  }

  /** Scans submodules of a parent project. */
  private Map<String, SubModuleInfo> scanSubModules(Path basePath, String parentArtifactId) {
    Map<String, SubModuleInfo> subModules = new HashMap<>();

    for (String suffix : SUB_MODULE_SUFFIXES) {
      String subModuleName = suffix.substring(1); // Remove leading dash
      String expectedArtifactId = extractBaseArtifactId(parentArtifactId) + suffix;
      Path subModulePath = findSubModulePath(basePath, expectedArtifactId, suffix);

      SubModuleInfo subModule =
          buildSubModuleInfo(subModuleName, subModulePath, expectedArtifactId);
      subModules.put(subModuleName, subModule);
    }

    // Also scan any additional submodules from the modules list
    Model pomModel = pomScanner.readPom(basePath.resolve("pom.xml"));
    List<String> modules = pomScanner.getModules(pomModel);

    for (String moduleName : modules) {
      Path modulePath = basePath.resolve(moduleName);
      if (Files.exists(modulePath.resolve("pom.xml"))) {
        Model subPom = pomScanner.readPom(modulePath.resolve("pom.xml"));
        if (subPom != null) {
          String artifactId = subPom.getArtifactId();
          String type = determineSubModuleType(artifactId);
          if (type != null && !subModules.containsKey(type)) {
            SubModuleInfo subModule = buildSubModuleInfo(type, modulePath, artifactId);
            subModules.put(type, subModule);
          }
        }
      }
    }

    return subModules;
  }

  /** Finds the path to a submodule, checking common naming patterns. */
  private Path findSubModulePath(Path basePath, String expectedArtifactId, String suffix) {
    // Try exact match first (e.g., "agent-api")
    Path exactPath = basePath.resolve(expectedArtifactId);
    if (Files.exists(exactPath.resolve("pom.xml"))) {
      return exactPath;
    }

    // Try short suffix (e.g., "api")
    String shortName = suffix.substring(1);
    Path shortPath = basePath.resolve(shortName);
    if (Files.exists(shortPath.resolve("pom.xml"))) {
      return shortPath;
    }

    return exactPath; // Return expected path even if doesn't exist
  }

  /** Builds SubModuleInfo for a submodule. */
  private SubModuleInfo buildSubModuleInfo(String name, Path path, String expectedArtifactId) {
    boolean exists = Files.exists(path.resolve("pom.xml"));

    SubModuleInfo.SubModuleInfoBuilder builder =
        SubModuleInfo.builder().name(name).path(path).exists(exists);

    if (exists) {
      Model subPom = pomScanner.readPom(path.resolve("pom.xml"));
      if (subPom != null) {
        builder
            .pomModel(subPom)
            .artifactId(subPom.getArtifactId())
            .groupId(pomScanner.getGroupId(subPom))
            .dependencies(pomScanner.getDependencyArtifactIds(subPom))
            .codeQualityPlugins(pomScanner.scanCodeQualityPlugins(subPom));
      }

      // Scan Java source files
      Path srcMainJava = path.resolve("src/main/java");
      if (Files.exists(srcMainJava)) {
        try (Stream<Path> walk = Files.walk(srcMainJava)) {
          List<Path> javaFiles =
              walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
          builder.javaSourceFiles(javaFiles);
        } catch (IOException e) {
          // Ignore IO errors
        }
      }

      // Scan test source files
      Path srcTestJava = path.resolve("src/test/java");
      if (Files.exists(srcTestJava)) {
        try (Stream<Path> walk = Files.walk(srcTestJava)) {
          List<Path> testFiles =
              walk.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
          builder.testSourceFiles(testFiles);
        } catch (IOException e) {
          // Ignore IO errors
        }
      }

      // Find exception package
      Path exceptionPath = findExceptionPackage(srcMainJava);
      if (exceptionPath != null) {
        builder.exceptionPackagePath(exceptionPath);
      }
    } else {
      builder.artifactId(expectedArtifactId);
    }

    return builder.build();
  }

  /** Determines submodule type from artifact ID suffix. */
  private String determineSubModuleType(String artifactId) {
    if (artifactId == null) {
      return null;
    }
    for (String suffix : SUB_MODULE_SUFFIXES) {
      if (artifactId.endsWith(suffix)) {
        return suffix.substring(1);
      }
    }
    return null;
  }

  /** Extracts base artifact ID by removing -parent suffix. */
  private String extractBaseArtifactId(String artifactId) {
    if (artifactId.endsWith("-parent")) {
      return artifactId.substring(0, artifactId.length() - "-parent".length());
    }
    return artifactId;
  }

  /** Extracts module name from artifact ID. */
  private String extractModuleName(String artifactId) {
    String name = artifactId;
    // Remove common prefixes
    if (name.startsWith("engineeringlab-")) {
      name = name.substring("engineeringlab-".length());
    }
    // Remove common suffixes
    for (String suffix : new String[] {"-parent", "-api", "-core", "-facade", "-spi"}) {
      if (name.endsWith(suffix)) {
        name = name.substring(0, name.length() - suffix.length());
        break;
      }
    }
    return name;
  }

  /** Finds the exception package in src/main/java. */
  private Path findExceptionPackage(Path srcMainJava) {
    if (!Files.exists(srcMainJava)) {
      return null;
    }
    try (Stream<Path> walk = Files.walk(srcMainJava)) {
      return walk.filter(Files::isDirectory)
          .filter(p -> p.getFileName().toString().equals("exception"))
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }
}
