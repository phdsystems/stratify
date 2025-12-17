package dev.engineeringlab.stratify.structure.remediation.api;

/** Severity levels for structure violations. */
public enum ViolationSeverity {

  /** Informational - suggestion for improvement. */
  INFO(0),

  /** Warning - should be fixed but doesn't fail the build. */
  WARNING(1),

  /** Error - must be fixed, fails the build. */
  ERROR(2),

  /** Critical - severe issue that must be addressed immediately. */
  CRITICAL(3);

  private final int level;

  ViolationSeverity(int level) {
    this.level = level;
  }

  /** Returns the numeric level for comparison. */
  public int getLevel() {
    return level;
  }

  /** Returns true if this severity is at least as severe as the given threshold. */
  public boolean isAtLeast(ViolationSeverity threshold) {
    return this.level >= threshold.level;
  }
}
