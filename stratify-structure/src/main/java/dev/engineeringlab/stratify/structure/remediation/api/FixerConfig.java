package dev.engineeringlab.stratify.structure.remediation.api;

/**
 * Configuration for structure fixers.
 *
 * <p>This is a simplified configuration class that contains only the settings needed by fixers. It
 * is populated from the full configuration by the remediation infrastructure.
 */
public class FixerConfig {

  /** Expected Java package namespace for modules (default: dev.engineeringlab). */
  private String namespace = "dev.engineeringlab";

  /**
   * Project name used in package derivation (e.g., "myproject" -> dev.engineeringlab.myproject.*).
   */
  private String project = null;

  /** Output path for JSON compliance reports. */
  private String reportJsonOutputPath = "target/compliance-reports/compliance-report.json";

  /** Directory for remediation reports (default: .remediation/reports). */
  private String remediationReportDir = ".remediation/reports";

  /** Creates a default configuration. */
  public FixerConfig() {
    // Defaults are set via field initializers
  }

  /** Creates a builder for constructing FixerConfig instances. */
  public static Builder builder() {
    return new Builder();
  }

  // Getters and Setters

  public String getNamespace() {
    return namespace;
  }

  public void setNamespace(String namespace) {
    this.namespace = namespace;
  }

  public String getProject() {
    return project;
  }

  public void setProject(String project) {
    this.project = project;
  }

  public String getReportJsonOutputPath() {
    return reportJsonOutputPath;
  }

  public void setReportJsonOutputPath(String reportJsonOutputPath) {
    this.reportJsonOutputPath = reportJsonOutputPath;
  }

  public String getRemediationReportDir() {
    return remediationReportDir;
  }

  public void setRemediationReportDir(String remediationReportDir) {
    this.remediationReportDir = remediationReportDir;
  }

  /** Builder for FixerConfig. */
  public static class Builder {
    private String namespace = "dev.engineeringlab";
    private String project = null;
    private String reportJsonOutputPath = "target/compliance-reports/compliance-report.json";
    private String remediationReportDir = ".remediation/reports";

    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public Builder project(String project) {
      this.project = project;
      return this;
    }

    public Builder reportJsonOutputPath(String reportJsonOutputPath) {
      this.reportJsonOutputPath = reportJsonOutputPath;
      return this;
    }

    public Builder remediationReportDir(String remediationReportDir) {
      this.remediationReportDir = remediationReportDir;
      return this;
    }

    public FixerConfig build() {
      FixerConfig config = new FixerConfig();
      config.setNamespace(namespace);
      config.setProject(project);
      config.setReportJsonOutputPath(reportJsonOutputPath);
      config.setRemediationReportDir(remediationReportDir);
      return config;
    }
  }
}
