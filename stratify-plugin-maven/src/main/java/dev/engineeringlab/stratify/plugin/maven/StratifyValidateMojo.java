package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.validator.DependencyValidator;
import dev.engineeringlab.stratify.plugin.validator.ModuleStructureValidator;
import dev.engineeringlab.stratify.plugin.validator.NamingValidator;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import dev.engineeringlab.stratify.plugin.reporter.ConsoleReporter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates SEA compliance by scanning modules and running validators.
 * Fails the build if violations of ERROR severity are found.
 */
@Mojo(name = "validate", defaultPhase = LifecyclePhase.VALIDATE)
public class StratifyValidateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "stratify.failOnWarning", defaultValue = "false")
    private boolean failOnWarning;

    @Parameter(property = "stratify.skip", defaultValue = "false")
    private boolean skip;

    @Parameter(defaultValue = "${project.basedir}")
    private File baseDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Stratify validation skipped");
            return;
        }

        getLog().info("Running Stratify SEA compliance validation...");
        getLog().info("Base directory: " + baseDirectory);

        try {
            // Scan modules
            ModuleScanner scanner = new ModuleScanner();
            List<ModuleInfo> modules = scanner.scanModules(baseDirectory.toPath());

            getLog().info("Found " + modules.size() + " modules");

            // Run all validators
            List<ValidationResult> allResults = new ArrayList<>();

            ModuleStructureValidator structureValidator = new ModuleStructureValidator();
            allResults.addAll(structureValidator.validate(modules));

            NamingValidator namingValidator = new NamingValidator();
            allResults.addAll(namingValidator.validate(modules));

            DependencyValidator dependencyValidator = new DependencyValidator();
            allResults.addAll(dependencyValidator.validate(modules));

            getLog().info("Ran 3 validators, found " + allResults.size() + " issues");

            // Report results
            ConsoleReporter reporter = new ConsoleReporter();
            reporter.report(allResults);

            // Check for failures
            long errorCount = allResults.stream()
                    .filter(r -> r.severity() == ValidationResult.Severity.ERROR)
                    .count();
            long warningCount = allResults.stream()
                    .filter(r -> r.severity() == ValidationResult.Severity.WARNING)
                    .count();

            if (errorCount > 0) {
                throw new MojoFailureException("SEA validation failed with " + errorCount + " errors");
            }

            if (failOnWarning && warningCount > 0) {
                throw new MojoFailureException("SEA validation failed with " + warningCount + " warnings (failOnWarning=true)");
            }

            if (allResults.isEmpty()) {
                getLog().info("SEA validation passed - no issues found");
            } else {
                getLog().info("SEA validation passed with " + warningCount + " warnings");
            }

        } catch (MojoFailureException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Validation failed: " + e.getMessage(), e);
        }
    }
}
