package dev.engineeringlab.stratify.plugin.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses build files (pom.xml, build.gradle) to extract module information.
 * Extracts artifactId, dependencies, and parent info.
 */
public class BuildFileScanner {
    private static final Logger log = LoggerFactory.getLogger(BuildFileScanner.class);

    // Maven patterns
    private static final Pattern ARTIFACT_ID_PATTERN = Pattern.compile("<artifactId>([^<]+)</artifactId>");
    private static final Pattern GROUP_ID_PATTERN = Pattern.compile("<groupId>([^<]+)</groupId>");
    private static final Pattern DEPENDENCY_ARTIFACT_PATTERN = Pattern.compile(
            "<dependency>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>",
            Pattern.DOTALL
    );
    private static final Pattern PARENT_ARTIFACT_PATTERN = Pattern.compile(
            "<parent>.*?<artifactId>([^<]+)</artifactId>.*?</parent>",
            Pattern.DOTALL
    );

    // Gradle patterns (basic support)
    private static final Pattern GRADLE_GROUP_PATTERN = Pattern.compile("group\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern GRADLE_NAME_PATTERN = Pattern.compile("rootProject\\.name\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern GRADLE_DEPENDENCY_PATTERN = Pattern.compile(
            "(?:implementation|api|compileOnly)\\s+['\"]([^'\"]+)['\"]"
    );

    /**
     * Parses a Maven pom.xml file.
     *
     * @param pomFile path to pom.xml
     * @return build file information
     * @throws IOException if file cannot be read
     */
    public BuildFileInfo parsePom(Path pomFile) throws IOException {
        log.debug("Parsing pom.xml: {}", pomFile);
        String content = Files.readString(pomFile);

        String artifactId = extractArtifactId(content);
        String groupId = extractGroupId(content);
        String parentArtifactId = extractParentArtifactId(content);
        List<String> dependencies = extractDependencies(content);

        return new BuildFileInfo(artifactId, groupId, dependencies, parentArtifactId);
    }

    /**
     * Parses a Gradle build.gradle file (basic support).
     *
     * @param gradleFile path to build.gradle
     * @return build file information
     * @throws IOException if file cannot be read
     */
    public BuildFileInfo parseGradle(Path gradleFile) throws IOException {
        log.debug("Parsing build.gradle: {}", gradleFile);
        String content = Files.readString(gradleFile);

        // Try to extract from settings.gradle in parent directory
        Path settingsFile = gradleFile.getParent().resolve("settings.gradle");
        String artifactId = extractGradleProjectName(settingsFile);

        if (artifactId == null) {
            // Fall back to directory name
            artifactId = gradleFile.getParent().getFileName().toString();
        }

        String groupId = extractGradleGroup(content);
        List<String> dependencies = extractGradleDependencies(content);

        return new BuildFileInfo(artifactId, groupId, dependencies, null);
    }

    private String extractArtifactId(String pomContent) {
        // Extract first artifactId after project tag (not in parent section)
        String[] lines = pomContent.split("\n");
        boolean inProject = false;
        boolean inParent = false;

        for (String line : lines) {
            if (line.contains("<project")) {
                inProject = true;
            } else if (line.contains("<parent>")) {
                inParent = true;
            } else if (line.contains("</parent>")) {
                inParent = false;
            } else if (inProject && !inParent && line.contains("<artifactId>")) {
                Matcher matcher = ARTIFACT_ID_PATTERN.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1).trim();
                }
            }
        }

        log.warn("No artifactId found in pom.xml");
        return "unknown";
    }

    private String extractGroupId(String pomContent) {
        Matcher matcher = GROUP_ID_PATTERN.matcher(pomContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "unknown";
    }

    private String extractParentArtifactId(String pomContent) {
        Matcher matcher = PARENT_ARTIFACT_PATTERN.matcher(pomContent);
        if (matcher.find()) {
            String parentSection = matcher.group(0);
            Matcher artifactMatcher = ARTIFACT_ID_PATTERN.matcher(parentSection);
            if (artifactMatcher.find()) {
                return artifactMatcher.group(1).trim();
            }
        }
        return null;
    }

    private List<String> extractDependencies(String pomContent) {
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = DEPENDENCY_ARTIFACT_PATTERN.matcher(pomContent);

        while (matcher.find()) {
            String dependency = matcher.group(1).trim();
            dependencies.add(dependency);
        }

        return dependencies;
    }

    private String extractGradleProjectName(Path settingsFile) {
        if (!Files.exists(settingsFile)) {
            return null;
        }

        try {
            String content = Files.readString(settingsFile);
            Matcher matcher = GRADLE_NAME_PATTERN.matcher(content);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        } catch (IOException e) {
            log.warn("Failed to read settings.gradle: {}", e.getMessage());
        }

        return null;
    }

    private String extractGradleGroup(String gradleContent) {
        Matcher matcher = GRADLE_GROUP_PATTERN.matcher(gradleContent);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "unknown";
    }

    private List<String> extractGradleDependencies(String gradleContent) {
        List<String> dependencies = new ArrayList<>();
        Matcher matcher = GRADLE_DEPENDENCY_PATTERN.matcher(gradleContent);

        while (matcher.find()) {
            String dep = matcher.group(1).trim();
            // Extract artifactId from "group:artifact:version"
            String[] parts = dep.split(":");
            if (parts.length >= 2) {
                dependencies.add(parts[1]);
            }
        }

        return dependencies;
    }

    /**
     * Information extracted from a build file.
     */
    public record BuildFileInfo(
            String artifactId,
            String groupId,
            List<String> dependencies,
            String parentArtifactId
    ) {
        public BuildFileInfo {
            dependencies = dependencies != null ? List.copyOf(dependencies) : List.of();
        }
    }
}
