package be.cnoupoue.memoriavault.platform.macos;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacosRuntimePathsTest {

  private static final String JPACKAGE_APP_PATH_PROPERTY = "jpackage.app-path";

  @TempDir private Path temporaryDirectory;

  @Test
  void resolvesJpackageBundlePathsFromCodeSourceInsideContentsApp() throws Exception {
    Path appBundle = Files.createDirectories(temporaryDirectory.resolve("Memoria Vault.app"));
    Path contents = Files.createDirectories(appBundle.resolve("Contents"));
    Path launcher = executable(contents.resolve("MacOS/Memoria Vault"));
    Path appDirectory = Files.createDirectories(contents.resolve("app"));
    Path jar = Files.writeString(appDirectory.resolve("memoriavault.jar"), "jar");

    PlatformRuntimePaths paths = new MacosRuntimePaths().detect(Optional.of(jar));

    assertThat(paths.applicationBundlePath()).contains(appBundle);
    assertThat(paths.applicationLauncherPath()).contains(launcher);
    assertThat(paths.bundledFfmpegPath()).contains(appDirectory.resolve("ffmpeg/ffmpeg"));
  }

  @Test
  void resolvesJpackageBundlePathsFromLauncherPath() throws Exception {
    Path appBundle = Files.createDirectories(temporaryDirectory.resolve("Custom Name.app"));
    Path contents = Files.createDirectories(appBundle.resolve("Contents"));
    Path launcher = executable(contents.resolve("MacOS/Custom Launcher"));
    Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));

    PlatformRuntimePaths paths =
        new MacosRuntimePaths().detect(Optional.of(classesDirectory), Optional.of(launcher));

    assertThat(paths.applicationBundlePath()).contains(appBundle);
    assertThat(paths.applicationLauncherPath()).contains(launcher);
    assertThat(paths.bundledFfmpegPath()).contains(contents.resolve("app/ffmpeg/ffmpeg"));
  }

  @Test
  void detectsJpackageBundlePathsFromLauncherSystemProperty() throws Exception {
    Path appBundle = Files.createDirectories(temporaryDirectory.resolve("Property Name.app"));
    Path contents = Files.createDirectories(appBundle.resolve("Contents"));
    Path launcher = executable(contents.resolve("MacOS/Property Launcher"));
    String previousValue = System.getProperty(JPACKAGE_APP_PATH_PROPERTY);

    try {
      System.setProperty(JPACKAGE_APP_PATH_PROPERTY, launcher.toString());

      PlatformRuntimePaths paths = new MacosRuntimePaths().detect();

      assertThat(paths.applicationBundlePath()).contains(appBundle);
      assertThat(paths.applicationLauncherPath()).contains(launcher);
      assertThat(paths.bundledFfmpegPath()).contains(contents.resolve("app/ffmpeg/ffmpeg"));
    } finally {
      if (previousValue == null) {
        System.clearProperty(JPACKAGE_APP_PATH_PROPERTY);
      } else {
        System.setProperty(JPACKAGE_APP_PATH_PROPERTY, previousValue);
      }
    }
  }

  @Test
  void returnsEmptyPathsOutsideJpackageLayout() throws Exception {
    Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));

    PlatformRuntimePaths paths = new MacosRuntimePaths().detect(Optional.of(classesDirectory));

    assertThat(paths.applicationBundlePath()).isEmpty();
    assertThat(paths.applicationLauncherPath()).isEmpty();
    assertThat(paths.bundledFfmpegPath()).isEmpty();
  }

  private Path executable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path.toAbsolutePath().normalize();
  }
}
