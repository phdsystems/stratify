package dev.engineeringlab.stratify.plugin.reporter;

import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Outputs validation results to console with color coding by severity.
 * Provides a summary at the end.
 */
public class ConsoleReporter {
    private static final Logger log = LoggerFactory.getLogger(ConsoleReporter.class);

    // ANSI color codes
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String GREEN = "\u001B[32m";
    private static final String BOLD = "\u001B[1m";

    private final boolean useColors;

    public ConsoleReporter() {
        this(true);
    }

    public ConsoleReporter(boolean useColors) {
        this.useColors = useColors && System.console() != null;
    }

    /**
     * Reports validation results to console.
     *
     * @param results list of validation results
     */
    public void report(List<ValidationResult> results) {
        if (results.isEmpty()) {
            printSuccess();
            return;
        }

        printHeader();
        printResults(results);
        printSummary(results);
    }

    private void printHeader() {
        System.out.println();
        System.out.println(bold("SEA Compliance Validation Results"));
        System.out.println("=".repeat(50));
        System.out.println();
    }

    private void printResults(List<ValidationResult> results) {
        // Group by severity
        Map<ValidationResult.Severity, List<ValidationResult>> grouped = results.stream()
                .collect(Collectors.groupingBy(ValidationResult::severity));

        // Print errors first
        printResultGroup("ERRORS", grouped.get(ValidationResult.Severity.ERROR), RED);

        // Then warnings
        printResultGroup("WARNINGS", grouped.get(ValidationResult.Severity.WARNING), YELLOW);

        // Then info
        printResultGroup("INFO", grouped.get(ValidationResult.Severity.INFO), BLUE);
    }

    private void printResultGroup(String title, List<ValidationResult> results, String color) {
        if (results == null || results.isEmpty()) {
            return;
        }

        System.out.println(bold(title + ":"));
        System.out.println();

        for (ValidationResult result : results) {
            printResult(result, color);
        }

        System.out.println();
    }

    private void printResult(ValidationResult result, String color) {
        String severityBadge = formatSeverity(result.severity(), color);
        String ruleId = bold("[" + result.ruleId() + "]");

        System.out.println("  " + severityBadge + " " + ruleId + " " + result.message());

        if (result.location() != null) {
            System.out.println("      Location: " + result.location());
        }

        System.out.println();
    }

    private void printSummary(List<ValidationResult> results) {
        System.out.println("=".repeat(50));
        System.out.println();

        long errors = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.ERROR)
                .count();
        long warnings = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.WARNING)
                .count();
        long infos = results.stream()
                .filter(r -> r.severity() == ValidationResult.Severity.INFO)
                .count();

        System.out.println(bold("Summary:"));
        System.out.println("  " + colorize(RED, "Errors:   " + errors));
        System.out.println("  " + colorize(YELLOW, "Warnings: " + warnings));
        System.out.println("  " + colorize(BLUE, "Info:     " + infos));
        System.out.println("  Total:    " + results.size());
        System.out.println();

        if (errors > 0) {
            System.out.println(colorize(RED, bold("VALIDATION FAILED")));
        } else if (warnings > 0) {
            System.out.println(colorize(YELLOW, bold("VALIDATION PASSED WITH WARNINGS")));
        } else {
            System.out.println(colorize(GREEN, bold("VALIDATION PASSED")));
        }
        System.out.println();
    }

    private void printSuccess() {
        System.out.println();
        System.out.println(bold("SEA Compliance Validation Results"));
        System.out.println("=".repeat(50));
        System.out.println();
        System.out.println(colorize(GREEN, bold("✓ All validations passed!")));
        System.out.println();
        System.out.println("No issues found. Project structure complies with SEA principles.");
        System.out.println();
    }

    private String formatSeverity(ValidationResult.Severity severity, String color) {
        String text = switch (severity) {
            case ERROR -> "ERROR  ";
            case WARNING -> "WARNING";
            case INFO -> "INFO   ";
        };

        return colorize(color, bold("[" + text + "]"));
    }

    private String bold(String text) {
        return useColors ? BOLD + text + RESET : text;
    }

    private String colorize(String color, String text) {
        return useColors ? color + text + RESET : text;
    }
}
