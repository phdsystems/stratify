package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ScanResult;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import dev.engineeringlab.stratify.plugin.validator.ValidatorEngine;
import dev.engineeringlab.stratify.plugin.reporter.JsonReporter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a JSON report of SEA validation results.
 * The report includes module structure and validation violations.
 * Does not fail the build.
 */
@Mojo(name = "report")
public class StratifyReportMojo extends AbstractMojo {

    /**
     * The Maven project.
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /**
     * Source directories to scan.
     */
    @Parameter(defaultValue = "${project.build.sourceDirectory}")
    private File sourceDirectory;

    /**
     * Output file for the JSON report.
     */
    @Parameter(defaultValue = "${project.build.directory}/stratify-report.json")
    private File outputFile;

    /**
     * Whether to skip report generation.
     */
    @Parameter(property = "stratify.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Stratify report generation skipped");
            return;
        }

        getLog().info("Generating Stratify SEA compliance report...");
        getLog().info("Source directory: " + sourceDirectory);
        getLog().info("Output file: " + outputFile);

        try {
            // Ensure output directory exists
            Path outputPath = outputFile.toPath();
            Files.createDirectories(outputPath.getParent());

            // Scan modules
            ModuleScanner scanner = new ModuleScanner();
            ScanResult scanResult = scanner.scan(sourceDirectory.toPath());

            getLog().info("Scanned " + scanResult.getModules().size() + " modules");

            // Run validators
            ValidatorEngine engine = new ValidatorEngine();
            ValidationResult validationResult = engine.validate(scanResult);

            getLog().info("Found " + validationResult.getViolations().size() + " violations");

            // Generate JSON report
            JsonReporter reporter = new JsonReporter();
            reporter.report(validationResult, outputPath);

            getLog().info("Report generated successfully: " + outputFile.getAbsolutePath());

        } catch (IOException e) {
            throw new MojoExecutionException("Failed to write report: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Report generation failed: " + e.getMessage(), e);
        }
    }
}
