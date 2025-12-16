package dev.engineeringlab.example.text.common.exception;

/**
 * Exception thrown when text processing fails.
 */
public class ProcessException extends RuntimeException {

    public ProcessException(String message) {
        super(message);
    }

    public ProcessException(String message, Throwable cause) {
        super(message, cause);
    }
}
