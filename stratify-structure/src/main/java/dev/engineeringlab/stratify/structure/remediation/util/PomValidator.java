package dev.engineeringlab.stratify.structure.remediation.util;

import java.nio.file.Path;

/**
 * Validates POM files after fixes are applied.
 *
 * <p>Used by the orchestrator to verify that fixes didn't break the build before committing the
 * changes.
 */
@FunctionalInterface
public interface PomValidator {

  /**
   * Validates the POM structure at the given module root.
   *
   * @param moduleRoot the module root directory containing pom.xml
   * @return true if validation passes, false if it fails
   */
  boolean validate(Path moduleRoot);
}
