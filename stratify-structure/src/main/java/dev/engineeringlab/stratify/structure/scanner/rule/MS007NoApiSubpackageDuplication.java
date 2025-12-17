package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * MS-007: No api subpackage duplication.
 *
 * <p>Avoid nested api packages (e.g., .../api/api/) which create confusing package structure.
 */
public class MS007NoApiSubpackageDuplication extends AbstractStructureRule {

  private static final int MAX_DEPTH = 10;

  public MS007NoApiSubpackageDuplication() {
    super("MS-007");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<String> duplications = new ArrayList<>();

    for (ModuleInfo.SubModuleInfo subModule : module.getSubModules().values()) {
      if (subModule.isExists() && subModule.getPath() != null) {
        try {
          Path srcMainJava = subModule.getPath().resolve("src/main/java");
          if (Files.exists(srcMainJava)) {
            try (Stream<Path> paths = Files.walk(srcMainJava, MAX_DEPTH)) {
              paths
                  .filter(Files::isDirectory)
                  .filter(
                      p -> {
                        String pathStr = p.toString();
                        return pathStr.matches(".*[\\\\/]api[\\\\/]api[\\\\/].*");
                      })
                  .forEach(p -> duplications.add(subModule.getName() + ": " + p.toString()));
            }
          }
        } catch (IOException e) {
          // Skip if can't read
        }
      }
    }

    if (duplications.isEmpty()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            String.format(
                "Found nested api packages (.../api/api/): %s. "
                    + "Nested api directories create confusing package structure.",
                String.join(", ", duplications)),
            module.getBasePath().toString()));
  }
}
