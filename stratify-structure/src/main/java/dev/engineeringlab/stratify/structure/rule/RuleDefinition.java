package dev.engineeringlab.stratify.structure.rule;

import dev.engineeringlab.stratify.structure.model.Category;
import dev.engineeringlab.stratify.structure.model.Severity;
import java.util.List;

/**
 * Represents a structure rule definition loaded from configuration.
 *
 * <p>Rule definitions contain all metadata and detection criteria for a structure rule, allowing
 * rules to be defined declaratively in .properties or .yaml files rather than programmatically in
 * Java.
 *
 * <p>This is a simplified version focused on structure rules, removing complex detection patterns
 * that aren't needed for basic structure validation.
 */
public record RuleDefinition(
    String id,
    String name,
    String description,
    Category category,
    Severity severity,
    boolean enabled,
    List<String> targetModules,
    DetectionCriteria detection,
    String reason,
    String fix,
    String reference) {

  /** Creates a builder for constructing rule definitions. */
  public static Builder builder() {
    return new Builder();
  }

  /** Detection criteria for identifying violations. */
  public record DetectionCriteria(
      List<String> pathPatterns,
      List<String> filePatterns,
      List<String> packagePatterns,
      DependencyPatterns dependencyPatterns,
      StructurePatterns structurePatterns) {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private List<String> pathPatterns = List.of();
      private List<String> filePatterns = List.of();
      private List<String> packagePatterns = List.of();
      private DependencyPatterns dependencyPatterns;
      private StructurePatterns structurePatterns;

      public Builder pathPatterns(List<String> pathPatterns) {
        this.pathPatterns = pathPatterns != null ? List.copyOf(pathPatterns) : List.of();
        return this;
      }

      public Builder filePatterns(List<String> filePatterns) {
        this.filePatterns = filePatterns != null ? List.copyOf(filePatterns) : List.of();
        return this;
      }

      public Builder packagePatterns(List<String> packagePatterns) {
        this.packagePatterns = packagePatterns != null ? List.copyOf(packagePatterns) : List.of();
        return this;
      }

      public Builder dependencyPatterns(DependencyPatterns dependencyPatterns) {
        this.dependencyPatterns = dependencyPatterns;
        return this;
      }

      public Builder structurePatterns(StructurePatterns structurePatterns) {
        this.structurePatterns = structurePatterns;
        return this;
      }

      public DetectionCriteria build() {
        return new DetectionCriteria(
            pathPatterns, filePatterns, packagePatterns, dependencyPatterns, structurePatterns);
      }
    }
  }

  /** Patterns for detecting dependency issues. */
  public record DependencyPatterns(
      List<String> mustNotContain,
      List<String> mustContain,
      List<String> exceptions,
      String scope) {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private List<String> mustNotContain = List.of();
      private List<String> mustContain = List.of();
      private List<String> exceptions = List.of();
      private String scope;

      public Builder mustNotContain(List<String> mustNotContain) {
        this.mustNotContain = mustNotContain != null ? List.copyOf(mustNotContain) : List.of();
        return this;
      }

      public Builder mustContain(List<String> mustContain) {
        this.mustContain = mustContain != null ? List.copyOf(mustContain) : List.of();
        return this;
      }

      public Builder exceptions(List<String> exceptions) {
        this.exceptions = exceptions != null ? List.copyOf(exceptions) : List.of();
        return this;
      }

      public Builder scope(String scope) {
        this.scope = scope;
        return this;
      }

      public DependencyPatterns build() {
        return new DependencyPatterns(mustNotContain, mustContain, exceptions, scope);
      }
    }
  }

  /** Patterns for detecting structure issues (modules, packages, etc.). */
  public record StructurePatterns(
      List<String> requiredModules,
      List<String> moduleOrder,
      List<String> requiredElements,
      boolean requireParent,
      boolean noSourceCode) {
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private List<String> requiredModules = List.of();
      private List<String> moduleOrder = List.of();
      private List<String> requiredElements = List.of();
      private boolean requireParent = false;
      private boolean noSourceCode = false;

      public Builder requiredModules(List<String> requiredModules) {
        this.requiredModules = requiredModules != null ? List.copyOf(requiredModules) : List.of();
        return this;
      }

      public Builder moduleOrder(List<String> moduleOrder) {
        this.moduleOrder = moduleOrder != null ? List.copyOf(moduleOrder) : List.of();
        return this;
      }

      public Builder requiredElements(List<String> requiredElements) {
        this.requiredElements =
            requiredElements != null ? List.copyOf(requiredElements) : List.of();
        return this;
      }

      public Builder requireParent(boolean requireParent) {
        this.requireParent = requireParent;
        return this;
      }

      public Builder noSourceCode(boolean noSourceCode) {
        this.noSourceCode = noSourceCode;
        return this;
      }

      public StructurePatterns build() {
        return new StructurePatterns(
            requiredModules, moduleOrder, requiredElements, requireParent, noSourceCode);
      }
    }
  }

  /** Builder for RuleDefinition. */
  public static class Builder {
    private String id;
    private String name;
    private String description = "";
    private Category category = Category.STRUCTURE;
    private Severity severity = Severity.ERROR;
    private boolean enabled = true;
    private List<String> targetModules = List.of();
    private DetectionCriteria detection;
    private String reason = "";
    private String fix = "";
    private String reference = "";

    public Builder id(String id) {
      this.id = id;
      return this;
    }

    public Builder name(String name) {
      this.name = name;
      return this;
    }

    public Builder description(String description) {
      this.description = description;
      return this;
    }

    public Builder category(Category category) {
      this.category = category;
      return this;
    }

    public Builder severity(Severity severity) {
      this.severity = severity;
      return this;
    }

    public Builder enabled(boolean enabled) {
      this.enabled = enabled;
      return this;
    }

    public Builder targetModules(List<String> targetModules) {
      this.targetModules = targetModules != null ? List.copyOf(targetModules) : List.of();
      return this;
    }

    public Builder detection(DetectionCriteria detection) {
      this.detection = detection;
      return this;
    }

    public Builder reason(String reason) {
      this.reason = reason;
      return this;
    }

    public Builder fix(String fix) {
      this.fix = fix;
      return this;
    }

    public Builder reference(String reference) {
      this.reference = reference;
      return this;
    }

    public RuleDefinition build() {
      return new RuleDefinition(
          id,
          name,
          description,
          category,
          severity,
          enabled,
          targetModules,
          detection,
          reason,
          fix,
          reference);
    }
  }
}
