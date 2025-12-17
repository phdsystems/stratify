package dev.engineeringlab.stratify.structure.remediation.model;

/** Status of a fix attempt. */
public enum FixStatus {

  /** The violation was successfully fixed. */
  FIXED,

  /** The fix attempt failed. */
  FAILED,

  /** The violation was skipped (e.g., already fixed or disabled). */
  SKIPPED,

  /** The violation cannot be automatically fixed. */
  NOT_FIXABLE,

  /** Dry-run mode - shows what would be changed without applying. */
  DRY_RUN,

  /** The file could not be parsed (syntax error or unsupported construct). */
  PARSE_ERROR,

  /** The fix was generated but validation failed. */
  VALIDATION_FAILED
}
