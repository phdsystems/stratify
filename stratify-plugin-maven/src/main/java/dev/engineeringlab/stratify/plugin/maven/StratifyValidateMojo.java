package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ScanResult;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import dev.engineeringlab.stratify.plugin.validator.Validator;
import dev.engineeringlab.stratify.plugin.validator.ValidatorEngine;
import dev.engineeringlab.stratify.plugin.validator.Severity;
import dev.engineeringlab.stratify.plugin.reporter.ConsoleReporter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/**
 * Validates SEA compliance by scanning modules and running validators.
 * Fails the build if violations of ERROR severity are found (or WARNING if failOnWarning is enabled).
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class StratifyValidateMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Whether to fail the build on WARNING severity violations.
     */
    @Parameter(property = "stratify.failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    /**
     * Whether to skip validation.
     */
    @Parameter(property = "stratify.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Source directories to scan.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Stratify validation skipped");
            return;
        }

        getLog().info("Running Stratify SEA compliance validation...");
        getLog().info("Source directory: " + sourceDirectory);

        try {
            // Scan modules
            ModuleScanner scanner = new ModuleScanner();
            ScanResult scanResult = scanner.scan(sourceDirectory.toPath());

            getLog().info("Found " + scanResult.getModules().size() + " modules");

            // Run validators
            ValidatorEngine engine = new ValidatorEngine();
            List<Validator> validators = engine.getValidators();
            getLog().info("Running " + validators.size() + " validators");

            ValidationResult validationResult = engine.validate(scanResult);

            // Report results
            ConsoleReporter reporter = new ConsoleReporter(getLog()::info);
            reporter.report(validationResult);

            // Check for failures
            boolean hasErrors = validationResult.getViolations().stream()
                    .anyMatch(v -> v.getSeverity() == Severity.ERROR);
            boolean hasWarnings = validationResult.getViolations().stream()
                    .anyMatch(v -> v.getSeverity() == Severity.WARNING);

            if (hasErrors) {
                throw new MojoFailureException("SEA validation failed with ERROR violations");
            }

            if (failOnWarning && hasWarnings) {
                throw new MojoFailureException("SEA validation failed with WARNING violations (failOnWarning=true)");
            }

            if (validationResult.getViolations().isEmpty()) {
                getLog().info("SEA validation passed - no violations found");
            } else {
                getLog().info("SEA validation passed with warnings");
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Validation failed: " + e.getMessage(), e);
        }
    }
}
