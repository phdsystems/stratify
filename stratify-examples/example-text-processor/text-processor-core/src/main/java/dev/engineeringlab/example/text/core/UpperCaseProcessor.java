package dev.engineeringlab.example.text.core;

import dev.engineeringlab.example.text.spi.TextProcessor;
import dev.engineeringlab.stratify.annotation.Provider;

/** Processor that converts text to uppercase. */
@Provider(name = "uppercase", priority = 10, description = "Converts text to uppercase")
public class UpperCaseProcessor implements TextProcessor {

  @Override
  public String process(String text) {
    return text.toUpperCase();
  }

  @Override
  public String getType() {
    return "uppercase";
  }
}
