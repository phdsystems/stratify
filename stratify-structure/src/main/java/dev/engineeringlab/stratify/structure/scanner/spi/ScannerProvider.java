package dev.engineeringlab.stratify.structure.scanner.spi;

import java.util.List;

/**
 * Service Provider Interface for scanner providers.
 *
 * <p>Implementations provide scanners for specific target types (e.g., Maven modules, source files,
 * configuration files). The scanner engine discovers providers via ServiceLoader.
 *
 * @param <T> the target type this provider supplies scanners for
 */
public interface ScannerProvider<T> {

  /**
   * Gets all scanners provided by this provider.
   *
   * @return list of scanners
   */
  List<Scanner<T>> getScanners();

  /**
   * Gets the target type this provider supports.
   *
   * @return the target class
   */
  Class<T> getTargetType();

  /**
   * Gets a unique identifier for this provider.
   *
   * @return provider ID (e.g., "naming-conventions", "module-structure")
   */
  String getProviderId();

  /**
   * Gets a human-readable name for this provider.
   *
   * @return provider name
   */
  String getName();

  /**
   * Gets a description of what this provider scans for.
   *
   * @return provider description (defaults to provider name)
   */
  default String getDescription() {
    return getName();
  }

  /**
   * Gets the priority of this provider (higher = loaded first).
   *
   * @return priority value (default 0)
   */
  default int getPriority() {
    return 0;
  }
}
