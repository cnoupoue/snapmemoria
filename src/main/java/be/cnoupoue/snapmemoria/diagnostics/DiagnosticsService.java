package be.cnoupoue.snapmemoria.diagnostics;

import be.cnoupoue.snapmemoria.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegResolution;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegSource;
import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import be.cnoupoue.snapmemoria.source.SourceAvailabilityService;
import be.cnoupoue.snapmemoria.source.SourceAvailabilityStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiagnosticsService {

  private static final String DEVELOPMENT_VERSION = "dev";

  private final FfmpegPathResolver ffmpegPathResolver;
  private final MemorySourceRepository memorySourceRepository;
  private final SourceAvailabilityService sourceAvailabilityService;
  private final Optional<BuildProperties> buildProperties;

  public DiagnosticsService(
      FfmpegPathResolver ffmpegPathResolver,
      MemorySourceRepository memorySourceRepository,
      SourceAvailabilityService sourceAvailabilityService,
      Optional<BuildProperties> buildProperties) {
    this.ffmpegPathResolver = ffmpegPathResolver;
    this.memorySourceRepository = memorySourceRepository;
    this.sourceAvailabilityService = sourceAvailabilityService;
    this.buildProperties = buildProperties;
  }

  @Transactional(readOnly = true)
  public DiagnosticsResponse getDiagnostics() {
    FfmpegResolution resolution = ffmpegPathResolver.resolve();
    SourceDiagnosticsResponse sources = sourceDiagnostics();

    return new DiagnosticsResponse(
        appVersion(),
        null,
        new VideoPreviewDiagnosticsResponse(
            resolution.available(),
            publicFfmpegSource(resolution.source()),
            resolution.diagnosticMessage()),
        sources,
        new DatabaseDiagnosticsResponse("READY"));
  }

  private String publicFfmpegSource(FfmpegSource source) {
    return source == FfmpegSource.SYSTEM_PATH ? "SYSTEM" : source.name();
  }

  private String appVersion() {
    return buildProperties
        .map(BuildProperties::getVersion)
        .filter(version -> !version.isBlank())
        .or(() -> Optional.ofNullable(getClass().getPackage().getImplementationVersion()))
        .filter(version -> !version.isBlank())
        .orElse(DEVELOPMENT_VERSION);
  }

  private SourceDiagnosticsResponse sourceDiagnostics() {
    List<MemorySource> sources = memorySourceRepository.findAll();
    int available = 0;

    for (MemorySource source : sources) {
      if (sourceAvailabilityService.check(source).status() == SourceAvailabilityStatus.AVAILABLE) {
        available++;
      }
    }

    int configured = sources.size();

    return new SourceDiagnosticsResponse(configured, available, configured - available);
  }
}
