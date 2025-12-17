/**
 * Stratify Annotations Runtime module.
 *
 * <p>Provides runtime utilities for provider discovery and service registry using Java's
 * ServiceLoader mechanism.
 */
module dev.engineeringlab.stratify.runtime {
  requires dev.engineeringlab.stratify.annotation;
  requires org.slf4j;

  exports dev.engineeringlab.stratify.runtime.exception;
  exports dev.engineeringlab.stratify.runtime.registry;
}
