/**
 * Stratify Annotations Processor module.
 *
 * <p>Provides compile-time annotation processors for SEA provider auto-registration.
 */
module dev.engineeringlab.stratify.processor {
  requires java.compiler;
  requires dev.engineeringlab.stratify.annotation;

  exports dev.engineeringlab.stratify.processor;

  provides javax.annotation.processing.Processor with
      dev.engineeringlab.stratify.processor.ProviderProcessor;
}
