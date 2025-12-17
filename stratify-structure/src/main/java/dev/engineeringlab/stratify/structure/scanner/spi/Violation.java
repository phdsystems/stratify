package dev.engineeringlab.stratify.structure.scanner.spi;

import lombok.Builder;
import lombok.Value;

/** Represents a violation found during scanning. */
@Value
@Builder
public class Violation {

  /** The scanner that detected the violation. */
  String scannerId;

  /** Human-readable name of the scanner. */
  String scannerName;

  /** The target that violated the rule (module name, file path, etc.). */
  String target;

  /** Detailed description of the violation. */
  String message;

  /** Severity of the violation. */
  Severity severity;

  /** Category of the scanner. */
  Category category;

  /** Suggested fix for the violation. */
  String fix;

  /** Reference to documentation or compliance checklist. */
  String reference;

  /** Optional: specific location (line number, dependency name, etc.). */
  String location;
}
