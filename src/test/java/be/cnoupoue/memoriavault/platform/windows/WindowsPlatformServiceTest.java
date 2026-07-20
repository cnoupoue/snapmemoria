package be.cnoupoue.memoriavault.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WindowsPlatformServiceTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void detectedAsWindowsPlatform() {
    WindowsPlatformService service = new WindowsPlatformService();
    assertThat(service.getPlatformType()).isEqualTo(PlatformType.WINDOWS);
  }

  @Test
  void enablesNativeFolderPickerCapability() {
    WindowsPlatformService service = new WindowsPlatformService();
    PlatformCapabilities capabilities = service.getCapabilities();

    assertThat(capabilities.nativeFolderPicker()).isTrue();
    assertThat(capabilities.applicationBundleDetection()).isTrue();
    assertThat(capabilities.bundledFfmpeg()).isTrue();
    assertThat(capabilities.desktopBrowserOpen()).isFalse();
  }

  @Test
  void resolvesExecutableBundledFfmpegFromRuntimePaths() throws Exception {
    Path installationDirectory =
        Files.createDirectories(temporaryDirectory.resolve("Memoria Vault"));
    Path launcher = executable(installationDirectory.resolve("Memoria Vault.exe"));
    Path ffmpeg = executable(installationDirectory.resolve("app/ffmpeg/ffmpeg.exe"));

    PlatformService service =
        new WindowsPlatformService(
            new StaticWindowsRuntimePaths(
                new PlatformRuntimePaths(
                    Optional.of(installationDirectory),
                    Optional.of(launcher),
                    Optional.of(ffmpeg))));

    assertThat(service.getPlatformType()).isEqualTo(PlatformType.WINDOWS);
    assertThat(service.resolveApplicationBundlePath()).contains(installationDirectory);
    assertThat(service.resolveApplicationLauncherPath()).contains(launcher);
    assertThat(service.resolveBundledFfmpegPath()).contains(ffmpeg);
    assertThat(service.getDiagnosticInfo().os()).isEqualTo("Windows");
    assertThat(service.getDiagnosticInfo().packaging()).isEqualTo("jpackage");
  }

  private Path executable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "@echo off\r\nexit /b 0\r\n");
    path.toFile().setExecutable(true);
    return path.toAbsolutePath().normalize();
  }

  private static class StaticWindowsRuntimePaths extends WindowsRuntimePaths {

    private final PlatformRuntimePaths paths;

    StaticWindowsRuntimePaths(PlatformRuntimePaths paths) {
      this.paths = paths;
    }

    @Override
    public PlatformRuntimePaths detect() {
      return paths;
    }
  }
}
