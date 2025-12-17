package dev.engineeringlab.stratify.structure.scanner.common.model;

/** Severity level for compliance violations. */
public enum Severity {
  /** Critical violation that must be fixed immediately (fails build, blocks merge). */
  CRITICAL("CRITICAL", "!!"),

  /** Error violation that must be fixed (fails build). */
  ERROR("ERROR", "X"),

  /** Non-critical violation that should be fixed (optional build failure). */
  WARNING("WARNING", "!"),

  /** Informational message (no build failure). */
  INFO("INFO", "i");

  /** The string representation of this severity level. */
  private final String level;

  /** The icon/emoji representing this severity level. */
  private final String icon;

  /**
   * Constructor for Severity enum.
   *
   * @param severityLevel the severity level string
   * @param severityIcon the severity icon/emoji
   */
  Severity(final String severityLevel, final String severityIcon) {
    this.level = severityLevel;
    this.icon = severityIcon;
  }

  /**
   * Gets the severity level string.
   *
   * @return the severity level
   */
  public String getLevel() {
    return level;
  }

  /**
   * Gets the severity icon/emoji.
   *
   * @return the severity icon
   */
  public String getIcon() {
    return icon;
  }

  @Override
  public String toString() {
    return level;
  }
}
