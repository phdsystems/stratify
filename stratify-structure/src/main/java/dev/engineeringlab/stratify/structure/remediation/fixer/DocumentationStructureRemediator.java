package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Remediator for MS-023: Module Documentation Structure.
 *
 * <p>Creates missing documentation files with proper structure:
 *
 * <ul>
 *   <li>README.md: Lean overview with links to detailed docs
 *   <li>doc/3-design/architecture.md: Architecture overview with mermaid
 *   <li>doc/3-design/workflow.md: Workflow diagrams with mermaid
 *   <li>doc/3-design/sequence.md: Sequence diagrams with mermaid
 *   <li>doc/4-development/developer-guide.md: Developer documentation
 *   <li>doc/6-operations/manual.md: Operations manual
 * </ul>
 */
public class DocumentationStructureRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"MS-023"};
  private static final int PRIORITY = 50; // Lower priority - run after structure fixes

  public DocumentationStructureRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "DocumentationStructureRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates missing documentation structure (MS-023)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!"MS-023".equals(violation.ruleId())) {
      return FixResult.skipped(violation, "Not an MS-023 violation");
    }

    try {
      // Derive moduleRoot from violation location, not context
      Path moduleRoot = deriveModuleRoot(violation, context);
      String moduleName = extractModuleName(moduleRoot);

      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Determine what specific documentation is missing based on violation message
      String message = violation.message() != null ? violation.message().toLowerCase() : "";
      DocType docType = determineDocType(message);

      switch (docType) {
        case README -> {
          modifiedFiles.addAll(createReadme(moduleRoot, moduleName, context, diffs));
        }
        case DESIGN -> {
          createDocDirectory(moduleRoot, "doc/3-design", context, diffs);
          modifiedFiles.addAll(createDesignDocs(moduleRoot, moduleName, context, diffs));
        }
        case DEVELOPMENT -> {
          createDocDirectory(moduleRoot, "doc/4-development", context, diffs);
          modifiedFiles.addAll(createDevDocs(moduleRoot, moduleName, context, diffs));
        }
        case OPERATIONS -> {
          createDocDirectory(moduleRoot, "doc/6-operations", context, diffs);
          modifiedFiles.addAll(createOpsDocs(moduleRoot, moduleName, context, diffs));
        }
        case ALL -> {
          // Fallback: create all documentation if message doesn't specify
          createDocDirectories(moduleRoot, context, diffs);
          modifiedFiles.addAll(createReadme(moduleRoot, moduleName, context, diffs));
          modifiedFiles.addAll(createDesignDocs(moduleRoot, moduleName, context, diffs));
          modifiedFiles.addAll(createDevDocs(moduleRoot, moduleName, context, diffs));
          modifiedFiles.addAll(createOpsDocs(moduleRoot, moduleName, context, diffs));
        }
      }

      if (diffs.isEmpty()) {
        return FixResult.skipped(violation, getSkipReason(docType));
      }

      if (context.dryRun()) {
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would create " + docType.description)
            .diffs(diffs)
            .build();
      }

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Created " + docType.description + " for " + moduleName)
          .modifiedFiles(modifiedFiles)
          .diffs(diffs)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to create documentation: " + e.getMessage());
    }
  }

  /** Determines what type of documentation is missing based on the violation message. */
  private DocType determineDocType(String message) {
    if (message.contains("readme")) {
      return DocType.README;
    } else if (message.contains("design") || message.contains("3-design")) {
      return DocType.DESIGN;
    } else if (message.contains("development") || message.contains("4-development")) {
      return DocType.DEVELOPMENT;
    } else if (message.contains("operations") || message.contains("6-operations")) {
      return DocType.OPERATIONS;
    }
    return DocType.ALL;
  }

  private String getSkipReason(DocType docType) {
    return switch (docType) {
      case README -> "README.md already exists with proper references";
      case DESIGN -> "Design documentation already exists";
      case DEVELOPMENT -> "Development documentation already exists";
      case OPERATIONS -> "Operations documentation already exists";
      case ALL -> "All documentation already exists";
    };
  }

  /** Documentation type enum for targeted fixes. */
  private enum DocType {
    README("README.md"),
    DESIGN("design documentation"),
    DEVELOPMENT("development documentation"),
    OPERATIONS("operations documentation"),
    ALL("documentation structure");

    final String description;

    DocType(String description) {
      this.description = description;
    }
  }

  // deriveModuleRoot(), extractModuleName() inherited from AbstractStructureFixer

  /** Creates a single documentation directory if it doesn't exist. */
  private void createDocDirectory(
      Path moduleRoot, String dir, FixerContext context, List<String> diffs) throws IOException {
    Path dirPath = moduleRoot.resolve(dir);
    if (!Files.exists(dirPath)) {
      if (!context.dryRun()) {
        Files.createDirectories(dirPath);
      }
      diffs.add("+ Created directory: " + dir);
      context.log("Created directory: %s", dirPath);
    }
  }

  private void createDocDirectories(Path moduleRoot, FixerContext context, List<String> diffs)
      throws IOException {

    String[] dirs = {"doc/3-design", "doc/4-development", "doc/6-operations"};

    for (String dir : dirs) {
      createDocDirectory(moduleRoot, dir, context, diffs);
    }
  }

  private List<Path> createReadme(
      Path moduleRoot, String moduleName, FixerContext context, List<String> diffs)
      throws IOException {

    List<Path> created = new ArrayList<>();
    Path readmePath = moduleRoot.resolve("README.md");

    if (Files.exists(readmePath)) {
      // Check if it needs doc references
      String content = Files.readString(readmePath);
      if (!content.contains("./doc/3-design/") || !content.contains("./doc/4-development/")) {
        String updatedContent = addDocReferences(content, moduleName);
        if (!context.dryRun()) {
          backup(readmePath, context.projectRoot());
          writeFile(readmePath, updatedContent);
        }
        diffs.add("~ Updated README.md with documentation links");
        created.add(readmePath);
      }
    } else {
      String content = generateReadme(moduleName);
      if (!context.dryRun()) {
        writeFile(readmePath, content);
      }
      diffs.add("+ Created README.md");
      created.add(readmePath);
      context.log("Created: %s", readmePath);
    }

    return created;
  }

  private String addDocReferences(String content, String moduleName) {
    String docSection =
        """

            ## Documentation

            - [Architecture](./doc/3-design/architecture.md) - System design and components
            - [Workflow](./doc/3-design/workflow.md) - Process flow diagrams
            - [Sequences](./doc/3-design/sequence.md) - Interaction sequences
            - [Developer Guide](./doc/4-development/developer-guide.md) - Development setup and guidelines
            - [Operations Manual](./doc/6-operations/manual.md) - Deployment and operations
            """;

    // Add before ## License or at end
    if (content.contains("## License")) {
      return content.replace("## License", docSection + "\n## License");
    }
    return content + docSection;
  }

  private String generateReadme(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s

            Brief description of the %s module.

            ## Quick Start

            ```xml
            <dependency>
                <groupId>dev.engineeringlab</groupId>
                <artifactId>%s</artifactId>
                <version>${project.version}</version>
            </dependency>
            ```

            ## Usage

            ```java
            // Example usage code here
            ```

            ## Documentation

            - [Architecture](./doc/3-design/architecture.md) - System design and components
            - [Workflow](./doc/3-design/workflow.md) - Process flow diagrams
            - [Sequences](./doc/3-design/sequence.md) - Interaction sequences
            - [Developer Guide](./doc/4-development/developer-guide.md) - Development setup and guidelines
            - [Operations Manual](./doc/6-operations/manual.md) - Deployment and operations

            ## License

            Copyright (c) %d Engineering Lab. All rights reserved.
            """
        .formatted(titleCase, moduleName, moduleName, LocalDate.now().getYear());
  }

  private List<Path> createDesignDocs(
      Path moduleRoot, String moduleName, FixerContext context, List<String> diffs)
      throws IOException {

    List<Path> created = new ArrayList<>();
    Path designDir = moduleRoot.resolve("doc/3-design");

    // Architecture
    Path archPath = designDir.resolve("architecture.md");
    if (!Files.exists(archPath)) {
      if (!context.dryRun()) {
        writeFile(archPath, generateArchitectureDoc(moduleName));
      }
      diffs.add("+ Created doc/3-design/architecture.md");
      created.add(archPath);
    }

    // Workflow
    Path workflowPath = designDir.resolve("workflow.md");
    if (!Files.exists(workflowPath)) {
      if (!context.dryRun()) {
        writeFile(workflowPath, generateWorkflowDoc(moduleName));
      }
      diffs.add("+ Created doc/3-design/workflow.md");
      created.add(workflowPath);
    }

    // Sequence
    Path sequencePath = designDir.resolve("sequence.md");
    if (!Files.exists(sequencePath)) {
      if (!context.dryRun()) {
        writeFile(sequencePath, generateSequenceDoc(moduleName));
      }
      diffs.add("+ Created doc/3-design/sequence.md");
      created.add(sequencePath);
    }

    return created;
  }

  private String generateArchitectureDoc(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s Architecture

            ## Overview

            High-level architecture description for the %s module.

            ## Components

            ```mermaid
            graph TB
                subgraph "%s"
                    API[API Layer]
                    SPI[SPI Layer]
                    Core[Core Layer]
                    Facade[Facade Layer]
                end

                Client --> Facade
                Facade --> Core
                Core --> API
                Core --> SPI
                SPI --> Provider[External Provider]
            ```

            ## Layer Responsibilities

            | Layer | Responsibility |
            |-------|----------------|
            | API | Public interfaces and contracts |
            | SPI | Service Provider Interface for extensibility |
            | Core | Implementation logic |
            | Facade | Simplified entry point for clients |

            ## Dependencies

            - List key dependencies here

            ## Configuration

            - Document configuration options
            """
        .formatted(titleCase, moduleName, titleCase);
  }

  private String generateWorkflowDoc(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s Workflow

            ## Main Process Flow

            ```mermaid
            flowchart TD
                A[Start] --> B{Input Valid?}
                B -->|Yes| C[Process Input]
                B -->|No| D[Return Error]
                C --> E[Apply Business Logic]
                E --> F[Generate Output]
                F --> G[End]
                D --> G
            ```

            ## Process Steps

            1. **Input Validation**: Validate incoming requests
            2. **Processing**: Apply business logic
            3. **Output**: Generate and return results

            ## Error Handling

            ```mermaid
            flowchart TD
                A[Operation] --> B{Success?}
                B -->|Yes| C[Return Result]
                B -->|No| D[Log Error]
                D --> E[Return Error Response]
            ```

            ## State Transitions

            Document any state machine logic here.
            """
        .formatted(titleCase);
  }

  private String generateSequenceDoc(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s Sequence Diagrams

            ## Main Interaction

            ```mermaid
            sequenceDiagram
                participant Client
                participant Facade
                participant Core
                participant Provider

                Client->>Facade: request()
                Facade->>Core: process()
                Core->>Provider: execute()
                Provider-->>Core: result
                Core-->>Facade: response
                Facade-->>Client: response
            ```

            ## Error Scenario

            ```mermaid
            sequenceDiagram
                participant Client
                participant Facade
                participant Core

                Client->>Facade: request()
                Facade->>Core: process()
                Core-->>Facade: error
                Facade-->>Client: error response
            ```

            ## Async Operations

            Document async interactions if applicable.
            """
        .formatted(titleCase);
  }

  private List<Path> createDevDocs(
      Path moduleRoot, String moduleName, FixerContext context, List<String> diffs)
      throws IOException {

    List<Path> created = new ArrayList<>();
    Path devDir = moduleRoot.resolve("doc/4-development");
    Path guidePath = devDir.resolve("developer-guide.md");

    if (!Files.exists(guidePath)) {
      if (!context.dryRun()) {
        writeFile(guidePath, generateDeveloperGuide(moduleName));
      }
      diffs.add("+ Created doc/4-development/developer-guide.md");
      created.add(guidePath);
    }

    return created;
  }

  private String generateDeveloperGuide(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s Developer Guide

            ## Prerequisites

            - JDK 21+
            - Maven 3.9+

            ## Setup

            ```bash
            # Clone repository
            git clone <repo-url>
            cd %s

            # Build
            mvn clean install

            # Run tests
            mvn test
            ```

            ## Project Structure

            ```
            %s/
            ├── %s-api/           # Public interfaces
            ├── %s-spi/           # Service Provider Interface
            ├── %s-core/          # Implementation
            ├── %s-facade/        # Entry point
            └── doc/              # Documentation
            ```

            ## Development Workflow

            1. Create feature branch from `dev`
            2. Implement changes with tests
            3. Run `mvn verify` to ensure compliance
            4. Create pull request

            ## Coding Standards

            - Follow SEA compliance rules
            - Maintain test coverage > 80%%
            - Document public APIs

            ## Testing

            ```bash
            # Unit tests
            mvn test

            # Integration tests
            mvn verify -Pintegration

            # Coverage report
            mvn jacoco:report
            ```

            ## Debugging

            - Enable debug logging: `-Dlogging.level.dev.engineeringlab=DEBUG`
            - Remote debug: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005`
            """
        .formatted(
            titleCase, moduleName, moduleName, moduleName, moduleName, moduleName, moduleName);
  }

  private List<Path> createOpsDocs(
      Path moduleRoot, String moduleName, FixerContext context, List<String> diffs)
      throws IOException {

    List<Path> created = new ArrayList<>();
    Path opsDir = moduleRoot.resolve("doc/6-operations");
    Path manualPath = opsDir.resolve("manual.md");

    if (!Files.exists(manualPath)) {
      if (!context.dryRun()) {
        writeFile(manualPath, generateOperationsManual(moduleName));
      }
      diffs.add("+ Created doc/6-operations/manual.md");
      created.add(manualPath);
    }

    return created;
  }

  private String generateOperationsManual(String moduleName) {
    String titleCase = toTitleCase(moduleName);
    return """
            # %s Operations Manual

            ## Installation

            ### Maven

            ```xml
            <dependency>
                <groupId>dev.engineeringlab</groupId>
                <artifactId>%s-facade</artifactId>
                <version>${version}</version>
            </dependency>
            ```

            ### Configuration

            ```properties
            # application.properties
            %s.enabled=true
            %s.timeout=30000
            ```

            ## Usage

            ### Basic Usage

            ```bash
            mvn %s:scan
            mvn %s:fix -DdryRun=true
            ```

            ### Advanced Options

            | Option | Description | Default |
            |--------|-------------|---------|
            | `-DdryRun` | Preview changes | false |
            | `-Dskip` | Skip execution | false |

            ## Monitoring

            ### Health Checks

            - Check application logs for errors
            - Monitor execution time

            ### Metrics

            - Violations found
            - Fixes applied
            - Execution time

            ## Troubleshooting

            ### Common Issues

            1. **Build fails**: Run `mvn clean install -U`
            2. **Tests fail**: Check test reports in `target/surefire-reports`

            ### Logs

            ```bash
            # Enable debug logging
            mvn <goal> -X
            ```

            ## Maintenance

            ### Upgrades

            1. Update version in pom.xml
            2. Run `mvn clean verify`
            3. Review release notes for breaking changes
            """
        .formatted(titleCase, moduleName, moduleName, moduleName, moduleName, moduleName);
  }

  // toTitleCase() inherited from AbstractStructureFixer
}
