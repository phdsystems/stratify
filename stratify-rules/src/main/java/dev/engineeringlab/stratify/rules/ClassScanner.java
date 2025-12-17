package dev.engineeringlab.stratify.rules;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Scans classpath for classes in specified packages.
 *
 * <p>Zero-dependency implementation using ClassLoader and file system.
 */
public final class ClassScanner {

  private ClassScanner() {}

  /**
   * Scans for all classes in the given package and subpackages.
   *
   * @param basePackage the base package to scan (e.g., "dev.engineeringlab.llm")
   * @return set of classes found
   */
  public static Set<Class<?>> scan(String basePackage) {
    return scan(basePackage, Thread.currentThread().getContextClassLoader());
  }

  /**
   * Scans for all classes in the given package and subpackages.
   *
   * @param basePackage the base package to scan
   * @param classLoader the class loader to use
   * @return set of classes found
   */
  public static Set<Class<?>> scan(String basePackage, ClassLoader classLoader) {
    Set<Class<?>> classes = new HashSet<>();
    String path = basePackage.replace('.', '/');

    try {
      Enumeration<URL> resources = classLoader.getResources(path);
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        String protocol = resource.getProtocol();

        if ("file".equals(protocol)) {
          scanDirectory(new File(resource.toURI()), basePackage, classes, classLoader);
        } else if ("jar".equals(protocol)) {
          scanJar(resource, basePackage, classes, classLoader);
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to scan package: " + basePackage, e);
    }

    return classes;
  }

  private static void scanDirectory(
      File directory, String packageName, Set<Class<?>> classes, ClassLoader classLoader) {
    if (!directory.exists()) {
      return;
    }

    File[] files = directory.listFiles();
    if (files == null) {
      return;
    }

    for (File file : files) {
      if (file.isDirectory()) {
        scanDirectory(file, packageName + "." + file.getName(), classes, classLoader);
      } else if (file.getName().endsWith(".class")) {
        String className = packageName + "." + file.getName().replace(".class", "");
        loadClass(className, classes, classLoader);
      }
    }
  }

  private static void scanJar(
      URL jarUrl, String basePackage, Set<Class<?>> classes, ClassLoader classLoader) {
    String jarPath = jarUrl.getPath();
    // Extract jar file path from URL like "file:/path/to/jar.jar!/package/path"
    int bangIndex = jarPath.indexOf('!');
    if (bangIndex > 0) {
      jarPath = jarPath.substring(5, bangIndex); // Remove "file:" prefix
    }

    try (JarFile jar = new JarFile(jarPath)) {
      String packagePath = basePackage.replace('.', '/');
      Enumeration<JarEntry> entries = jar.entries();

      while (entries.hasMoreElements()) {
        JarEntry entry = entries.nextElement();
        String name = entry.getName();

        if (name.startsWith(packagePath) && name.endsWith(".class")) {
          String className = name.replace('/', '.').replace(".class", "");
          loadClass(className, classes, classLoader);
        }
      }
    } catch (IOException e) {
      // Ignore jar read errors
    }
  }

  private static void loadClass(String className, Set<Class<?>> classes, ClassLoader classLoader) {
    try {
      // Skip inner classes, anonymous classes, and package-info
      if (className.contains("$") || className.endsWith("package-info")) {
        return;
      }
      Class<?> clazz = classLoader.loadClass(className);
      classes.add(clazz);
    } catch (ClassNotFoundException | NoClassDefFoundError | ExceptionInInitializerError e) {
      // Skip classes that can't be loaded
    }
  }
}
