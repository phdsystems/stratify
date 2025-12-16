package dev.engineeringlab.example.text.common.model;

import java.util.Objects;

/**
 * Request to process text.
 *
 * @param text the text to process
 * @param processorType the type of processing to apply
 */
public record ProcessRequest(String text, String processorType) {

    public ProcessRequest {
        Objects.requireNonNull(text, "text cannot be null");
        Objects.requireNonNull(processorType, "processorType cannot be null");

        if (text.isBlank()) {
            throw new IllegalArgumentException("text cannot be blank");
        }
        if (processorType.isBlank()) {
            throw new IllegalArgumentException("processorType cannot be blank");
        }
    }
}
