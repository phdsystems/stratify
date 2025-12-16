package dev.engineeringlab.example.text;

import dev.engineeringlab.example.text.api.TextService;
import dev.engineeringlab.example.text.common.model.ProcessRequest;
import dev.engineeringlab.example.text.common.model.ProcessResult;
import dev.engineeringlab.example.text.core.DefaultTextService;
import dev.engineeringlab.example.text.spi.TextProcessor;
import dev.engineeringlab.stratify.annotation.Facade;
import dev.engineeringlab.stratify.provider.ProviderDiscovery;
import dev.engineeringlab.stratify.registry.ProviderRegistry;

import java.util.List;

/**
 * Facade entry point for text processing operations.
 *
 * <p>This class provides a simplified, static API for common text processing tasks.
 * It automatically discovers and manages TextProcessor implementations.
 *
 * <p>Example usage:
 * <pre>
 * // Process text with automatic provider discovery
 * ProcessResult result = TextProcessing.process("Hello World", "uppercase");
 * System.out.println(result.processedText()); // "HELLO WORLD"
 *
 * // List available processors
 * List{@literal <}String{@literal >} processors = TextProcessing.availableProcessors();
 * </pre>
 */
@Facade(description = "Text processing facade with automatic provider discovery")
public final class TextProcessing {

    private static final TextService service;

    static {
        // Discover and register all TextProcessor implementations
        ProviderRegistry<TextProcessor> registry =
            ProviderDiscovery.loadRegistry(TextProcessor.class);
        service = new DefaultTextService(registry);
    }

    private TextProcessing() {
        // Prevent instantiation
    }

    /**
     * Process text using the specified processor type.
     *
     * @param text the text to process
     * @param processorType the type of processing (e.g., "uppercase", "lowercase")
     * @return the processing result
     */
    public static ProcessResult process(String text, String processorType) {
        ProcessRequest request = new ProcessRequest(text, processorType);
        return service.process(request);
    }

    /**
     * Get a list of all available processor types.
     *
     * @return list of processor type names
     */
    public static List<String> availableProcessors() {
        return service.getAvailableProcessors();
    }

    /**
     * Get the underlying TextService instance for advanced usage.
     *
     * @return the text service
     */
    public static TextService service() {
        return service;
    }
}
