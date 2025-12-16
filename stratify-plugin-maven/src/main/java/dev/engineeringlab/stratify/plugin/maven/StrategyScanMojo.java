package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ScanResult;
import dev.engineeringlab.stratify.plugin.scanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.reporter.ConsoleReporter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.File;

/**
 * Scans and reports the module structure without performing validation.
 * Does not fail the build - used for analysis and diagnostics.
 */
@Mojo(name = "scan")
public class StrategyScanMojo extends AbstractMojo {

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
     * Whether to skip scanning.
     */
    @Parameter(property = "stratify.skip", defaultValue = "false")
    private boolean skip;

    @Override
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Stratify scan skipped");
            return;
        }

        getLog().info("Scanning SEA module structure...");
        getLog().info("Source directory: " + sourceDirectory);

        try {
            // Scan modules
            ModuleScanner scanner = new ModuleScanner();
            ScanResult scanResult = scanner.scan(sourceDirectory.toPath());

            // Report findings
            getLog().info("=== Module Scan Results ===");
            getLog().info("Total modules found: " + scanResult.getModules().size());
            getLog().info("");

            if (scanResult.getModules().isEmpty()) {
                getLog().warn("No SEA modules found. Ensure classes are annotated with @SEAModule");
            } else {
                for (ModuleInfo module : scanResult.getModules()) {
                    reportModule(module);
                }
            }

            getLog().info("=== Scan Complete ===");

        } catch (Exception e) {
            throw new MojoExecutionException("Scan failed: " + e.getMessage(), e);
        }
    }

    private void reportModule(ModuleInfo module) {
        getLog().info("Module: " + module.getName());
        getLog().info("  Type: " + module.getType());
        getLog().info("  Layer: " + module.getLayer());
        getLog().info("  Exports: " + module.getExports().size() + " items");
        getLog().info("  Dependencies: " + module.getDependencies().size() + " modules");

        if (!module.getDependencies().isEmpty()) {
            getLog().info("    -> " + String.join(", ", module.getDependencies()));
        }

        getLog().info("");
    }
}
