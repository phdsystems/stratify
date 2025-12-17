package dev.engineeringlab.stratify.structure.model;

/**
 * Represents the category of a validation violation.
 *
 * <p>Categories are used to classify violations by the type of architectural concern they address:
 *
 * <ul>
 *   <li>{@link #STRUCTURE} - Module structure and organization issues
 *   <li>{@link #DEPENDENCIES} - Dependency and access control violations
 *   <li>{@link #NAMING} - Naming convention violations
 * </ul>
 */
public enum Category {
  /**
   * Module structure and organization issues. Violations related to the presence, absence, or
   * organization of required module components (api, core, facade, etc.).
   */
  STRUCTURE,

  /**
   * Dependency and access control violations. Violations related to improper dependencies between
   * modules or submodules, or violations of access restrictions.
   */
  DEPENDENCIES,

  /**
   * Naming convention violations. Violations related to incorrect naming of packages, modules, or
   * other architectural elements.
   */
  NAMING
}
