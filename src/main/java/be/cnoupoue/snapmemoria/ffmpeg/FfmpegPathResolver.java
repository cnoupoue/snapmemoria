package be.cnoupoue.snapmemoria.ffmpeg;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FfmpegPathResolver {

  private static final Logger LOGGER = LoggerFactory.getLogger(FfmpegPathResolver.class);
  private static final String FFMPEG_BINARY = "ffmpeg";

  private final String configuredPath;
  private final Supplier<Optional<Path>> bundledAppDirectorySupplier;
  private final String pathEnvironment;
  private volatile FfmpegResolution cachedResolution;

  @Autowired
  public FfmpegPathResolver(@Value("${snapmemoria.ffmpeg.path:}") String configuredPath) {
    this(configuredPath, FfmpegPathResolver::detectBundledAppDirectory, System.getenv("PATH"));
  }

  public FfmpegPathResolver(
      String configuredPath,
      Supplier<Optional<Path>> bundledAppDirectorySupplier,
      String pathEnvironment) {
    this.configuredPath = configuredPath;
    this.bundledAppDirectorySupplier = bundledAppDirectorySupplier;
    this.pathEnvironment = pathEnvironment;
  }

  public FfmpegResolution resolve() {
    FfmpegResolution resolution = cachedResolution;

    if (resolution != null) {
      return resolution;
    }

    resolution = resolveFresh();
    cachedResolution = resolution;

    LOGGER.info("{}", resolution.diagnosticMessage());

    return resolution;
  }

  void clearCache() {
    cachedResolution = null;
  }

  private FfmpegResolution resolveFresh() {
    Optional<Path> configuredCandidate = configuredCandidate();

    if (configuredCandidate.isPresent()) {
      return FfmpegResolution.available(
          configuredCandidate.get(), FfmpegSource.CONFIGURED, "Using configured FFmpeg.");
    }

    Optional<Path> bundledCandidate = bundledCandidate();

    if (bundledCandidate.isPresent()) {
      return FfmpegResolution.available(
          bundledCandidate.get(), FfmpegSource.BUNDLED, "Using bundled FFmpeg.");
    }

    Optional<Path> systemPathCandidate = systemPathCandidate();

    if (systemPathCandidate.isPresent()) {
      return FfmpegResolution.available(
          systemPathCandidate.get(), FfmpegSource.SYSTEM_PATH, "Using system FFmpeg.");
    }

    return FfmpegResolution.unavailable("Original videos can still be opened.");
  }

  private Optional<Path> configuredCandidate() {
    if (!StringUtils.hasText(configuredPath)) {
      return Optional.empty();
    }

    return executableCandidate(Path.of(configuredPath.trim()).toAbsolutePath().normalize());
  }

  private Optional<Path> bundledCandidate() {
    return bundledAppDirectorySupplier
        .get()
        .map(appDirectory -> appDirectory.resolve("ffmpeg").resolve(FFMPEG_BINARY))
        .flatMap(this::executableCandidate);
  }

  private Optional<Path> systemPathCandidate() {
    if (!StringUtils.hasText(pathEnvironment)) {
      return Optional.empty();
    }

    return Arrays.stream(pathEnvironment.split(File.pathSeparator))
        .filter(StringUtils::hasText)
        .map(directory -> Path.of(directory).resolve(FFMPEG_BINARY))
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .map(this::executableCandidate)
        .flatMap(Optional::stream)
        .findFirst();
  }

  private Optional<Path> executableCandidate(Path candidate) {
    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
      return Optional.of(candidate);
    }

    return Optional.empty();
  }

  private static Optional<Path> detectBundledAppDirectory() {
    try {
      URI codeSourceUri =
          FfmpegPathResolver.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      Path codeSourcePath = Path.of(codeSourceUri).toAbsolutePath().normalize();
      Path cursor = Files.isDirectory(codeSourcePath) ? codeSourcePath : codeSourcePath.getParent();

      while (cursor != null) {
        if (cursor.getFileName() != null
            && "app".equals(cursor.getFileName().toString())
            && cursor.getParent() != null
            && "Contents".equals(cursor.getParent().getFileName().toString())) {
          return Optional.of(cursor);
        }

        cursor = cursor.getParent();
      }
    } catch (IllegalArgumentException | SecurityException | URISyntaxException exception) {
      LOGGER.debug("Could not inspect application location for bundled FFmpeg.", exception);
    }

    return Optional.empty();
  }
}
