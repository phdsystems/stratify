package dev.engineeringlab.stratify.plugin.maven;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import java.io.File;
import java.util.List;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Scans and reports the module structure without performing validation. Does not fail the build -
 * used for analysis and diagnostics.
 */
@Mojo(name = "scan")
public class StrategyScanMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(defaultValue = "${project.basedir}")
  private File baseDirectory;

  @Parameter(property = "stratify.skip", defaultValue = "false")
  private boolean skip;

  @Override
  public void execute() throws MojoExecutionException {
    if (skip) {
      getLog().info("Stratify scan skipped");
      return;
    }

    getLog().info("Scanning SEA module structure...");
    getLog().info("Base directory: " + baseDirectory);

    try {
      ModuleScanner scanner = new ModuleScanner();
      List<ModuleInfo> modules = scanner.scanModules(baseDirectory.toPath());

      getLog().info("=== Module Scan Results ===");
      getLog().info("Total modules found: " + modules.size());
      getLog().info("");

      if (modules.isEmpty()) {
        getLog().warn("No SEA modules found in " + baseDirectory);
      } else {
        for (ModuleInfo module : modules) {
          reportModule(module);
        }
      }

      getLog().info("=== Scan Complete ===");

    } catch (Exception e) {
      throw new MojoExecutionException("Scan failed: " + e.getMessage(), e);
    }
  }

  private void reportModule(ModuleInfo module) {
    getLog().info("Module: " + module.artifactId());
    getLog().info("  Group: " + module.groupId());
    getLog().info("  Layer: " + module.layer());
    getLog().info("  Path: " + module.path());
    getLog().info("  Dependencies: " + module.dependencies().size());

    if (!module.dependencies().isEmpty()) {
      getLog().info("    -> " + String.join(", ", module.dependencies()));
    }

    getLog().info("");
  }
}
