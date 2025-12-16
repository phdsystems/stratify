package dev.engineeringlab.example.text.common.model;

import java.util.Objects;

/**
 * Result of text processing.
 *
 * @param originalText the original input text
 * @param processedText the processed output text
 * @param processorUsed the name of the processor that was used
 */
public record ProcessResult(String originalText, String processedText, String processorUsed) {

    public ProcessResult {
        Objects.requireNonNull(originalText, "originalText cannot be null");
        Objects.requireNonNull(processedText, "processedText cannot be null");
        Objects.requireNonNull(processorUsed, "processorUsed cannot be null");
    }
}
