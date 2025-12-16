package dev.engineeringlab.stratify.plugin.reporter;

import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Outputs validation results as JSON for CI/CD integration.
 * Generates structured JSON output that can be parsed by build systems.
 */
public class JsonReporter {
    private static final Logger log = LoggerFactory.getLogger(JsonReporter.class);

    /**
     * Reports validation results as JSON to console.
     *
     * @param results list of validation results
     */
    public void report(List<ValidationResult> results) {
        String json = generateJson(results);
        System.out.println(json);
    }

    /**
     * Writes validation results as JSON to a file.
     *
     * @param results list of validation results
     * @param outputPath path to output file
     * @throws IOException if file cannot be written
     */
    public void reportToFile(List<ValidationResult> results, Path outputPath) throws IOException {
        log.info("Writing validation results to: {}", outputPath);

        String json = generateJson(results);

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            writer.println(json);
        }

        log.info("Validation results written successfully");
    }

    /**
     * Generates JSON representation of validation results.
     */
    private String generateJson(List<ValidationResult> results) {
        StringBuilder json = new StringBuilder();

        json.append("{\n");
        json.append("  \"timestamp\": \"").append(java.time.Instant.now()).append("\",\n");
        json.append("  \"totalIssues\": ").append(results.size()).append(",\n");

        // Summary by severity
        long errors = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.ERROR)
                .count();
        long warnings = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.WARNING)
                .count();
        long infos = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.INFO)
                .count();

        json.append("  \"summary\": {\n");
        json.append("    \"errors\": ").append(errors).append(",\n");
        json.append("    \"warnings\": ").append(warnings).append(",\n");
        json.append("    \"info\": ").append(infos).append("\n");
        json.append("  },\n");

        // Status
        String status = errors > 0 ? "FAILED" : (warnings > 0 ? "PASSED_WITH_WARNINGS" : "PASSED");
        json.append("  \"status\": \"").append(status).append("\",\n");

        // Results array
        json.append("  \"results\": [\n");

        String resultsJson = results.stream()
                .map(this::formatResult)
                .collect(Collectors.joining(",\n"));

        json.append(resultsJson);
        json.append("\n  ]\n");
        json.append("}\n");

        return json.toString();
    }

    /**
     * Formats a single validation result as JSON.
     */
    private String formatResult(ValidationResult result) {
        StringBuilder json = new StringBuilder();

        json.append("    {\n");
        json.append("      \"ruleId\": \"").append(escapeJson(result.ruleId())).append("\",\n");
        json.append("      \"severity\": \"").append(result.severity()).append("\",\n");
        json.append("      \"message\": \"").append(escapeJson(result.message())).append("\"");

        if (result.location() != null) {
            json.append(",\n");
            json.append("      \"location\": \"").append(escapeJson(result.location())).append("\"");
        }

        json.append("\n    }");

        return json.toString();
    }

    /**
     * Escapes special characters for JSON string values.
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
