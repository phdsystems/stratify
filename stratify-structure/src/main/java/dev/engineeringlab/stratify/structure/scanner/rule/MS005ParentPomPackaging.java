package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.util.List;

/**
 * MS-005: Parent POM must have packaging type 'pom'.
 *
 * <p>Parent/aggregator POMs must use packaging type 'pom', not 'jar'.
 */
public class MS005ParentPomPackaging extends AbstractStructureRule {

  public MS005ParentPomPackaging() {
    super("MS-005");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    return module.isParent();
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    String packaging = module.getPomModel() != null ? module.getPomModel().getPackaging() : null;

    if ("pom".equals(packaging)) {
      return List.of();
    }

    return List.of(
        createViolation(
            module,
            String.format(
                "Parent POM has packaging '%s', expected 'pom'. "
                    + "Parent/aggregator POMs must use packaging type 'pom'.",
                packaging != null ? packaging : "jar"),
            module.getBasePath().resolve("pom.xml").toString()));
  }
}
