package be.cnoupoue.snapmemoria.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import be.cnoupoue.snapmemoria.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegResolution;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegSource;
import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import be.cnoupoue.snapmemoria.source.SourceAvailability;
import be.cnoupoue.snapmemoria.source.SourceAvailabilityService;
import be.cnoupoue.snapmemoria.source.SourceAvailabilityStatus;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

class DiagnosticsControllerTest {

  private static final Path LOCAL_FFMPEG_PATH =
      Path.of("/Users/cameron/private-support-test/bin/ffmpeg");
  private static final MemorySource AVAILABLE_SOURCE =
      new MemorySource(
          "source-1",
          "Family archive USB",
          "/Volumes/FAMILY_USB/snapchat-export",
          null,
          "NOT_SCANNED",
          "2026-01-01T00:00:00Z",
          "2026-01-01T00:00:00Z");
  private static final MemorySource UNAVAILABLE_SOURCE =
      new MemorySource(
          "source-2",
          "Vacation archive",
          "/Users/cameron/Pictures/vacation-export",
          null,
          "NOT_SCANNED",
          "2026-01-01T00:00:00Z",
          "2026-01-01T00:00:00Z");

  private FfmpegPathResolver ffmpegPathResolver;
  private MemorySourceRepository memorySourceRepository;
  private SourceAvailabilityService sourceAvailabilityService;

  @BeforeEach
  void setUp() {
    ffmpegPathResolver = mock(FfmpegPathResolver.class);
    memorySourceRepository = mock(MemorySourceRepository.class);
    sourceAvailabilityService = mock(SourceAvailabilityService.class);

    when(ffmpegPathResolver.resolve())
        .thenReturn(
            FfmpegResolution.available(
                LOCAL_FFMPEG_PATH, FfmpegSource.BUNDLED, "Using bundled FFmpeg."));
    when(memorySourceRepository.findAll())
        .thenReturn(List.of(AVAILABLE_SOURCE, UNAVAILABLE_SOURCE));
    when(sourceAvailabilityService.check(AVAILABLE_SOURCE))
        .thenReturn(
            new SourceAvailability(
                SourceAvailabilityStatus.AVAILABLE, "Source folder is available."));
    when(sourceAvailabilityService.check(UNAVAILABLE_SOURCE))
        .thenReturn(
            new SourceAvailability(
                SourceAvailabilityStatus.UNAVAILABLE,
                "Connect the drive containing this source, then refresh its status."));
  }

  @Test
  void diagnosticsResponseIncludesAppVersion() {
    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.appVersion()).isEqualTo("0.1.0");
  }

  @Test
  void ffmpegDiagnosticsExposeSourceCategoryButNotExecutablePath() {
    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.videoPreviews().available()).isTrue();
    assertThat(response.videoPreviews().source()).isEqualTo("BUNDLED");
    assertThat(response.videoPreviews().message()).isEqualTo("Using bundled FFmpeg.");
    assertThat(response.toString()).doesNotContain(LOCAL_FFMPEG_PATH.toString());
  }

  @Test
  void systemFfmpegDiagnosticsUsePublicSourceCategory() {
    when(ffmpegPathResolver.resolve())
        .thenReturn(
            FfmpegResolution.available(
                LOCAL_FFMPEG_PATH, FfmpegSource.SYSTEM_PATH, "Using system FFmpeg."));

    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.videoPreviews().source()).isEqualTo("SYSTEM");
    assertThat(response.toString()).doesNotContain("SYSTEM_PATH");
  }

  @Test
  void sourceDiagnosticsExposeCountsOnly() {
    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.sources().configured()).isEqualTo(2);
    assertThat(response.sources().available()).isEqualTo(1);
    assertThat(response.sources().unavailable()).isEqualTo(1);
  }

  @Test
  void diagnosticsDoNotIncludeSourceNamesOrLocalPaths() {
    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.toString())
        .doesNotContain("Family archive USB")
        .doesNotContain("Vacation archive")
        .doesNotContain("/Volumes/FAMILY_USB")
        .doesNotContain("/Users/cameron")
        .doesNotContain("snapchat-export")
        .doesNotContain("vacation-export");
  }

  @Test
  void databaseStatusIsReturnedSafely() {
    DiagnosticsResponse response = service("0.1.0").getDiagnostics();

    assertThat(response.database().status()).isEqualTo("READY");
  }

  @Test
  void diagnosticsEndpointReturnsStableResponseShape() {
    DiagnosticsResponse response = new DiagnosticsController(service("0.1.0")).getDiagnostics();

    assertThat(response.appVersion()).isNotBlank();
    assertThat(response.platform()).isNull();
    assertThat(response.videoPreviews()).isNotNull();
    assertThat(response.sources()).isNotNull();
    assertThat(response.database()).isNotNull();
  }

  private DiagnosticsService service(String version) {
    Properties properties = new Properties();
    properties.setProperty("version", version);

    return new DiagnosticsService(
        ffmpegPathResolver,
        memorySourceRepository,
        sourceAvailabilityService,
        Optional.of(new BuildProperties(properties)));
  }
}
