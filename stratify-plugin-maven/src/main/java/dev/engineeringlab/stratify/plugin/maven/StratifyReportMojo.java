package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.reporter.JsonReporter;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.validator.DependencyValidator;
import dev.engineeringlab.stratify.plugin.validator.ModuleStructureValidator;
import dev.engineeringlab.stratify.plugin.validator.NamingValidator;
import dev.engineeringlab.stratify.plugin.validator.ValidationResult;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/** Generates a JSON report of SEA validation results. Does not fail the build. */
@Mojo(name = "report")
public class StratifyReportMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.basedir}")
  private File baseDirectory;

  @Parameter(defaultValue = "${project.build.directory}/stratify-report.json")
  private File outputFile;

  @Parameter(property = "stratify.skip", defaultValue = "false")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Stratify report generation skipped");
      return;
    }

    getLog().info("Generating Stratify SEA compliance report...");
    getLog().info("Base directory: " + baseDirectory);
    getLog().info("Output file: " + outputFile);

    try {
      // Ensure output directory exists
      Path outputPath = outputFile.toPath();
      Files.createDirectories(outputPath.getParent());

      // Scan modules
      ModuleScanner scanner = new ModuleScanner();
      List<ModuleInfo> modules = scanner.scanModules(baseDirectory.toPath());

      getLog().info("Scanned " + modules.size() + " modules");

      // Run all validators
      List<ValidationResult> allResults = new ArrayList<>();

      allResults.addAll(new ModuleStructureValidator().validate(modules));
      allResults.addAll(new NamingValidator().validate(modules));
      allResults.addAll(new DependencyValidator().validate(modules));

      getLog().info("Found " + allResults.size() + " issues");

      // Generate JSON report
      JsonReporter reporter = new JsonReporter();
      reporter.reportToFile(allResults, outputPath);

      getLog().info("Report generated: " + outputFile.getAbsolutePath());

    } catch (Exception e) {
      throw new MojoExecutionException("Report generation failed: " + e.getMessage(), e);
    }
  }
}
