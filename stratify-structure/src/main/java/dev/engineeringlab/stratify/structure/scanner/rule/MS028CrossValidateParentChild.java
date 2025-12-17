package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * MS-028: Cross-validate Parent-Child Types.
 *
 * <p>This rule ensures parent-child relationships are valid by verifying actual module types match
 * expectations. No assumptions - verify each relationship directly.
 *
 * <p>Validates:
 *
 * <ul>
 *   <li>If a module declares a parent, that parent must actually exist on disk
 *   <li>If a module declares children/submodules, those must actually exist on disk
 *   <li>The declared type in config (leaf/aggregator/parent) must match actual module structure
 *   <li>Leaf modules (-api, -core, -facade, -spi) must have a parent that is type "parent" (not
 *       pure aggregator)
 * </ul>
 *
 * <p>This is the "no blind spots" rule - verify everything, assume nothing.
 */
public class MS028CrossValidateParentChild extends AbstractStructureRule {

  private static final Set<String> LEAF_SUFFIXES =
      Set.of("-api", "-core", "-facade", "-spi", "-common");
  private static final Set<String> LAYER_SUFFIXES =
      Set.of("-api", "-spi", "-core", "-facade", "-common");

  private static final Pattern PARENT_ARTIFACT_ID_PATTERN =
      Pattern.compile("<parent>.*?<artifactId>([^<]+)</artifactId>.*?</parent>", Pattern.DOTALL);

  private static final Pattern RELATIVE_PATH_PATTERN =
      Pattern.compile(
          "<parent>.*?<relativePath>([^<]+)</relativePath>.*?</parent>", Pattern.DOTALL);

  private static final Pattern MODULE_PATTERN = Pattern.compile("<module>([^<]+)</module>");
  private static final Pattern ARTIFACT_ID_PATTERN =
      Pattern.compile("<artifactId>([^<]+)</artifactId>");

  public MS028CrossValidateParentChild() {
    super("MS-028");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to all modules with pom.xml
    return module.getBasePath() != null && Files.exists(module.getBasePath().resolve("pom.xml"));
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();
    Path pomFile = modulePath.resolve("pom.xml");

    try {
      String pomContent = Files.readString(pomFile);

      // Validation 1: If module declares a parent, that parent must exist on disk
      violations.addAll(validateParentExists(module, pomContent, modulePath));

      // Validation 2: If module declares children/submodules, those must exist on disk
      violations.addAll(validateChildrenExist(module, pomContent, modulePath));

      // Validation 3: Verify declared type matches actual structure
      violations.addAll(validateDeclaredTypeMatchesActual(module, modulePath));

      // Validation 4: Leaf modules must have a parent that is type "parent"
      violations.addAll(validateLeafParentType(module, pomContent, modulePath));

    } catch (IOException e) {
      violations.add(
          createViolation(module, "Failed to read pom.xml: " + e.getMessage(), pomFile.toString()));
    }

    return violations.size() > MAX_VIOLATIONS ? violations.subList(0, MAX_VIOLATIONS) : violations;
  }

