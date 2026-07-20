package be.cnoupoue.memoriavault.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsRuntimePathsTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void resolvesJpackagePathsFromCodeSourceInsideAppDirectory() throws Exception {
    Path installationDirectory =
        Files.createDirectories(temporaryDirectory.resolve("Memoria Vault"));
    Path appDirectory = Files.createDirectories(installationDirectory.resolve("app"));
    Path jar = Files.writeString(appDirectory.resolve("memoriavault.jar"), "jar");
    Path launcher = Files.writeString(installationDirectory.resolve("Memoria Vault.exe"), "exe");
    Path ffmpeg = writeFile(appDirectory.resolve("ffmpeg/ffmpeg.exe"), "ffmpeg");

    PlatformRuntimePaths paths =
        new WindowsRuntimePaths().detect(Optional.of(jar), Optional.empty(), Optional.empty());

    assertThat(paths.applicationBundlePath()).contains(installationDirectory);
    assertThat(paths.applicationLauncherPath()).contains(launcher);
    assertThat(paths.bundledFfmpegPath()).contains(ffmpeg);
  }

  @Test
  void resolvesJpackagePathsFromLauncherPath() throws Exception {
    Path installationDirectory = Files.createDirectories(temporaryDirectory.resolve("Custom App"));
    Path appDirectory = Files.createDirectories(installationDirectory.resolve("app"));
    Path launcher = Files.writeString(installationDirectory.resolve("Custom App.exe"), "exe");
    Path ffmpeg = writeFile(appDirectory.resolve("ffmpeg/ffmpeg.exe"), "ffmpeg");

    PlatformRuntimePaths paths =
        new WindowsRuntimePaths().detect(Optional.empty(), Optional.of(launcher), Optional.empty());

    assertThat(paths.applicationBundlePath()).contains(installationDirectory);
    assertThat(paths.applicationLauncherPath()).contains(launcher);
    assertThat(paths.bundledFfmpegPath()).contains(ffmpeg);
  }

  @Test
  void resolvesJpackagePathsFromBundledJavaHome() throws Exception {
    Path installationDirectory =
        Files.createDirectories(temporaryDirectory.resolve("Java Home App"));
    Path runtimeHome = Files.createDirectories(installationDirectory.resolve("runtime"));
    Path launcher = Files.writeString(installationDirectory.resolve("Java Home App.exe"), "exe");
    Path ffmpeg = writeFile(installationDirectory.resolve("app/ffmpeg/ffmpeg.exe"), "ffmpeg");

    PlatformRuntimePaths paths =
        new WindowsRuntimePaths()
            .detect(Optional.empty(), Optional.empty(), Optional.of(runtimeHome));

    assertThat(paths.applicationBundlePath()).contains(installationDirectory);
    assertThat(paths.applicationLauncherPath()).contains(launcher);
    assertThat(paths.bundledFfmpegPath()).contains(ffmpeg);
  }

  @Test
  void supportsFlatFfmpegLayoutForExistingWindowsPackages() throws Exception {
    Path installationDirectory = Files.createDirectories(temporaryDirectory.resolve("Flat App"));
    Path appDirectory = Files.createDirectories(installationDirectory.resolve("app"));
    Path jar = Files.writeString(appDirectory.resolve("memoriavault.jar"), "jar");
    Path ffmpeg = writeFile(appDirectory.resolve("ffmpeg.exe"), "ffmpeg");

    PlatformRuntimePaths paths =
        new WindowsRuntimePaths().detect(Optional.of(jar), Optional.empty(), Optional.empty());

    assertThat(paths.bundledFfmpegPath()).contains(ffmpeg);
  }

  @Test
  void returnsEmptyPathsOutsideJpackageLayout() throws Exception {
    Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));

    PlatformRuntimePaths paths =
        new WindowsRuntimePaths()
            .detect(Optional.of(classesDirectory), Optional.empty(), Optional.empty());

    assertThat(paths.applicationBundlePath()).isEmpty();
    assertThat(paths.applicationLauncherPath()).isEmpty();
    assertThat(paths.bundledFfmpegPath()).isEmpty();
  }

  private Path writeFile(Path path, String content) throws Exception {
    Files.createDirectories(path.getParent());
    return Files.writeString(path, content).toAbsolutePath().normalize();
  }
}
