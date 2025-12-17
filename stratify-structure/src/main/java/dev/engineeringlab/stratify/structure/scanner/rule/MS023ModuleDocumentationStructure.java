package dev.engineeringlab.stratify.structure.scanner.rule;

import dev.engineeringlab.stratify.structure.scanner.common.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.core.AbstractStructureRule;
import dev.engineeringlab.stratify.structure.scanner.spi.Violation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * MS-023: Module Documentation Structure.
 *
 * <p>Validates that modules have proper documentation structure:
 *
 * <ul>
 *   <li>README.md: Lean overview with links to detailed docs
 *   <li>doc/3-design/: architecture.md, workflow.md, sequence.md (with mermaid)
 *   <li>doc/4-development/: developer-guide.md
 *   <li>doc/6-operations/: manual.md
 * </ul>
 */
public class MS023ModuleDocumentationStructure extends AbstractStructureRule {

  private static final String README = "README.md";
  private static final String DOC_DESIGN = "doc/3-design";
  private static final String DOC_DEV = "doc/4-development";
  private static final String DOC_OPS = "doc/6-operations";

  private static final List<String> DESIGN_DOCS =
      List.of("architecture.md", "workflow.md", "sequence.md");

  private static final List<String> DEV_DOCS = List.of("developer-guide.md");

  private static final List<String> OPS_DOCS = List.of("manual.md");

  private static final List<String> README_REFS =
      List.of("./doc/3-design/", "./doc/4-development/", "./doc/6-operations/");

  public MS023ModuleDocumentationStructure() {
    super("MS-023");
  }

  @Override
  protected boolean appliesTo(ModuleInfo module) {
    // Only applies to engine modules and parent modules
    if (!module.isParent()) {
      return false;
    }
    String artifactId = module.getArtifactId();
    return artifactId.endsWith("-engine") || artifactId.endsWith("-parent");
  }

  @Override
  protected List<Violation> doValidate(ModuleInfo module) {
    List<Violation> violations = new ArrayList<>();
    Path modulePath = module.getBasePath();

    // Check doc directories first (higher priority than README references)
    violations.addAll(validateDocDirectory(module, modulePath, DOC_DESIGN, DESIGN_DOCS, "design"));
    violations.addAll(validateDocDirectory(module, modulePath, DOC_DEV, DEV_DOCS, "development"));
    violations.addAll(validateDocDirectory(module, modulePath, DOC_OPS, OPS_DOCS, "operations"));

    // Check mermaid diagrams in design docs
    violations.addAll(validateMermaidDiagrams(module, modulePath));

    // Check README.md exists and has proper references (lower priority)
    Path readmePath = modulePath.resolve(README);
    if (!Files.exists(readmePath)) {
      violations.add(
          createViolation(
              module,
              "Missing README.md file. Module must have a lean README with quick start and documentation links.",
              modulePath.toString()));
    } else {
      // Check README references documentation
      violations.addAll(validateReadmeReferences(module, readmePath));
    }

    // Don't limit violations for documentation checks - all issues should be reported
    return violations;
  }

  private List<Violation> validateReadmeReferences(ModuleInfo module, Path readmePath) {
    List<Violation> violations = new ArrayList<>();

    try {
      String content = Files.readString(readmePath);

      for (String ref : README_REFS) {
        if (!content.contains(ref) && !content.contains(ref.replace("./", ""))) {
          violations.add(
              createViolation(
                  module,
                  String.format("README.md must reference '%s' for detailed documentation", ref),
                  readmePath.toString()));
        }
      }

      // Check README is lean (not too long)
      long lineCount = content.lines().count();
      if (lineCount > 150) {
        violations.add(
            createViolation(
                module,
                String.format(
                    "README.md is too long (%d lines). Keep it lean (<150 lines) "
                        + "and move detailed content to doc/ subdirectories.",
                    lineCount),
                readmePath.toString()));
      }

    } catch (IOException e) {
      violations.add(
          createViolation(
              module, "Failed to read README.md: " + e.getMessage(), readmePath.toString()));
    }

    return violations;
  }

  private List<Violation> validateDocDirectory(
      ModuleInfo module,
      Path modulePath,
      String docDir,
      List<String> requiredDocs,
      String docType) {

    List<Violation> violations = new ArrayList<>();
    Path docPath = modulePath.resolve(docDir);

    if (!Files.exists(docPath)) {
      violations.add(
          createViolation(
              module,
              String.format(
                  "Missing %s directory. Create '%s' with %s documentation.",
                  docType, docDir, docType),
              modulePath.toString()));
      return violations;
    }

    for (String doc : requiredDocs) {
      Path docFile = docPath.resolve(doc);
      if (!Files.exists(docFile)) {
        violations.add(
            createViolation(
                module,
                String.format("Missing %s documentation: %s/%s", docType, docDir, doc),
                docPath.toString()));
      }
    }

    return violations;
  }

  private List<Violation> validateMermaidDiagrams(ModuleInfo module, Path modulePath) {
    List<Violation> violations = new ArrayList<>();
    Path designPath = modulePath.resolve(DOC_DESIGN);

    if (!Files.exists(designPath)) {
      return violations; // Already reported above
    }

    // Check workflow.md and sequence.md have mermaid diagrams
    for (String diagramDoc : List.of("workflow.md", "sequence.md")) {
      Path docFile = designPath.resolve(diagramDoc);
      if (Files.exists(docFile)) {
        try {
          String content = Files.readString(docFile);
          if (!content.contains("```mermaid")) {
            violations.add(
                createViolation(
                    module,
                    String.format(
                        "%s/%s should contain mermaid diagrams for visualization",
                        DOC_DESIGN, diagramDoc),
                    docFile.toString()));
          }
        } catch (IOException e) {
          // Skip if can't read
        }
      }
    }

    return violations;
  }
}
