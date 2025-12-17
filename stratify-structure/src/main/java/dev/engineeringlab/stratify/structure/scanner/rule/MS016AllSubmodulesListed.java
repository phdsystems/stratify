package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * MS-016: All Submodule Directories Must Be Listed.
 *
 * <p>All subdirectories containing pom.xml must be listed in aggregator's modules section.
 */
public class MS016AllSubmodulesListed extends AbstractStructureRule {

  public MS016AllSubmodulesListed() {
    super("MS-016");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    Path basePath = module.getBasePath();
    List<String> moduleOrder = module.getModuleOrder();
    Set<String> listedModules = moduleOrder != null ? new HashSet<>(moduleOrder) : new HashSet<>();

    List<String> unlistedModules = new ArrayList<>();

    try (Stream<Path> paths = Files.list(basePath)) {
      paths
          .filter(Files::isDirectory)
          .filter(dir -> Files.exists(dir.resolve("pom.xml")))
          .map(dir -> dir.getFileName().toString())
          .filter(name -> !listedModules.contains(name))
          .forEach(unlistedModules::add);
    } catch (IOException e) {
      // Skip if can't list directory
      return List.of();
    }

    if (unlistedModules.isEmpty()) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            "Aggregator module '"
                + module.getArtifactId()
                + "' has "
                + unlistedModules.size()
                + " unlisted submodules: "
                + String.join(", ", unlistedModules)));
  }
}
