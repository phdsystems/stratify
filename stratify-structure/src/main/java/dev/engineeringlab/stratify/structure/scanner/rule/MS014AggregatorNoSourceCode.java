package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * MS-014: Aggregator Modules Should Not Contain Source Code.
 *
 * <p>Aggregator modules should only contain pom.xml and submodule directories, not source code
 * (src/main/java, src/test/java).
 */
public class MS014AggregatorNoSourceCode extends AbstractStructureRule {

  public MS014AggregatorNoSourceCode() {
    super("MS-014");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Applies to aggregator modules (with pom packaging and modules section)
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Path basePath = module.getBasePath();
    Path srcMainJava = basePath.resolve("src/main/java");
    Path srcTestJava = basePath.resolve("src/test/java");

    boolean hasSrcMain = Files.exists(srcMainJava) && Files.isDirectory(srcMainJava);
    boolean hasSrcTest = Files.exists(srcTestJava) && Files.isDirectory(srcTestJava);

    if (!hasSrcMain && !hasSrcTest) {
      return List.of();
    }

    StringBuilder found = new StringBuilder();
    if (hasSrcMain) {
      found.append("src/main/java");
    }
    if (hasSrcTest) {
      if (found.length() > 0) found.append(", ");
      found.append("src/test/java");
    }

    return List.of(
        createViolation(
            module,
            "Aggregator module '"
                + module.getArtifactId()
                + "' contains source code directories: "
                + found
                + ". All implementation belongs in submodules."));
  }
}
