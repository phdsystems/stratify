package dev.engineeringlab.stratify.structure.remediation.fixer;

import dev.engineeringlab.stratify.structure.remediation.api.FixResult;
import dev.engineeringlab.stratify.structure.remediation.api.FixerContext;
import dev.engineeringlab.stratify.structure.remediation.api.StructureViolation;
import dev.engineeringlab.stratify.structure.remediation.core.AbstractStructureFixer;
import dev.engineeringlab.stratify.structure.remediation.model.FixStatus;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remediator for FA-002: Facade public methods must return API types, not core types.
 *
 * <p>Detects facade classes returning core implementation types and converts them to return API
 * interfaces instead. For example:
 *
 * <pre>
 * Before: public DefaultAgentRegistry registry() { ... }
 * After:  public AgentRegistry registry() { ... }
 * </pre>
 *
 * <p>The fixer:
 *
 * <ul>
 *   <li>Identifies core return types (from *.core.* packages)
 *   <li>Maps them to corresponding API interfaces
 *   <li>Updates method return types
 *   <li>Adds necessary API imports
 * </ul>
 *
 * @since 0.2.0
 */
public class FacadeReturnTypeRemediator extends AbstractStructureFixer {

  private static final String[] SUPPORTED_RULES = {"FA-002"};
  private static final int PRIORITY = 80; // Run after module structure fixers

  /** Pattern to match method declarations with return types. */
  private static final Pattern METHOD_PATTERN =
      Pattern.compile("(public\\s+(?:static\\s+)?)(\\w+)(\\s+\\w+\\s*\\([^)]*\\)\\s*\\{)");

  /** Pattern to extract simple class name from fully qualified name. */
  private static final Pattern SIMPLE_NAME_PATTERN = Pattern.compile("([A-Z][a-zA-Z0-9]*)$");

