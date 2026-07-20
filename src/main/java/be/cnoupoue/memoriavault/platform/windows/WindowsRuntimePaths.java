package be.cnoupoue.memoriavault.platform.windows;

import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WindowsRuntimePaths {

  private static final Logger LOGGER = LoggerFactory.getLogger(WindowsRuntimePaths.class);
  private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";
  private static final String FFMPEG_BINARY = "ffmpeg.exe";

  public PlatformRuntimePaths detect() {
    return detect(codeSourcePath(), jpackageAppPath(), javaHomePath());
  }

  PlatformRuntimePaths detect(
      Optional<Path> codeSourcePath, Optional<Path> jpackageAppPath, Optional<Path> javaHomePath) {
    Optional<Path> installationDirectory =
        jpackageAppPath
            .map(path -> path.toAbsolutePath().normalize().getParent())
            .or(() -> codeSourcePath.flatMap(this::findInstallationDirectoryFromCodeSource))
            .or(() -> javaHomePath.flatMap(this::findInstallationDirectoryFromJavaHome));
    Optional<Path> appDirectory = installationDirectory.map(directory -> directory.resolve("app"));

    return new PlatformRuntimePaths(
        installationDirectory.filter(Files::isDirectory),
        jpackageAppPath.or(() -> installationDirectory.flatMap(this::findLauncherPath)),
        appDirectory.flatMap(this::findBundledFfmpegPath));
  }

  private Optional<Path> codeSourcePath() {
    try {
      URI codeSourceUri =
          WindowsRuntimePaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      return Optional.of(Path.of(codeSourceUri).toAbsolutePath().normalize());
    } catch (FileSystemNotFoundException
        | IllegalArgumentException
        | SecurityException
        | URISyntaxException exception) {
      LOGGER.debug("Could not inspect application location for Windows runtime paths.", exception);
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

  private Optional<Path> findInstallationDirectoryFromCodeSource(Path codeSourcePath) {
    Path cursor = Files.isDirectory(codeSourcePath) ? codeSourcePath : codeSourcePath.getParent();

    while (cursor != null) {
      if (cursor.getFileName() != null
          && "app".equalsIgnoreCase(cursor.getFileName().toString())
          && cursor.getParent() != null) {
        return Optional.of(cursor.getParent().toAbsolutePath().normalize());
      }

      cursor = cursor.getParent();
    }

    return Optional.empty();
  }

  private Optional<Path> findInstallationDirectoryFromJavaHome(Path javaHomePath) {
    Path normalizedJavaHome = javaHomePath.toAbsolutePath().normalize();

    if (normalizedJavaHome.getFileName() != null
        && "runtime".equalsIgnoreCase(normalizedJavaHome.getFileName().toString())
        && normalizedJavaHome.getParent() != null) {
      return Optional.of(normalizedJavaHome.getParent().toAbsolutePath().normalize());
    }

    return Optional.empty();
  }

  private Optional<Path> findLauncherPath(Path installationDirectory) {
    if (!Files.isDirectory(installationDirectory)) {
      return Optional.empty();
    }

    try (var launcherCandidates = Files.list(installationDirectory)) {
      return launcherCandidates
          .filter(Files::isRegularFile)
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".exe"))
          .findFirst()
          .map(Path::toAbsolutePath)
          .map(Path::normalize);
    } catch (Exception exception) {
      LOGGER.debug("Could not inspect Windows launcher directory.", exception);
      return Optional.empty();
    }
  }

  private Optional<Path> findBundledFfmpegPath(Path appDirectory) {
    Path nestedCandidate = appDirectory.resolve("ffmpeg").resolve(FFMPEG_BINARY);
    if (Files.isRegularFile(nestedCandidate)) {
      return Optional.of(nestedCandidate.toAbsolutePath().normalize());
    }

    Path flatCandidate = appDirectory.resolve(FFMPEG_BINARY);
    if (Files.isRegularFile(flatCandidate)) {
      return Optional.of(flatCandidate.toAbsolutePath().normalize());
    }

    return Optional.of(nestedCandidate.toAbsolutePath().normalize());
  }
}
