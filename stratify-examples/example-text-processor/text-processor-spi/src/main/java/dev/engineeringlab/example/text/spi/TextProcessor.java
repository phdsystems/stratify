package dev.engineeringlab.example.text.spi;

/**
 * SPI contract for text processing implementations.
 *
 * <p>Implementations should be annotated with @Provider and registered via ServiceLoader for
 * automatic discovery.
 */
public interface TextProcessor {

  /**
   * Process the given text.
   *
   * @param text the text to process
   * @return the processed text
   */
  String process(String text);

  /**
   * Get the type identifier for this processor.
   *
   * @return the processor type (e.g., "uppercase", "lowercase")
   */
  String getType();
}
