package dev.engineeringlab.example.text.api;

import dev.engineeringlab.example.text.common.model.ProcessRequest;
import dev.engineeringlab.example.text.common.model.ProcessResult;

import java.util.List;

/**
 * Public API for text processing operations.
 *
 * <p>This interface defines the high-level operations available to users.
 */
public interface TextService {

    /**
     * Process text according to the request.
     *
     * @param request the processing request
     * @return the processing result
     */
    ProcessResult process(ProcessRequest request);

    /**
     * Get a list of available processor types.
     *
     * @return list of processor type names
     */
    List<String> getAvailableProcessors();
}
