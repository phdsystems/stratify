package dev.engineeringlab.stratify.error;

/**
 * Base exception for all Stratify operations.
 *
 * <p>All Stratify exceptions include an {@link ErrorCode} for programmatic
 * error handling and categorization.
 */
public class StratifyException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * Creates a new exception with the specified error code.
     *
     * @param errorCode the error code
     */
    public StratifyException(ErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with the specified error code and detail message.
     *
     * @param errorCode the error code
     * @param detail additional detail message
     */
    public StratifyException(ErrorCode errorCode, String detail) {
        super(errorCode.toString() + " - " + detail);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with the specified error code and cause.
     *
     * @param errorCode the error code
     * @param cause the underlying cause
     */
    public StratifyException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.toString(), cause);
        this.errorCode = errorCode;
    }

    /**
     * Creates a new exception with all details.
     *
     * @param errorCode the error code
     * @param detail additional detail message
     * @param cause the underlying cause
     */
    public StratifyException(ErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.toString() + " - " + detail, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the error code for this exception.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the numeric error code.
     *
     * @return the numeric code
     */
    public int getCode() {
        return errorCode.code();
    }
}
