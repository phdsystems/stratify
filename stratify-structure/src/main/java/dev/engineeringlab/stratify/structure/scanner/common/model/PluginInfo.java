package dev.engineeringlab.stratify.structure.scanner.common.model;

import lombok.Builder;
import lombok.Data;

/** Information about a Maven plugin found in a module. */
@Data
@Builder
public class PluginInfo {
  /** Plugin groupId (e.g., "org.apache.maven.plugins"). */
  private String groupId;

  /** Plugin artifactId (e.g., "maven-checkstyle-plugin"). */
  private String artifactId;

  /** Plugin version. */
  private String version;

  /** Whether this is a code quality plugin. */
  @Builder.Default private boolean isCodeQualityPlugin = false;

  /** Plugin type/category. (e.g., "Static Analysis", "Code Coverage", "Enforcer") */
  private String category;

  /**
   * Returns a display name for the plugin.
   *
   * @return human-readable plugin display name
   */
  public String getDisplayName() {
    if (artifactId == null) {
      return "Unknown Plugin";
    }
    // Remove common prefixes/suffixes for cleaner display
    String name =
        artifactId.replace("maven-", "").replace("-maven-plugin", "").replace("-plugin", "");
    return capitalizeWords(name);
  }

  /**
   * Returns the full plugin coordinates.
   *
   * @return Maven coordinates (groupId:artifactId:version)
   */
  public String getCoordinates() {
    StringBuilder sb = new StringBuilder();
    if (groupId != null) {
      sb.append(groupId).append(":");
    }
    sb.append(artifactId);
    if (version != null) {
      sb.append(":").append(version);
    }
    return sb.toString();
  }

  /**
   * Capitalizes words in a hyphen-separated string.
   *
   * @param input the hyphen-separated string
   * @return capitalized string with spaces
   */
  private String capitalizeWords(final String input) {
    if (input == null || input.isEmpty()) {
      return input;
    }
    String[] words = input.split("-");
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < words.length; i++) {
      if (i > 0) {
        result.append(" ");
      }
      String word = words[i];
      if (!word.isEmpty()) {
        result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
      }
    }
    return result.toString();
  }
}
