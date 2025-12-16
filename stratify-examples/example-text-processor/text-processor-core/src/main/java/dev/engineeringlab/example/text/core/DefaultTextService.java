package dev.engineeringlab.example.text.core;

import dev.engineeringlab.example.text.api.TextService;
import dev.engineeringlab.example.text.common.exception.ProcessException;
import dev.engineeringlab.example.text.common.model.ProcessRequest;
import dev.engineeringlab.example.text.common.model.ProcessResult;
import dev.engineeringlab.example.text.spi.TextProcessor;
import dev.engineeringlab.stratify.registry.ProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Default implementation of TextService.
 *
 * <p>Uses a ProviderRegistry to manage and select TextProcessor implementations.
 */
public class DefaultTextService implements TextService {

    private static final Logger log = LoggerFactory.getLogger(DefaultTextService.class);
    private final ProviderRegistry<TextProcessor> registry;

    public DefaultTextService(ProviderRegistry<TextProcessor> registry) {
        this.registry = registry;
    }

    @Override
    public ProcessResult process(ProcessRequest request) {
        log.debug("Processing request for type: {}", request.processorType());

        TextProcessor processor = registry.get(request.processorType())
            .orElseThrow(() -> new ProcessException(
                "No processor found for type: " + request.processorType()));

        String processed = processor.process(request.text());

        return new ProcessResult(
            request.text(),
            processed,
            processor.getType()
        );
    }

    @Override
    public List<String> getAvailableProcessors() {
        return registry.names();
    }
}
