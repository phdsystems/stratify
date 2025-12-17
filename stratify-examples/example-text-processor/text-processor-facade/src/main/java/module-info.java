/**
 * Text Processor Facade module.
 *
 * <p>Simplified entry point for text processing (Layer 5 - Facade).
 */
module dev.engineeringlab.example.text.facade {
  requires dev.engineeringlab.example.text.api;
  requires dev.engineeringlab.example.text.common;
  requires dev.engineeringlab.example.text.core;
  requires dev.engineeringlab.example.text.spi;
  requires dev.engineeringlab.stratify.annotation;
  requires dev.engineeringlab.stratify.core;

  exports dev.engineeringlab.example.text;
}
