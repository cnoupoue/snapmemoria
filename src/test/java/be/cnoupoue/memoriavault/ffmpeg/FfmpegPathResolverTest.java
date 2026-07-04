package be.cnoupoue.memoriavault.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FfmpegPathResolverTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void configuredFfmpegPathTakesPriority() throws Exception {
    Path configuredFfmpeg = executable(temporaryDirectory.resolve("configured-ffmpeg"));
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver(configuredFfmpeg.toString(), () -> Optional.of(bundledFfmpeg), "")
            .resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(configuredFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.CONFIGURED);
  }

  @Test
  void bundledFfmpegIsUsedWhenConfiguredPathIsAbsent() throws Exception {
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("", () -> Optional.of(bundledFfmpeg), "").resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(bundledFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.BUNDLED);
  }

  @Test
  void bundledFfmpegIsUsedBeforeSystemPathFallback() throws Exception {
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));
    Path binDirectory = Files.createDirectories(temporaryDirectory.resolve("bin"));
    executable(binDirectory.resolve("ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("", () -> Optional.of(bundledFfmpeg), binDirectory.toString())
            .resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(bundledFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.BUNDLED);
  }

  @Test
  void plainFfmpegConfigurationDoesNotBlockBundledFfmpeg() throws Exception {
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("ffmpeg", () -> Optional.of(bundledFfmpeg), "").resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(bundledFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.BUNDLED);
  }

  @Test
  void systemPathFfmpegIsUsedWhenBundledFfmpegIsAbsent() throws Exception {
    Path binDirectory = Files.createDirectories(temporaryDirectory.resolve("bin"));
    Path systemFfmpeg = executable(binDirectory.resolve("ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("", Optional::empty, binDirectory.toString()).resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(systemFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.SYSTEM_PATH);
    assertThat(resolution.diagnosticMessage()).isEqualTo("Using system FFmpeg.");
  }

  @Test
  void plainFfmpegConfigurationCanBeUsedAsSystemFallback() throws Exception {
    Path binDirectory = Files.createDirectories(temporaryDirectory.resolve("bin"));
    Path systemFfmpeg = executable(binDirectory.resolve("ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("ffmpeg", Optional::empty, binDirectory.toString()).resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(systemFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.SYSTEM_PATH);
  }

  @Test
  void nonExecutableBundledFfmpegIsRejectedSafely() throws Exception {
    Path bundledFfmpeg = nonExecutable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("", () -> Optional.of(bundledFfmpeg), "").resolve();

    assertThat(resolution.available()).isFalse();
    assertThat(resolution.executablePath()).isNull();
    assertThat(resolution.source()).isEqualTo(FfmpegSource.UNAVAILABLE);
    assertThat(resolution.diagnosticMessage())
        .isEqualTo(
            "Bundled video preview support could not start. Original videos can still be opened.");
  }

  @Test
  void bundledFfmpegThatFailsVersionValidationFallsBackToSystemPath() throws Exception {
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("platform-bundle/bin/ffmpeg"));
    Path binDirectory = Files.createDirectories(temporaryDirectory.resolve("bin"));
    Path systemFfmpeg = executable(binDirectory.resolve("ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver(
                "",
                () -> Optional.of(bundledFfmpeg),
                binDirectory.toString(),
                candidate -> candidate.equals(systemFfmpeg))
            .resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(systemFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.SYSTEM_PATH);
  }

  @Test
  void unavailableFfmpegReturnsSafeResolution() {
    FfmpegResolution resolution = new FfmpegPathResolver("", Optional::empty, "").resolve();

    assertThat(resolution.available()).isFalse();
    assertThat(resolution.executablePath()).isNull();
    assertThat(resolution.source()).isEqualTo(FfmpegSource.UNAVAILABLE);
    assertThat(resolution.diagnosticMessage()).isEqualTo("Original videos can still be opened.");
  }

  @Test
  void resolverDoesNotContainMacosBundleAssumptions() throws Exception {
    String resolverSource =
        Files.readString(
            Path.of("src/main/java/be/cnoupoue/memoriavault/ffmpeg/FfmpegPathResolver.java"));

    assertThat(resolverSource)
        .doesNotContain("Contents")
        .doesNotContain(".app")
        .doesNotContain("MacOS");
  }

  private Path executable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path.toAbsolutePath().normalize();
  }

  private Path nonExecutable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(false);
    return path.toAbsolutePath().normalize();
  }
}
