package be.cnoupoue.snapmemoria.ffmpeg;

import java.nio.file.Path;

public record FfmpegResolution(
    boolean available, Path executablePath, FfmpegSource source, String diagnosticMessage) {

  public static FfmpegResolution available(
      Path executablePath, FfmpegSource source, String diagnosticMessage) {
    return new FfmpegResolution(true, executablePath, source, diagnosticMessage);
  }

  public static FfmpegResolution unavailable(String diagnosticMessage) {
    return new FfmpegResolution(false, null, FfmpegSource.UNAVAILABLE, diagnosticMessage);
  }
}
