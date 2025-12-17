package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Remediator for AG-005: Maven Wrapper Exists.
 *
 * <p>Creates Maven wrapper files (mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties) for pure
 * aggregator modules. The wrapper files are bundled as resources and copied to the target module.
 *
 * <p>The Maven wrapper allows building the project without requiring Maven to be pre-installed,
 * ensuring reproducible builds across different environments.
 */
public class MavenWrapperRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"AG-005"};
  private static final int PRIORITY = 80;

  private static final String RESOURCE_BASE = "/maven-wrapper/";
  private static final String MVNW = "mvnw";
  private static final String MVNW_CMD = "mvnw.cmd";
  private static final String MAVEN_WRAPPER_PROPERTIES = "maven-wrapper.properties";
  private static final String MAVEN_WRAPPER_DIR = ".mvn/wrapper";

  public MavenWrapperRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "MavenWrapperRemediator";
  }

  @Override
  public String getDescription() {
    return "Creates Maven wrapper files for pure aggregator modules (AG-005)";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!canFix(violation)) {
      return FixResult.skipped(violation, "Not an AG-005 violation");
    }

    try {
      Path moduleRoot = deriveModuleRoot(violation, context);
      List<Path> modifiedFiles = new ArrayList<>();
      List<String> diffs = new ArrayList<>();

      // Create .mvn/wrapper directory if needed
      Path wrapperDir = moduleRoot.resolve(MAVEN_WRAPPER_DIR);
      if (!Files.exists(wrapperDir)) {
        if (context.dryRun()) {
          diffs.add("+ Would create directory: " + MAVEN_WRAPPER_DIR);
        } else {
          Files.createDirectories(wrapperDir);
          context.log("Created directory: %s", wrapperDir);
        }
      }

      // Copy mvnw
      Path mvnwPath = moduleRoot.resolve(MVNW);
      if (!Files.exists(mvnwPath)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + MVNW);
        } else {
          copyResource(RESOURCE_BASE + MVNW, mvnwPath);
          makeExecutable(mvnwPath);
          modifiedFiles.add(mvnwPath);
          context.log("Created: %s", mvnwPath);
        }
      }

      // Copy mvnw.cmd
      Path mvnwCmdPath = moduleRoot.resolve(MVNW_CMD);
      if (!Files.exists(mvnwCmdPath)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + MVNW_CMD);
        } else {
          copyResource(RESOURCE_BASE + MVNW_CMD, mvnwCmdPath);
          modifiedFiles.add(mvnwCmdPath);
          context.log("Created: %s", mvnwCmdPath);
        }
      }

      // Copy maven-wrapper.properties
      Path propertiesPath = wrapperDir.resolve(MAVEN_WRAPPER_PROPERTIES);
      if (!Files.exists(propertiesPath)) {
        if (context.dryRun()) {
          diffs.add("+ Would create: " + MAVEN_WRAPPER_DIR + "/" + MAVEN_WRAPPER_PROPERTIES);
        } else {
          copyResource(RESOURCE_BASE + MAVEN_WRAPPER_PROPERTIES, propertiesPath);
          modifiedFiles.add(propertiesPath);
          context.log("Created: %s", propertiesPath);
        }
      }

      if (diffs.isEmpty() && modifiedFiles.isEmpty()) {
        return FixResult.skipped(violation, "All Maven wrapper files already exist");
      }

      if (context.dryRun()) {
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.FIXED)
            .description("Would create Maven wrapper files")
            .diffs(diffs)
            .build();
      }

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Created Maven wrapper files (" + modifiedFiles.size() + " files)")
          .modifiedFiles(modifiedFiles)
          .build();

    } catch (Exception e) {
      return FixResult.failed(violation, "Failed to create Maven wrapper: " + e.getMessage());
    }
  }

  /** Copies a resource from the classpath to the target path. */
  private void copyResource(String resourcePath, Path targetPath) throws IOException {
    try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IOException("Resource not found: " + resourcePath);
      }
      Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Makes a file executable on Unix-like systems. On Windows, this is a no-op. */
  private void makeExecutable(Path file) {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(file);
      permissions.add(PosixFilePermission.OWNER_EXECUTE);
      permissions.add(PosixFilePermission.GROUP_EXECUTE);
      permissions.add(PosixFilePermission.OTHERS_EXECUTE);
      Files.setPosixFilePermissions(file, permissions);
    } catch (UnsupportedOperationException | IOException e) {
      // Ignore on Windows or if permission setting fails
    }
  }
}