  /** Pattern to match import statements. */
  private static final Pattern IMPORT_PATTERN =
      Pattern.compile("import\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);

  /** Known mappings from core types to API interfaces. */
  private static final Map<String, TypeMapping> KNOWN_MAPPINGS = new HashMap<>();

  static {
    // Agent module mappings
    KNOWN_MAPPINGS.put(
        "DefaultAgentRegistry",
        new TypeMapping(
            "dev.engineeringlab.agent.core.DefaultAgentRegistry",
            "dev.engineeringlab.agent.registry.AgentRegistry"));
    KNOWN_MAPPINGS.put(
        "DefaultAgentManager",
        new TypeMapping(
            "dev.engineeringlab.agent.core.DefaultAgentManager",
            "dev.engineeringlab.agent.orchestration.AgentManager"));
    KNOWN_MAPPINGS.put(
        "DefaultCommunicationManager",
        new TypeMapping(
            "dev.engineeringlab.agent.coordination.DefaultCommunicationManager",
            "dev.engineeringlab.agent.communication.CommunicationManager"));
    // Add more mappings as needed
  }

  public FacadeReturnTypeRemediator() {
    setPriority(PRIORITY);
  }

  @Override
  public String getName() {
    return "FacadeReturnTypeRemediator";
  }

  @Override
  public String getDescription() {
    return "Fixes FA-002 violations by changing facade return types from core types to API interfaces";
  }

  @Override
  public String[] getAllSupportedRules() {
    return SUPPORTED_RULES;
  }

  @Override
  public FixResult fix(StructureViolation violation, FixerContext context) {
    if (!"FA-002".equals(violation.ruleId())) {
      return FixResult.skipped(violation, "Not an FA-002 violation");
    }

    Path sourceFile = extractSourceFile(violation);
    if (sourceFile == null || !Files.exists(sourceFile)) {
      return FixResult.skipped(
          violation, "Could not determine source file from violation: " + violation.location());
    }

    try {
      String originalContent = readFile(sourceFile);
      List<String> diffs = new ArrayList<>();

      // Extract the core type from violation message
      String coreType = extractCoreTypeFromMessage(violation.message());
      if (coreType == null) {
        return FixResult.skipped(violation, "Could not determine core type from violation message");
      }

      // Find the API interface mapping
      TypeMapping mapping = findMapping(coreType);
      if (mapping == null) {
        return FixResult.skipped(violation, "No API interface mapping found for: " + coreType);
      }

      // Generate the fixed content
      String modifiedContent = fixReturnTypes(originalContent, coreType, mapping, diffs);

      if (modifiedContent.equals(originalContent)) {
        return FixResult.skipped(
            violation, "No changes needed - return type already correct or not found");
      }

      if (context.dryRun()) {
        return FixResult.builder()
            .violation(violation)
            .status(FixStatus.DRY_RUN)
            .description(
                "Would change return type from " + coreType + " to " + mapping.apiSimpleName())
            .diffs(diffs)
            .build();
      }

      // Backup and write
      backup(sourceFile, context.projectRoot());
      writeFile(sourceFile, modifiedContent);
      cleanupBackupsOnSuccess(List.of(sourceFile), context.projectRoot());

      context.log(
          "Fixed FA-002: Changed return type from %s to %s in %s",
          coreType, mapping.apiSimpleName(), sourceFile.getFileName());

      return FixResult.builder()
          .violation(violation)
          .status(FixStatus.FIXED)
          .description("Changed return type from " + coreType + " to " + mapping.apiSimpleName())
          .modifiedFiles(List.of(sourceFile))
          .diffs(diffs)
          .build();

    } catch (IOException e) {
      rollbackOnFailure(List.of(sourceFile), context.projectRoot());
      return FixResult.failed(violation, "Failed to fix FA-002 violation: " + e.getMessage());
    }
  }

  /** Extracts the source file path from the violation. */
  private Path extractSourceFile(StructureViolation violation) {
    if (violation.location() != null) {
      Path location = violation.location();
      if (Files.isRegularFile(location)) {
        return location;
      }
      // Check if it's a directory containing java files
      if (Files.isDirectory(location)) {
        // Look for facade class in the directory
        try {
          return Files.walk(location)
              .filter(p -> p.toString().endsWith(".java"))
              .filter(
                  p -> {
                    String name = p.getFileName().toString();
                    return !name.equals("package-info.java");
                  })
              .findFirst()
              .orElse(null);
        } catch (IOException e) {
          return null;
        }
      }
    }
    return null;
  }

  /**
   * Extracts the core type name from the violation message.
   *
   * <p>Expected message format: "Method X returns core type Y instead of API type"
   */
  private String extractCoreTypeFromMessage(String message) {
    if (message == null) {
      return null;
    }

    // Look for patterns like "returns DefaultAgentRegistry" or "return type DefaultAgentRegistry"
    Pattern coreTypePattern =
        Pattern.compile(
            "(?:returns?|return type)\\s+(Default\\w+|\\w+Impl)", Pattern.CASE_INSENSITIVE);
    Matcher matcher = coreTypePattern.matcher(message);
    if (matcher.find()) {
      return matcher.group(1);
    }

    // Try to find any known core type in the message
    for (String knownType : KNOWN_MAPPINGS.keySet()) {
      if (message.contains(knownType)) {
        return knownType;
      }
    }

    return null;
  }

  /** Finds the type mapping for a core type. */
  private TypeMapping findMapping(String coreType) {
    // Try direct lookup
    if (KNOWN_MAPPINGS.containsKey(coreType)) {
      return KNOWN_MAPPINGS.get(coreType);
    }

    // Try to infer mapping from naming convention
    // DefaultXxx -> Xxx, XxxImpl -> Xxx
    String inferredApi = null;
    if (coreType.startsWith("Default")) {
      inferredApi = coreType.substring("Default".length());
    } else if (coreType.endsWith("Impl")) {
      inferredApi = coreType.substring(0, coreType.length() - "Impl".length());
    }

    if (inferredApi != null) {
      // Create a dynamic mapping (will need manual import verification)
      return new TypeMapping(coreType, inferredApi);
    }

    return null;
  }

  /** Fixes return types in the source content. */
  private String fixReturnTypes(
      String content, String coreType, TypeMapping mapping, List<String> diffs) {
    String result = content;

    // Find all methods returning the core type
    Matcher methodMatcher = METHOD_PATTERN.matcher(content);
    StringBuilder sb = new StringBuilder();
    int lastEnd = 0;

    while (methodMatcher.find()) {
      String modifier = methodMatcher.group(1);
      String returnType = methodMatcher.group(2);
      String methodSig = methodMatcher.group(3);

      if (returnType.equals(coreType)) {
        sb.append(content, lastEnd, methodMatcher.start());
        String newDecl = modifier + mapping.apiSimpleName() + methodSig;
        sb.append(newDecl);

        diffs.add("- " + methodMatcher.group(0).trim());
        diffs.add("+ " + newDecl.trim());

        lastEnd = methodMatcher.end();
      }
    }

    if (lastEnd > 0) {
      sb.append(content.substring(lastEnd));
      result = sb.toString();
    }

    // Handle imports
    result = updateImports(result, coreType, mapping, diffs);

    return result;
  }

  /** Updates imports to use API interface instead of core type. */
  private String updateImports(
      String content, String coreType, TypeMapping mapping, List<String> diffs) {
    String result = content;

    // Check if API import already exists
    boolean hasApiImport = content.contains("import " + mapping.apiFqn + ";");
    boolean hasCoreImport = content.contains("import " + mapping.coreFqn + ";");

    // Add API import if needed
    if (!hasApiImport && mapping.apiFqn.contains(".")) {
      // Find the last import or package statement
      int insertPos = findImportInsertPosition(content);
      if (insertPos > 0) {
        String newImport = "import " + mapping.apiFqn + ";\n";
        result = result.substring(0, insertPos) + newImport + result.substring(insertPos);
        diffs.add("+ " + newImport.trim());
      }
    }

    // Remove core import if it's no longer used
    if (hasCoreImport && !result.contains(coreType)) {
      String coreImportLine = "import " + mapping.coreFqn + ";";
      // Check if the import is on its own line
      Pattern coreImportPattern =
          Pattern.compile(
              "^import\\s+" + Pattern.quote(mapping.coreFqn) + "\\s*;\\s*\\n?", Pattern.MULTILINE);
      Matcher importMatcher = coreImportPattern.matcher(result);
      if (importMatcher.find()) {
        diffs.add("- " + coreImportLine);
        result = importMatcher.replaceFirst("");
      }
    }

    return result;
  }

  /** Finds the position to insert a new import statement. */
  private int findImportInsertPosition(String content) {
    // Find the last import statement
    int lastImport = content.lastIndexOf("import ");
    if (lastImport >= 0) {
      int endOfLine = content.indexOf('\n', lastImport);
      if (endOfLine >= 0) {
        return endOfLine + 1;
      }
    }

    // Fall back to after package statement
    int packageEnd = content.indexOf(';');
    if (packageEnd >= 0) {
      int newline = content.indexOf('\n', packageEnd);
      if (newline >= 0) {
        return newline + 1;
      }
    }

    return -1;
  }

  /** Represents a mapping from a core type to its API interface. */
  private record TypeMapping(String coreFqn, String apiFqn) {
    String apiSimpleName() {
      int lastDot = apiFqn.lastIndexOf('.');
      return lastDot >= 0 ? apiFqn.substring(lastDot + 1) : apiFqn;
    }

    String coreSimpleName() {
      int lastDot = coreFqn.lastIndexOf('.');
      return lastDot >= 0 ? coreFqn.substring(lastDot + 1) : coreFqn;
    }
  }
}
