package dev.engineeringlab.stratify.plugin.scanner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleInfo;
import dev.engineeringlab.stratify.plugin.scanner.ModuleScanner.ModuleLayer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModuleScannerTest {

  @TempDir Path tempDir;

  private final ModuleScanner scanner = new ModuleScanner();

  @Test
  void shouldThrowForNonDirectory() {
    Path file = tempDir.resolve("not-a-dir.txt");
    try {
      Files.createFile(file);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    assertThatThrownBy(() -> scanner.scanModules(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must be a directory");
  }

  @Test
  void shouldReturnEmptyListForEmptyDirectory() throws IOException {
    List<ModuleInfo> modules = scanner.scanModules(tempDir);

    assertThat(modules).isEmpty();
  }

  @Test
  void shouldDetectMavenModule() throws IOException {
    Path moduleDir = tempDir.resolve("my-module-api");
    Files.createDirectory(moduleDir);
    Files.writeString(
        moduleDir.resolve("pom.xml"),
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.example</groupId>
                <artifactId>my-module-api</artifactId>
                <version>1.0.0</version>
            </project>
            """);

    List<ModuleInfo> modules = scanner.scanModules(tempDir);

    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.artifactId()).isEqualTo("my-module-api");
    assertThat(module.groupId()).isEqualTo("com.example");
    assertThat(module.layer()).isEqualTo(ModuleLayer.API);
  }

  @Test
  void shouldDetectLayerFromSuffix() throws IOException {
    createMavenModule("test-common", "com.test");
    createMavenModule("test-spi", "com.test");
    createMavenModule("test-api", "com.test");
    createMavenModule("test-core", "com.test");
    createMavenModule("test-facade", "com.test");

    List<ModuleInfo> modules = scanner.scanModules(tempDir);

    assertThat(modules).hasSize(5);
    assertThat(modules)
        .extracting(ModuleInfo::layer)
        .containsExactlyInAnyOrder(
            ModuleLayer.COMMON,
            ModuleLayer.SPI,
            ModuleLayer.API,
            ModuleLayer.CORE,
            ModuleLayer.FACADE);
  }

  @Test
  void shouldDefaultToFacadeForUnknownSuffix() throws IOException {
    createMavenModule("unknown-module", "com.test");

    List<ModuleInfo> modules = scanner.scanModules(tempDir);

    assertThat(modules).hasSize(1);
    assertThat(modules.get(0).layer()).isEqualTo(ModuleLayer.FACADE);
  }

  @Test
  void shouldIgnoreHiddenDirectories() throws IOException {
    Path hiddenDir = tempDir.resolve(".hidden");
    Files.createDirectory(hiddenDir);
    Files.writeString(
        hiddenDir.resolve("pom.xml"),
        """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>com.example</groupId>
                <artifactId>hidden-module</artifactId>
            </project>
            """);

    List<ModuleInfo> modules = scanner.scanModules(tempDir);

    assertThat(modules).isEmpty();
  }

  @Test
  void moduleInfoShouldProvideFullName() {
    ModuleInfo module =
        new ModuleInfo("my-artifact", "com.example", ModuleLayer.API, tempDir, List.of(), null);

    assertThat(module.getFullName()).isEqualTo("com.example:my-artifact");
  }

  @Test
  void moduleInfoShouldCheckDependencies() {
    ModuleInfo module =
        new ModuleInfo(
            "my-artifact",
            "com.example",
            ModuleLayer.API,
            tempDir,
            List.of("dep-a", "dep-b"),
            null);

    assertThat(module.hasDependency("dep-a")).isTrue();
    assertThat(module.hasDependency("dep-b")).isTrue();
    assertThat(module.hasDependency("dep-c")).isFalse();
  }

  @Test
  void moduleInfoShouldHandleNullDependencies() {
    ModuleInfo module =
        new ModuleInfo("my-artifact", "com.example", ModuleLayer.API, tempDir, null, null);

    assertThat(module.dependencies()).isEmpty();
  }

  private void createMavenModule(String artifactId, String groupId) throws IOException {
    Path moduleDir = tempDir.resolve(artifactId);
    Files.createDirectory(moduleDir);
    Files.writeString(
        moduleDir.resolve("pom.xml"),
        String.format(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <project>
                <groupId>%s</groupId>
                <artifactId>%s</artifactId>
                <version>1.0.0</version>
            </project>
            """,
            groupId, artifactId));
  }
}
