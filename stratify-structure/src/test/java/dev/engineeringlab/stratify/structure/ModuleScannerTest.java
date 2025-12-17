package dev.engineeringlab.stratify.structure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.engineeringlab.stratify.structure.model.ModuleInfo;
import dev.engineeringlab.stratify.structure.scanner.ModuleScanner;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Test suite for ModuleScanner.
 *
 * <p>Tests the file-based scanner for SEA module structure including:
 *
 * <ul>
 *   <li>Scanning empty directories
 *   <li>Scanning directories with pom.xml files
 *   <li>Identifying layer modules (api, core, facade, spi)
 *   <li>Detecting common/util modules
 * </ul>
 */
class ModuleScannerTest {

  @TempDir Path tempDir;

  private Path projectRoot;

  @BeforeEach
  void setUp() {
    projectRoot = tempDir;
  }

  @AfterEach
  void tearDown() {
    // Cleanup handled by @TempDir
  }

  @Test
  void testScanEmptyDirectory() throws IOException {
    // Given: An empty directory
    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: No modules should be found
    assertThat(modules).isEmpty();
  }

  @Test
  void testScanDirectoryWithSinglePom() throws IOException {
    // Given: A directory with a single pom.xml but no layer pattern
    Files.createFile(projectRoot.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: No modules should be found (no layer pattern)
    assertThat(modules).isEmpty();
  }

  @Test
  void testScanDirectoryWithApiModule() throws IOException {
    // Given: A directory with a text-processor-api module
    Path apiModule = projectRoot.resolve("text-processor-api");
    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: One module should be found with api layer
    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasApi()).isTrue();
    assertThat(module.hasCore()).isFalse();
    assertThat(module.hasFacade()).isFalse();
    assertThat(module.hasSpi()).isFalse();
    assertThat(module.hasCommon()).isFalse();
    assertThat(module.hasUtil()).isFalse();
  }

