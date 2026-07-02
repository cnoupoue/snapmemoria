package be.cnoupoue.snapmemoria.thumbnail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.cnoupoue.snapmemoria.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegResolution;
import be.cnoupoue.snapmemoria.ffmpeg.FfmpegSource;
import be.cnoupoue.snapmemoria.memory.SnapMemory;
import be.cnoupoue.snapmemoria.memory.SnapMemoryRepository;
import be.cnoupoue.snapmemoria.memory.SnapMemoryType;
import be.cnoupoue.snapmemoria.streaming.SecureMemoryPathResolver;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MemoryThumbnailServiceTest {

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @TempDir private Path temporaryDirectory;

  @Test
  void generatesImageThumbnailWithOverlayAndCachesIt() throws Exception {
    Path sourceImage = writeImage(temporaryDirectory.resolve("main.jpg"), 1200, 600, Color.BLUE);
    Path overlayImage = writeImage(temporaryDirectory.resolve("overlay.png"), 1200, 600, Color.RED);
    Path thumbnailDirectory = temporaryDirectory.resolve("thumbs");
    SnapMemory memory =
        memory("memory-1", SnapMemoryType.IMAGE, sourceImage.toString(), overlayImage.toString());
    FakeSecureMemoryPathResolver secureMemoryPathResolver = new FakeSecureMemoryPathResolver();
    MemoryThumbnailService service =
        service(
            thumbnailDirectory, 480, 480, secureMemoryPathResolver, unavailableFfmpegResolver());

    when(snapMemoryRepository.findById(memory.getId())).thenReturn(Optional.of(memory));
    secureMemoryPathResolver.register(memory.getSourceId(), memory.getMainPath(), sourceImage);
    secureMemoryPathResolver.register(memory.getSourceId(), memory.getOverlayPath(), overlayImage);

    var firstThumbnail = service.getThumbnail(memory.getId());
    var secondThumbnail = service.getThumbnail(memory.getId());

    assertThat(firstThumbnail.getFile()).exists();
    assertThat(secondThumbnail.getFile()).isEqualTo(firstThumbnail.getFile());

    BufferedImage generated = ImageIO.read(firstThumbnail.getFile());
    assertThat(generated.getWidth()).isEqualTo(480);
    assertThat(generated.getHeight()).isEqualTo(240);
    assertThat(secureMemoryPathResolver.resolveCount()).isEqualTo(2);
  }

  @Test
  void returnsCachedThumbnailWithoutResolvingOriginalMedia() throws Exception {
    Path thumbnailDirectory = Files.createDirectory(temporaryDirectory.resolve("thumbs"));
    Path cachedThumbnail =
        writeImage(thumbnailDirectory.resolve("memory-cached.jpg"), 20, 20, Color.GREEN);
    SnapMemory memory = memory("memory-cached", SnapMemoryType.IMAGE, "main.jpg", null);
    FakeSecureMemoryPathResolver secureMemoryPathResolver = new FakeSecureMemoryPathResolver();
    MemoryThumbnailService service =
        service(
            thumbnailDirectory, 480, 480, secureMemoryPathResolver, unavailableFfmpegResolver());

    when(snapMemoryRepository.findById(memory.getId())).thenReturn(Optional.of(memory));

    var thumbnail = service.getThumbnail(memory.getId());

    assertThat(thumbnail.getFile()).isEqualTo(cachedThumbnail.toFile());
    assertThat(secureMemoryPathResolver.resolveCount()).isZero();
  }

  @Test
  void throwsNotFoundWhenMemoryIsMissing() {
    MemoryThumbnailService service =
        service(
            temporaryDirectory.resolve("thumbs"),
            480,
            480,
            new FakeSecureMemoryPathResolver(),
            unavailableFfmpegResolver());

    when(snapMemoryRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getThumbnail("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  @Test
  void reportsUnsupportedImageFormatsAsUnavailable() throws Exception {
    Path invalidImage = Files.writeString(temporaryDirectory.resolve("main.jpg"), "not an image");
    SnapMemory memory =
        memory("memory-invalid", SnapMemoryType.IMAGE, invalidImage.toString(), null);
    FakeSecureMemoryPathResolver secureMemoryPathResolver = new FakeSecureMemoryPathResolver();
    MemoryThumbnailService service =
        service(
            temporaryDirectory.resolve("thumbs"),
            480,
            480,
            secureMemoryPathResolver,
            unavailableFfmpegResolver());

    when(snapMemoryRepository.findById(memory.getId())).thenReturn(Optional.of(memory));
    secureMemoryPathResolver.register(memory.getSourceId(), memory.getMainPath(), invalidImage);

    assertThatThrownBy(() -> service.getThumbnail(memory.getId()))
        .isInstanceOf(ThumbnailUnavailableException.class)
        .hasMessage("The original image format is not supported.");
  }

  @Test
  void videoThumbnailGenerationUsesResolvedFfmpegExecutable() throws Exception {
    Path sourceVideo = Files.writeString(temporaryDirectory.resolve("main.mp4"), "video");
    Path fakeFfmpeg = writeFakeFfmpeg(temporaryDirectory.resolve("ffmpeg"));
    Path thumbnailDirectory = temporaryDirectory.resolve("thumbs");
    SnapMemory memory = memory("memory-video", SnapMemoryType.VIDEO, sourceVideo.toString(), null);
    FakeSecureMemoryPathResolver secureMemoryPathResolver = new FakeSecureMemoryPathResolver();
    MemoryThumbnailService service =
        service(
            thumbnailDirectory,
            480,
            480,
            secureMemoryPathResolver,
            resolver(
                FfmpegResolution.available(
                    fakeFfmpeg, FfmpegSource.CONFIGURED, "Using configured FFmpeg.")));

    when(snapMemoryRepository.findById(memory.getId())).thenReturn(Optional.of(memory));
    secureMemoryPathResolver.register(memory.getSourceId(), memory.getMainPath(), sourceVideo);

    var thumbnail = service.getThumbnail(memory.getId());

    assertThat(thumbnail.getFile()).exists();
  }

  @Test
  void missingFfmpegReturnsVideoThumbnailUnavailable() throws Exception {
    Path sourceVideo = Files.writeString(temporaryDirectory.resolve("main.mp4"), "video");
    SnapMemory memory =
        memory("memory-video-unavailable", SnapMemoryType.VIDEO, sourceVideo.toString(), null);
    FakeSecureMemoryPathResolver secureMemoryPathResolver = new FakeSecureMemoryPathResolver();
    MemoryThumbnailService service =
        service(
            temporaryDirectory.resolve("thumbs"),
            480,
            480,
            secureMemoryPathResolver,
            unavailableFfmpegResolver());

    when(snapMemoryRepository.findById(memory.getId())).thenReturn(Optional.of(memory));
    secureMemoryPathResolver.register(memory.getSourceId(), memory.getMainPath(), sourceVideo);

    assertThatThrownBy(() -> service.getThumbnail(memory.getId()))
        .isInstanceOf(VideoThumbnailUnavailableException.class)
        .hasMessage(
            "Video preview generation is unavailable, but the original video can still be opened.");
  }

  private MemoryThumbnailService service(
      Path thumbnailDirectory,
      int maxWidth,
      int maxHeight,
      SecureMemoryPathResolver secureMemoryPathResolver,
      FfmpegPathResolver ffmpegPathResolver) {
    return new MemoryThumbnailService(
        snapMemoryRepository,
        secureMemoryPathResolver,
        ffmpegPathResolver,
        thumbnailDirectory.toString(),
        maxWidth,
        maxHeight,
        1);
  }

  private Path writeImage(Path path, int width, int height, Color color) throws Exception {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();

    try {
      graphics.setColor(color);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }

    ImageIO.write(image, path.toString().endsWith(".png") ? "png" : "jpg", path.toFile());

    return path;
  }

  private Path writeFakeFfmpeg(Path path) throws Exception {
    Files.writeString(
        path,
        "#!/bin/sh\n"
            + "output=\"\"\n"
            + "for arg in \"$@\"; do\n"
            + "  output=\"$arg\"\n"
            + "done\n"
            + "printf 'fake thumbnail' > \"$output\"\n"
            + "exit 0\n");
    path.toFile().setExecutable(true);

    return path.toAbsolutePath().normalize();
  }

  private SnapMemory memory(
      String id, SnapMemoryType mediaType, String mainPath, String overlayPath) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        "source-1",
        id + "-external",
        "2024-07-02",
        mediaType,
        mainPath,
        overlayPath,
        123,
        now,
        now,
        now);
  }

  private FfmpegPathResolver unavailableFfmpegResolver() {
    return resolver(FfmpegResolution.unavailable("Original videos can still be opened."));
  }

  private FfmpegPathResolver resolver(FfmpegResolution resolution) {
    return new FfmpegPathResolver("", Optional::empty, "") {
      @Override
      public FfmpegResolution resolve() {
        return resolution;
      }
    };
  }

  private static class FakeSecureMemoryPathResolver extends SecureMemoryPathResolver {

    private final Map<String, Path> paths = new HashMap<>();
    private int resolveCount;

    private FakeSecureMemoryPathResolver() {
      super(null);
    }

    private void register(String sourceId, String storedMediaPath, Path resolvedPath) {
      paths.put(sourceId + "|" + storedMediaPath, resolvedPath);
    }

    @Override
    public Path resolve(String sourceId, String storedMediaPath, String unavailableMessage) {
      resolveCount++;

      Path resolvedPath = paths.get(sourceId + "|" + storedMediaPath);

      if (resolvedPath == null) {
        throw new IllegalArgumentException("No fake path registered.");
      }

      return resolvedPath;
    }

    private int resolveCount() {
      return resolveCount;
    }
  }
}
