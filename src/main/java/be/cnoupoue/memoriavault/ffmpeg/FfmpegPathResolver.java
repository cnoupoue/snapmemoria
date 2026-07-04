package be.cnoupoue.memoriavault.ffmpeg;

import be.cnoupoue.memoriavault.platform.PlatformService;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
  private static final int VERSION_CHECK_TIMEOUT_SECONDS = 5;

  private final String configuredPath;
  private final Supplier<Optional<Path>> bundledFfmpegPathSupplier;
  private final String pathEnvironment;
  private final ExecutableValidator executableValidator;
  private volatile FfmpegResolution cachedResolution;

  @Autowired
  public FfmpegPathResolver(
      @Value("${memoriavault.ffmpeg.path:}") String configuredPath,
      PlatformService platformService) {
    this(configuredPath, platformService::resolveBundledFfmpegPath, System.getenv("PATH"));
  }

  public FfmpegPathResolver(
      String configuredPath,
      Supplier<Optional<Path>> bundledFfmpegPathSupplier,
      String pathEnvironment) {
    this(
        configuredPath,
        bundledFfmpegPathSupplier,
        pathEnvironment,
        FfmpegPathResolver::runsFfmpegVersion);
  }

  FfmpegPathResolver(
      String configuredPath,
      Supplier<Optional<Path>> bundledFfmpegPathSupplier,
      String pathEnvironment,
      ExecutableValidator executableValidator) {
    this.configuredPath = configuredPath;
    this.bundledFfmpegPathSupplier = bundledFfmpegPathSupplier;
    this.pathEnvironment = pathEnvironment;
    this.executableValidator = executableValidator;
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

    Optional<Path> bundledPath = bundledFfmpegPathSupplier.get();
    Optional<Path> bundledCandidate = bundledCandidate(bundledPath);

    if (bundledCandidate.isPresent()) {
      return FfmpegResolution.available(
          bundledCandidate.get(), FfmpegSource.BUNDLED, "Using bundled FFmpeg.");
    }

    Optional<Path> systemPathCandidate = systemPathCandidate();

    if (systemPathCandidate.isPresent()) {
      return FfmpegResolution.available(
          systemPathCandidate.get(), FfmpegSource.SYSTEM_PATH, "Using system FFmpeg.");
    }

    if (bundledPath.isPresent()) {
      return FfmpegResolution.unavailable(
          "Bundled video preview support could not start. Original videos can still be opened.");
    }

    return FfmpegResolution.unavailable("Original videos can still be opened.");
  }

  private Optional<Path> configuredCandidate() {
    if (!StringUtils.hasText(configuredPath)) {
      return Optional.empty();
    }

    Path configuredCandidate = Path.of(configuredPath.trim());
    if (!configuredCandidate.isAbsolute()) {
      return Optional.empty();
    }

    return executableCandidate(configuredCandidate, FfmpegSource.CONFIGURED);
  }

  private Optional<Path> bundledCandidate(Optional<Path> bundledPath) {
    return bundledPath.flatMap(candidate -> executableCandidate(candidate, FfmpegSource.BUNDLED));
  }

  private Optional<Path> systemPathCandidate() {
    if (!StringUtils.hasText(pathEnvironment)) {
      return Optional.empty();
    }

    return Arrays.stream(pathEnvironment.split(File.pathSeparator))
        .filter(StringUtils::hasText)
        .map(directory -> Path.of(directory).resolve(systemFfmpegBinaryName()))
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .map(candidate -> executableCandidate(candidate, FfmpegSource.SYSTEM_PATH))
        .flatMap(Optional::stream)
        .findFirst();
  }

  private String systemFfmpegBinaryName() {
    if (!StringUtils.hasText(configuredPath)) {
      return FFMPEG_BINARY;
    }

    Path configuredCandidate = Path.of(configuredPath.trim());
    if (configuredCandidate.isAbsolute() || configuredCandidate.getNameCount() != 1) {
      return FFMPEG_BINARY;
    }

    return configuredCandidate.toString();
  }

  private Optional<Path> executableCandidate(Path candidate, FfmpegSource source) {
    Path normalizedCandidate = candidate.toAbsolutePath().normalize();
    if (!Files.isRegularFile(normalizedCandidate) || !Files.isExecutable(normalizedCandidate)) {
      return Optional.empty();
    }

    if (!executableValidator.isValid(normalizedCandidate)) {
      LOGGER.warn("FFmpeg {} candidate failed executable validation.", source);
      return Optional.empty();
    }

    return Optional.of(normalizedCandidate);
  }

  private static boolean runsFfmpegVersion(Path candidate) {
    Process process = null;
    try {
      process =
          new ProcessBuilder(candidate.toString(), "-version")
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();

      if (!process.waitFor(VERSION_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return false;
      }

      return process.exitValue() == 0;
    } catch (IOException exception) {
      LOGGER.warn("FFmpeg candidate could not be started for executable validation.");
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @FunctionalInterface
  interface ExecutableValidator {
    boolean isValid(Path candidate);
  }
}
