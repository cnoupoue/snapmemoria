package be.cnoupoue.memoriavault.platform.macos;

import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacosRuntimePaths {

  private static final Logger LOGGER = LoggerFactory.getLogger(MacosRuntimePaths.class);
  private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";
  private static final String FFMPEG_BINARY = "ffmpeg";

  public PlatformRuntimePaths detect() {
    Optional<Path> jpackageAppPath = jpackageAppPath();
    if (jpackageAppPath.isPresent()) {
      return detect(Optional.empty(), jpackageAppPath);
    }

    return detect(codeSourcePath(), Optional.empty());
  }

  PlatformRuntimePaths detect(Optional<Path> codeSourcePath) {
    return detect(codeSourcePath, Optional.empty());
  }

  PlatformRuntimePaths detect(Optional<Path> codeSourcePath, Optional<Path> jpackageAppPath) {
    return detect(codeSourcePath, jpackageAppPath, javaHomePath());
  }

  PlatformRuntimePaths detect(
      Optional<Path> codeSourcePath, Optional<Path> jpackageAppPath, Optional<Path> javaHomePath) {
    Optional<Path> contentsDirectory =
        jpackageAppPath
            .flatMap(this::findContentsDirectoryFromLauncher)
            .or(() -> codeSourcePath.flatMap(this::findJpackageAppDirectory).map(Path::getParent))
            .or(() -> javaHomePath.flatMap(this::findContentsDirectoryFromJavaHome));
    Optional<Path> appDirectory = contentsDirectory.map(directory -> directory.resolve("app"));

    return new PlatformRuntimePaths(
        contentsDirectory.map(Path::getParent).filter(Files::isDirectory),
        jpackageAppPath.or(() -> contentsDirectory.flatMap(this::findLauncherPath)),
        appDirectory.map(directory -> directory.resolve("ffmpeg").resolve(FFMPEG_BINARY)));
  }

  private Optional<Path> codeSourcePath() {
    try {
      URI codeSourceUri =
          MacosRuntimePaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      return Optional.of(Path.of(codeSourceUri).toAbsolutePath().normalize());
    } catch (FileSystemNotFoundException
        | IllegalArgumentException
        | SecurityException
        | URISyntaxException exception) {
      LOGGER.debug("Could not inspect application location for macOS runtime paths.", exception);
      return Optional.empty();
    }
  }

  private Optional<Path> jpackageAppPath() {
    String value = System.getProperty(JPACKAGE_APP_PATH_PROPERTY);
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(Path.of(value.trim()).toAbsolutePath().normalize());
  }

  private Optional<Path> javaHomePath() {
    String value = System.getProperty("java.home");
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(Path.of(value.trim()).toAbsolutePath().normalize());
  }

  private Optional<Path> findContentsDirectoryFromLauncher(Path launcherPath) {
    Path normalizedLauncher = launcherPath.toAbsolutePath().normalize();
    Path macosDirectory = normalizedLauncher.getParent();

    if (macosDirectory == null
        || macosDirectory.getFileName() == null
        || !"MacOS".equals(macosDirectory.getFileName().toString())) {
      return Optional.empty();
    }

    Path contentsDirectory = macosDirectory.getParent();
    if (contentsDirectory == null
        || contentsDirectory.getFileName() == null
        || !"Contents".equals(contentsDirectory.getFileName().toString())) {
      return Optional.empty();
    }

    return Optional.of(contentsDirectory.toAbsolutePath().normalize());
  }

  private Optional<Path> findJpackageAppDirectory(Path codeSourcePath) {
    Path cursor = Files.isDirectory(codeSourcePath) ? codeSourcePath : codeSourcePath.getParent();

    while (cursor != null) {
      if (cursor.getFileName() != null
          && "app".equals(cursor.getFileName().toString())
          && cursor.getParent() != null
          && "Contents".equals(cursor.getParent().getFileName().toString())) {
        return Optional.of(cursor.toAbsolutePath().normalize());
      }

      cursor = cursor.getParent();
    }

    return Optional.empty();
  }

  private Optional<Path> findContentsDirectoryFromJavaHome(Path javaHomePath) {
    Path cursor = javaHomePath.toAbsolutePath().normalize();

    while (cursor != null) {
      if (cursor.getFileName() != null
          && "Contents".equals(cursor.getFileName().toString())
          && Files.isDirectory(cursor.resolve("app"))) {
        return Optional.of(cursor);
      }

      cursor = cursor.getParent();
    }

    return Optional.empty();
  }

  private Optional<Path> findLauncherPath(Path contentsDirectory) {
    Path macosDirectory = contentsDirectory.resolve("MacOS");
    if (!Files.isDirectory(macosDirectory)) {
      return Optional.empty();
    }

    try (var launcherCandidates = Files.list(macosDirectory)) {
      return launcherCandidates
          .filter(Files::isRegularFile)
          .filter(Files::isExecutable)
          .findFirst()
          .map(Path::toAbsolutePath)
          .map(Path::normalize);
    } catch (Exception exception) {
      LOGGER.debug("Could not inspect macOS launcher directory.", exception);
      return Optional.empty();
    }
  }
}
