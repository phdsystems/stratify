package dev.engineeringlab.example.text.core;

import dev.engineeringlab.example.text.spi.TextProcessor;
import dev.engineeringlab.stratify.annotation.Provider;

/** Processor that converts text to lowercase. */
@Provider(name = "lowercase", priority = 5, description = "Converts text to lowercase")
public class LowerCaseProcessor implements TextProcessor {

  @Override
  public String process(String text) {
    return text.toLowerCase();
  }

  @Override
  public String getType() {
    return "lowercase";
  }
}