  /** Validates that if a module declares a parent, that parent actually exists on disk. */
  private List<Violation> validateParentExists(
      ModuleInfo module, String pomContent, Path modulePath) {
    List<Violation> violations = new ArrayList<>();

    Matcher parentMatcher = PARENT_ARTIFACT_ID_PATTERN.matcher(pomContent);
    if (!parentMatcher.find()) {
      return violations; // No parent declared, nothing to validate
    }

    String parentArtifactId = parentMatcher.group(1).trim();

    // Find parent path using relativePath or default
    Path parentPath = findParentPath(pomContent, modulePath);

    if (parentPath == null || !Files.exists(parentPath)) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Module declares parent '%s' but parent directory does not exist. "
                      + "Expected parent at: %s",
                  parentArtifactId, parentPath),
              modulePath.toString()));
      return violations;
    }

    Path parentPomFile = parentPath.resolve("pom.xml");
    if (!Files.exists(parentPomFile)) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Module declares parent '%s' but parent pom.xml does not exist at: %s",
                  parentArtifactId, parentPomFile),
              modulePath.toString()));
    }

    return violations;
  }

  /** Validates that if a module declares children/submodules, those actually exist on disk. */
  private List<Violation> validateChildrenExist(
      ModuleInfo module, String pomContent, Path modulePath) {
    List<Violation> violations = new ArrayList<>();

    Matcher moduleMatcher = MODULE_PATTERN.matcher(pomContent);
    List<String> declaredModules = new ArrayList<>();

    while (moduleMatcher.find()) {
      declaredModules.add(moduleMatcher.group(1).trim());
    }

    for (String childModule : declaredModules) {
      Path childPath = modulePath.resolve(childModule);

      if (!Files.exists(childPath)) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Module declares child '%s' in <modules> but child directory does not exist",
                    childModule),
                modulePath.toString()));
        continue;
      }

      Path childPomFile = childPath.resolve("pom.xml");
      if (!Files.exists(childPomFile)) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Module declares child '%s' but child pom.xml does not exist at: %s",
                    childModule, childPomFile),
                modulePath.toString()));
      }
    }

    return violations;
  }

  /** Validates that declared type in config matches actual module structure. */
  private List<Violation> validateDeclaredTypeMatchesActual(ModuleInfo module, Path modulePath) {
    List<Violation> violations = new ArrayList<>();

    // Check if module has config files declaring its type
    Path aggregatorConfig = modulePath.resolve("config/module.aggregator.yml");
    Path parentConfig = modulePath.resolve("config/module.parent.yml");

    String actualType = detectActualModuleType(modulePath);

    if (Files.exists(aggregatorConfig)) {
      // Module has aggregator config, verify it's actually a pure aggregator
      if (!"aggregator".equals(actualType)) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Module has config/module.aggregator.yml but actual structure indicates type '%s'. "
                        + "Pure aggregators must not have layer submodules (-api, -core, -facade, -spi).",
                    actualType),
                aggregatorConfig.toString()));
      }
    }

    if (Files.exists(parentConfig)) {
      // Module has parent config, verify it's actually a parent aggregator
      if (!"parent".equals(actualType)) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Module has config/module.parent.yml but actual structure indicates type '%s'. "
                        + "Parent aggregators must have layer submodules (-api, -core, -facade, -spi).",
                    actualType),
                parentConfig.toString()));
      }
    }

    return violations;
  }

  /** Validates that leaf modules have a parent that is type "parent" (not pure aggregator). */
  private List<Violation> validateLeafParentType(
      ModuleInfo module, String pomContent, Path modulePath) {
    List<Violation> violations = new ArrayList<>();

    String artifactId = module.getArtifactId();
    boolean isLeaf = LEAF_SUFFIXES.stream().anyMatch(artifactId::endsWith);

    if (!isLeaf) {
      return violations; // Not a leaf module, skip validation
    }

    // Extract parent artifactId
    Matcher parentMatcher = PARENT_ARTIFACT_ID_PATTERN.matcher(pomContent);
    if (!parentMatcher.find()) {
      return violations; // MS-011 will catch this (leaf must have parent)
    }

    String parentArtifactId = parentMatcher.group(1).trim();

    // Find parent path
    Path parentPath = findParentPath(pomContent, modulePath);
    if (parentPath == null || !Files.exists(parentPath)) {
      return violations; // Already caught by validateParentExists
    }

    // Check parent's actual type
    String parentActualType = detectActualModuleType(parentPath);

    if ("aggregator".equals(parentActualType)) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Leaf module '%s' has parent '%s' which is a pure aggregator. "
                      + "Leaf modules (-api, -core, -facade, -spi) must have a parent aggregator (-parent suffix), "
                      + "not a pure aggregator (-aggregator suffix).",
                  artifactId, parentArtifactId),
              modulePath.toString()));
    } else if ("parent".equals(parentActualType)) {
      // Parent is a parent type - verify it has the correct suffix
      if (!parentArtifactId.endsWith("-parent")) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Leaf module '%s' has parent '%s' which does not end with -parent suffix. "
                        + "Leaf modules must have a parent aggregator ending with -parent.",
                    artifactId, parentArtifactId),
                modulePath.toString()));
      }
    } else {
      // Parent is neither aggregator nor parent type (shouldn't happen for pom packaging)
      if (!parentArtifactId.endsWith("-parent")) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "Leaf module '%s' has parent '%s' which does not end with -parent suffix. "
                        + "Leaf modules must have a parent aggregator ending with -parent.",
                    artifactId, parentArtifactId),
                modulePath.toString()));
      }
    }

    return violations;
  }

  /**
   * Detects the actual type of a module based on its structure and naming.
   *
   * @return "aggregator", "parent", or "leaf"
   */
  private String detectActualModuleType(Path modulePath) {
    Path pomFile = modulePath.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return "leaf";
    }

    // Check if module has pom packaging
    boolean isPomPackaging = hasPomPackaging(pomFile);

    if (isPomPackaging) {
      // Module has pom packaging - determine type by suffix and structure
      // Get artifactId from pom.xml
      String artifactId = getArtifactId(pomFile);

      if (artifactId != null) {
        // Check suffix first (explicit declaration of intent)
        if (artifactId.endsWith("-parent")) {
          return "parent";
        }
        if (artifactId.endsWith("-aggregator")) {
          return "aggregator";
        }
      }

      // No explicit suffix - determine by structure
      if (hasLayerSubmodules(modulePath)) {
        return "parent";
      } else {
        return "aggregator";
      }
    }

    // Not pom packaging - it's a leaf module
    return "leaf";
  }

  /**
   * Checks if a module has layer submodules (-api, -core, -facade, -spi, -common). Checks both for
   * directories with pom.xml AND declarations in <modules> section.
   */
  private boolean hasLayerSubmodules(Path modulePath) {
    // First check for directories with layer suffixes
    try (Stream<Path> stream = Files.list(modulePath)) {
      boolean hasLayerDirs =
          stream
              .filter(Files::isDirectory)
              .filter(dir -> Files.exists(dir.resolve("pom.xml")))
              .anyMatch(
                  dir -> {
                    String name = dir.getFileName().toString();
                    return LAYER_SUFFIXES.stream().anyMatch(name::endsWith);
                  });
      if (hasLayerDirs) {
        return true;
      }
    } catch (IOException e) {
      // Continue to check modules section
    }

    // Also check modules declared in pom.xml
    Path pomFile = modulePath.resolve("pom.xml");
    if (!Files.exists(pomFile)) {
      return false;
    }

    try {
      String pomContent = Files.readString(pomFile);
      Matcher moduleMatcher = MODULE_PATTERN.matcher(pomContent);

      while (moduleMatcher.find()) {
        String moduleName = moduleMatcher.group(1).trim();
        // Check if this declared module name ends with a layer suffix
        if (LAYER_SUFFIXES.stream().anyMatch(moduleName::endsWith)) {
          return true;
        }
      }
      return false;
    } catch (IOException e) {
      return false;
    }
  }

  /** Checks if a module has any submodules. */
  private boolean hasSubmodules(Path modulePath) {
    try (Stream<Path> stream = Files.list(modulePath)) {
      return stream
          .filter(Files::isDirectory)
          .anyMatch(dir -> Files.exists(dir.resolve("pom.xml")));
    } catch (IOException e) {
      return false;
    }
  }

  /** Checks if a module has source code. */
  private boolean hasSourceCode(Path modulePath) {
    return Files.exists(modulePath.resolve("src/main/java"))
        || Files.exists(modulePath.resolve("src/test/java"));
  }

  /** Checks if a pom.xml file has packaging type "pom". */
  private boolean hasPomPackaging(Path pomFile) {
    try {
      String pomContent = Files.readString(pomFile);
      return pomContent.contains("<packaging>pom</packaging>");
    } catch (IOException e) {
      return false;
    }
  }

  /** Extracts the artifactId from a pom.xml file. */
  private String getArtifactId(Path pomFile) {
    try {
      String pomContent = Files.readString(pomFile);
      Matcher matcher = ARTIFACT_ID_PATTERN.matcher(pomContent);
      if (matcher.find()) {
        return matcher.group(1).trim();
      }
    } catch (IOException e) {
      // Ignore
    }
    return null;
  }

  /** Finds the parent path using relativePath or default parent directory. */
  private Path findParentPath(String pomContent, Path modulePath) {
    Matcher relativePathMatcher = RELATIVE_PATH_PATTERN.matcher(pomContent);

    if (relativePathMatcher.find()) {
      String relativePath = relativePathMatcher.group(1).trim();
      Path parentPath = modulePath.resolve(relativePath).normalize();

      if (Files.isDirectory(parentPath)) {
        return parentPath;
      }
      // relativePath might point to pom.xml directly
      if (Files.isRegularFile(parentPath)
          && parentPath.getFileName().toString().equals("pom.xml")) {
        return parentPath.getParent();
      }
      return parentPath;
    }

    // Default: look in parent directory
    return modulePath.getParent();
  }
}
