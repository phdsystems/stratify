/**
 * Text Processor Core module.
 *
 * <p>Core implementation with providers (Layer 4 - Core).
 */
module dev.engineeringlab.example.text.core {
  requires dev.engineeringlab.example.text.api;
  requires dev.engineeringlab.example.text.spi;
  requires dev.engineeringlab.stratify.annotation;
  requires dev.engineeringlab.stratify.core;
  requires org.slf4j;

  exports dev.engineeringlab.example.text.core;

  provides dev.engineeringlab.example.text.spi.TextProcessor with
      dev.engineeringlab.example.text.core.UpperCaseProcessor,
      dev.engineeringlab.example.text.core.LowerCaseProcessor;
}
