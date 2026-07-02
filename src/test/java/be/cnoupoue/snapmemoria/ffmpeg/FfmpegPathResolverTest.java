package be.cnoupoue.snapmemoria.ffmpeg;

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
    Path bundledFfmpeg = executable(temporaryDirectory.resolve("app/ffmpeg/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver(
                configuredFfmpeg.toString(),
                () -> Optional.of(bundledFfmpeg.getParent().getParent()),
                "")
            .resolve();

    assertThat(resolution.available()).isTrue();
    assertThat(resolution.executablePath()).isEqualTo(configuredFfmpeg);
    assertThat(resolution.source()).isEqualTo(FfmpegSource.CONFIGURED);
  }

  @Test
  void bundledFfmpegIsUsedWhenConfiguredPathIsAbsent() throws Exception {
    Path bundledAppDirectory = Files.createDirectories(temporaryDirectory.resolve("app"));
    Path bundledFfmpeg = executable(bundledAppDirectory.resolve("ffmpeg/ffmpeg"));

    FfmpegResolution resolution =
        new FfmpegPathResolver("", () -> Optional.of(bundledAppDirectory), "").resolve();

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
  void unavailableFfmpegReturnsSafeResolution() {
    FfmpegResolution resolution = new FfmpegPathResolver("", Optional::empty, "").resolve();

    assertThat(resolution.available()).isFalse();
    assertThat(resolution.executablePath()).isNull();
    assertThat(resolution.source()).isEqualTo(FfmpegSource.UNAVAILABLE);
    assertThat(resolution.diagnosticMessage()).isEqualTo("Original videos can still be opened.");
  }

  private Path executable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path.toAbsolutePath().normalize();
  }
}
