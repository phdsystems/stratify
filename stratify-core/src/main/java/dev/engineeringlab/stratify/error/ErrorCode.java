package dev.engineeringlab.stratify.error;

/** Error codes for Stratify operations. */
public enum ErrorCode {

  // Registry errors (1xx)
  PROVIDER_NOT_FOUND(100, "Provider not found"),
  PROVIDER_ALREADY_REGISTERED(101, "Provider already registered"),
  REGISTRY_EMPTY(102, "Registry is empty"),
  INVALID_PROVIDER_NAME(103, "Invalid provider name"),

  // Discovery errors (2xx)
  DISCOVERY_FAILED(200, "Provider discovery failed"),
  INSTANTIATION_FAILED(201, "Provider instantiation failed"),
  NO_DEFAULT_CONSTRUCTOR(202, "Provider has no default constructor"),

  // Configuration errors (3xx)
  CONFIG_NOT_FOUND(300, "Configuration not found"),
  CONFIG_INVALID(301, "Invalid configuration"),
  CONFIG_LOAD_FAILED(302, "Failed to load configuration"),

  // Validation errors (4xx)
  LAYER_VIOLATION(400, "Layer dependency violation"),
  CIRCULAR_DEPENDENCY(401, "Circular dependency detected"),
  INVALID_MODULE_STRUCTURE(402, "Invalid module structure"),

  // General errors (9xx)
  UNKNOWN_ERROR(999, "Unknown error");

  private final int code;
  private final String message;

  ErrorCode(int code, String message) {
    this.code = code;
    this.message = message;
  }

  /**
   * Returns the numeric error code.
   *
   * @return the error code
   */
  public int code() {
    return code;
  }

  /**
   * Returns the error message.
   *
   * @return the message
   */
  public String message() {
    return message;
  }

  @Override
  public String toString() {
    return String.format("STRATIFY-%03d: %s", code, message);
  }
}
