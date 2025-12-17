package dev.engineeringlab.stratify.structure.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Utility for generating template files. */
public class FileGenerator {

  /**
   * Generates a file from a template string with variable substitution.
   *
   * @param targetPath the path to create the file
   * @param template the template content
   * @param variables variables to substitute (${varName})
   * @throws IOException if file creation fails
   */
  public void generate(Path targetPath, String template, Map<String, String> variables)
      throws IOException {
    String content = substituteVariables(template, variables);
    Files.createDirectories(targetPath.getParent());
    Files.writeString(targetPath, content);
  }

  /** Substitutes variables in a template string. */
  public String substituteVariables(String template, Map<String, String> variables) {
    String result = template;
    for (Map.Entry<String, String> entry : variables.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  /** Generates a BaseException class file. */
  public void generateBaseException(Path targetDir, String packageName, String moduleName)
      throws IOException {
    String className = capitalize(moduleName) + "Exception";
    String template =
        """
            package %s;

            /**
             * Base exception for %s module.
             */
            public class %s extends RuntimeException {

                private final ErrorCode errorCode;

                public %s(ErrorCode errorCode, String message) {
                    super(message);
                    this.errorCode = errorCode;
                }

                public %s(ErrorCode errorCode, String message, Throwable cause) {
                    super(message, cause);
                    this.errorCode = errorCode;
                }

                public ErrorCode getErrorCode() {
                    return errorCode;
                }
            }
            """
            .formatted(packageName, moduleName, className, className, className);

    Path targetPath = targetDir.resolve(className + ".java");
    Files.createDirectories(targetDir);
    Files.writeString(targetPath, template);
  }

  /** Generates an ErrorCode enum file. */
  public void generateErrorCode(Path targetDir, String packageName) throws IOException {
    String template =
        """
            package %s;

            /**
             * Error codes for this module.
             */
            public enum ErrorCode {
                UNKNOWN_ERROR("E000", "Unknown error"),
                VALIDATION_ERROR("E001", "Validation failed"),
                NOT_FOUND("E002", "Resource not found"),
                OPERATION_FAILED("E003", "Operation failed");

                private final String code;
                private final String description;

                ErrorCode(String code, String description) {
                    this.code = code;
                    this.description = description;
                }

                public String getCode() {
                    return code;
                }

                public String getDescription() {
                    return description;
                }
            }
            """
            .formatted(packageName);

    Path targetPath = targetDir.resolve("ErrorCode.java");
    Files.createDirectories(targetDir);
    Files.writeString(targetPath, template);
  }

  /** Generates a package-info.java file. */
  public void generatePackageInfo(Path targetDir, String packageName, String description)
      throws IOException {
    String template =
        """
            /**
             * %s
             */
            package %s;
            """
            .formatted(description, packageName);

    Path targetPath = targetDir.resolve("package-info.java");
    Files.createDirectories(targetDir);
    Files.writeString(targetPath, template);
  }

  private String capitalize(String str) {
    if (str == null || str.isEmpty()) {
      return str;
    }
    // Handle hyphenated names: "swq-engine" -> "SwqEngine"
    StringBuilder result = new StringBuilder();
    boolean capitalizeNext = true;
    for (char c : str.toCharArray()) {
      if (c == '-' || c == '_') {
        capitalizeNext = true;
      } else if (capitalizeNext) {
        result.append(Character.toUpperCase(c));
        capitalizeNext = false;
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}
