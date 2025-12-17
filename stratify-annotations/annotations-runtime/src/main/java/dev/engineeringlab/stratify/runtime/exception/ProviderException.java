package dev.engineeringlab.stratify.runtime.exception;

/**
 * Exception thrown when provider discovery or instantiation fails.
 *
 * <p>This exception type is thrown by the service discovery mechanism. Domain modules should catch
 * this exception and wrap it in their own exception hierarchy with appropriate error codes.
 *
 * @since 1.0.0
 */
public class ProviderException extends RuntimeException {

  private final Class<?> providerType;

  /**
   * Creates a new provider exception.
   *
   * @param providerType the provider interface type
   * @param message the error message
   */
  public ProviderException(Class<?> providerType, String message) {
    super(message);
    this.providerType = providerType;
  }

  /**
   * Creates a new provider exception with a cause.
   *
   * @param providerType the provider interface type
   * @param message the error message
   * @param cause the underlying cause
   */
  public ProviderException(Class<?> providerType, String message, Throwable cause) {
    super(message, cause);
    this.providerType = providerType;
  }

  /**
   * Gets the provider interface type that failed.
   *
   * @return the provider type
   */
  public Class<?> getProviderType() {
    return providerType;
  }
}