  @Test
  void testScanDirectoryWithCompleteLayers() throws IOException {
    // Given: A complete module with api, core, facade, and spi
    Path apiModule = projectRoot.resolve("text-processor-api");
    Path coreModule = projectRoot.resolve("text-processor-core");
    Path facadeModule = projectRoot.resolve("text-processor-facade");
    Path spiModule = projectRoot.resolve("text-processor-spi");

    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));
    Files.createDirectories(facadeModule);
    Files.createFile(facadeModule.resolve("pom.xml"));
    Files.createDirectories(spiModule);
    Files.createFile(spiModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: One module should be found with all layers
    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasApi()).isTrue();
    assertThat(module.hasCore()).isTrue();
    assertThat(module.hasFacade()).isTrue();
    assertThat(module.hasSpi()).isTrue();
    assertThat(module.hasCommon()).isFalse();
    assertThat(module.hasUtil()).isFalse();
  }

  @Test
  void testScanDirectoryWithCommonModule() throws IOException {
    // Given: A module with api, core, and common (deprecated pattern)
    Path apiModule = projectRoot.resolve("text-processor-api");
    Path coreModule = projectRoot.resolve("text-processor-core");
    Path commonModule = projectRoot.resolve("text-processor-common");

    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));
    Files.createDirectories(commonModule);
    Files.createFile(commonModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: One module should be found with common flag set
    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasApi()).isTrue();
    assertThat(module.hasCore()).isTrue();
    assertThat(module.hasCommon()).isTrue();
    assertThat(module.isSea4Compliant()).isFalse(); // Not compliant with SEA-4
  }

  @Test
  void testScanDirectoryWithUtilModule() throws IOException {
    // Given: A module with util module
    Path utilModule = projectRoot.resolve("text-processor-util");

    Files.createDirectories(utilModule);
    Files.createFile(utilModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: One module should be found with util flag set
    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasUtil()).isTrue();
  }

  @Test
  void testScanDirectoryWithUtilsModule() throws IOException {
    // Given: A module with utils module (should normalize to util)
    Path utilsModule = projectRoot.resolve("text-processor-utils");

    Files.createDirectories(utilsModule);
    Files.createFile(utilsModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: One module should be found with util flag set (normalized)
    assertThat(modules).hasSize(1);
    ModuleInfo module = modules.get(0);
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasUtil()).isTrue();
  }

  @Test
  void testScanDirectoryWithMultipleModules() throws IOException {
    // Given: Multiple complete modules
    Path textApiModule = projectRoot.resolve("text-processor-api");
    Path textCoreModule = projectRoot.resolve("text-processor-core");
    Path userApiModule = projectRoot.resolve("user-service-api");
    Path userCoreModule = projectRoot.resolve("user-service-core");

    Files.createDirectories(textApiModule);
    Files.createFile(textApiModule.resolve("pom.xml"));
    Files.createDirectories(textCoreModule);
    Files.createFile(textCoreModule.resolve("pom.xml"));
    Files.createDirectories(userApiModule);
    Files.createFile(userApiModule.resolve("pom.xml"));
    Files.createDirectories(userCoreModule);
    Files.createFile(userCoreModule.resolve("pom.xml"));

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: Two modules should be found
    assertThat(modules).hasSize(2);
    assertThat(modules)
        .extracting(ModuleInfo::baseName)
        .containsExactlyInAnyOrder("text-processor", "user-service");
  }

  @Test
  void testScanDirectoryWithoutPomXml() throws IOException {
    // Given: A directory with layer pattern but no pom.xml
    Path apiModule = projectRoot.resolve("text-processor-api");
    Files.createDirectories(apiModule);
    // No pom.xml created

    // When: Scanning for modules
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: No modules should be found
    assertThat(modules).isEmpty();
  }

  @Test
  void testScanWithNullPath() {
    // Given: A null path
    // When/Then: Should throw IllegalArgumentException
    assertThatThrownBy(() -> ModuleScanner.scan(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectRoot cannot be null");
  }

  @Test
  void testScanWithNonDirectoryPath() throws IOException {
    // Given: A file path instead of directory
    Path file = projectRoot.resolve("file.txt");
    Files.createFile(file);

    // When/Then: Should throw IllegalArgumentException
    assertThatThrownBy(() -> ModuleScanner.scan(file))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("projectRoot must be a directory");
  }

  @Test
  void testScanModuleWithValidSubmodules() throws IOException {
    // Given: A module directory with valid submodules
    Path moduleDir = projectRoot.resolve("text-processor");
    Files.createDirectories(moduleDir);

    Path apiModule = moduleDir.resolve("text-processor-api");
    Path coreModule = moduleDir.resolve("text-processor-core");
    Files.createDirectories(apiModule);
    Files.createFile(apiModule.resolve("pom.xml"));
    Files.createDirectories(coreModule);
    Files.createFile(coreModule.resolve("pom.xml"));

    // When: Scanning the specific module
    Optional<ModuleInfo> result = ModuleScanner.scanModule(moduleDir);

    // Then: Module should be found
    assertThat(result).isPresent();
    ModuleInfo module = result.get();
    assertThat(module.baseName()).isEqualTo("text-processor");
    assertThat(module.hasApi()).isTrue();
    assertThat(module.hasCore()).isTrue();
  }

  @Test
  void testScanModuleWithNoSubmodules() throws IOException {
    // Given: A module directory with no submodules
    Path moduleDir = projectRoot.resolve("text-processor");
    Files.createDirectories(moduleDir);

    // When: Scanning the specific module
    Optional<ModuleInfo> result = ModuleScanner.scanModule(moduleDir);

    // Then: No module should be found
    assertThat(result).isEmpty();
  }

  @Test
  void testScanModuleWithNullPath() {
    // Given: A null path
    // When/Then: Should throw IllegalArgumentException
    assertThatThrownBy(() -> ModuleScanner.scanModule(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("modulePath cannot be null");
  }

  @Test
  void testScanNestedModules() throws IOException {
    // Given: Nested module structure
    Path parentModule = projectRoot.resolve("parent");
    Files.createDirectories(parentModule);

    Path textApiModule = parentModule.resolve("text-processor-api");
    Path textCoreModule = parentModule.resolve("text-processor-core");
    Files.createDirectories(textApiModule);
    Files.createFile(textApiModule.resolve("pom.xml"));
    Files.createDirectories(textCoreModule);
    Files.createFile(textCoreModule.resolve("pom.xml"));

    // When: Scanning from project root
    List<ModuleInfo> modules = ModuleScanner.scan(projectRoot);

    // Then: Nested module should be found
    assertThat(modules).hasSize(1);
    assertThat(modules.get(0).baseName()).isEqualTo("text-processor");
  }
}
