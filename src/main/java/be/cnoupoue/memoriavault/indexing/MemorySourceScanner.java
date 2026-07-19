package be.cnoupoue.memoriavault.indexing;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import be.cnoupoue.memoriavault.source.SourceAvailability;
import be.cnoupoue.memoriavault.source.SourceAvailabilityService;
import be.cnoupoue.memoriavault.source.SourceUnavailableDuringScanException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MemorySourceScanner {

  private static final int PROGRESS_UPDATE_INTERVAL = 250;

  private static final Pattern SNAPCHAT_FILE_PATTERN =
      Pattern.compile(
          "^(?<date>\\d{4}-\\d{2}-\\d{2})_"
              + "(?<memoryId>[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12})-"
              + "(?<asset>main|overlay)\\."
              + "(?<extension>jpg|jpeg|png|mp4|mov)$",
          Pattern.CASE_INSENSITIVE);

  private final MemorySourceRepository memorySourceRepository;
  private final MemoryIndexPersistence memoryIndexPersistence;
  private final SourceAvailabilityService sourceAvailabilityService;

  public MemorySourceScanner(
      MemorySourceRepository memorySourceRepository,
      MemoryIndexPersistence memoryIndexPersistence,
      SourceAvailabilityService sourceAvailabilityService) {
    this.memorySourceRepository = memorySourceRepository;
    this.memoryIndexPersistence = memoryIndexPersistence;
    this.sourceAvailabilityService = sourceAvailabilityService;
  }

  public ScanProgress scan(String sourceId, Consumer<ScanProgress> progressListener) {
    MemorySource source =
        memorySourceRepository
            .findById(sourceId)
            .orElseThrow(() -> new IllegalArgumentException("Memory source not found."));

    Path rootPath = Path.of(source.getRootPath());

    ensureAvailable(rootPath);

    String startedAt = Instant.now().toString();

    source.markScanStarted(startedAt);
    memorySourceRepository.save(source);

    try {
      long totalFiles = countRegularFiles(rootPath);

      ScanCounters counters = new ScanCounters(totalFiles);

      progressListener.accept(counters.toProgress());

      ensureAvailable(rootPath);

      List<SnapMemory> scannedMemories = indexFiles(source, rootPath, counters, progressListener);
      memoryIndexPersistence.synchronizeSourceMemories(sourceId, scannedMemories);

      String completedAt = Instant.now().toString();

      source.markScanCompleted(completedAt);
      memorySourceRepository.save(source);

      progressListener.accept(counters.toProgress());

      return counters.toProgress();
    } catch (RuntimeException exception) {
      source.markScanFailed(Instant.now().toString());
      memorySourceRepository.save(source);

      throw exception;
    }
  }

  private void ensureAvailable(Path rootPath) {
    SourceAvailability availability = sourceAvailabilityService.check(rootPath);

    if (!availability.isAvailable()) {
      throw new SourceUnavailableDuringScanException();
    }
  }

  private long countRegularFiles(Path rootPath) {
    try (var paths = Files.walk(rootPath)) {
      return paths.filter(Files::isRegularFile).count();
    } catch (IOException exception) {
      throw new SourceUnavailableDuringScanException();
    }
  }

  private List<SnapMemory> indexFiles(
      MemorySource source,
      Path rootPath,
      ScanCounters counters,
      Consumer<ScanProgress> progressListener) {
    List<SnapMemory> scannedMemories = new ArrayList<>();

    try (var paths = Files.walk(rootPath)) {
      paths
          .filter(Files::isRegularFile)
          .forEach(path -> processFile(source, path, scannedMemories, counters, progressListener));
    } catch (IOException exception) {
      throw new SourceUnavailableDuringScanException();
    }

    return scannedMemories;
  }

  private void processFile(
      MemorySource source,
      Path filePath,
      List<SnapMemory> scannedMemories,
      ScanCounters counters,
      Consumer<ScanProgress> progressListener) {
    counters.filesProcessed++;

    ParsedSnapchatAsset asset = parseSnapchatAsset(filePath);

    if (asset == null) {
      counters.unsupportedFiles++;
      reportProgressIfNeeded(counters, progressListener);
      return;
    }

    if (asset.isOverlay()) {
      counters.overlays++;

      if (!hasSiblingMainFile(filePath)) {
        counters.unmatchedOverlays++;
      }

      reportProgressIfNeeded(counters, progressListener);
      return;
    }

    if (asset.mediaType() == SnapMemoryType.IMAGE) {
      counters.mainImages++;
    } else {
      counters.mainVideos++;
    }

    SnapMemory memory = createMemory(source, filePath, asset, counters);

    if (memory != null) {
      scannedMemories.add(memory);
      counters.indexedMemories++;

      if (memory.getOverlayPath() != null) {
        counters.attachedOverlays++;
      }

      reportProgressIfNeeded(counters, progressListener);
      return;
    }

    reportProgressIfNeeded(counters, progressListener);
  }

  private SnapMemory createMemory(
      MemorySource source, Path filePath, ParsedSnapchatAsset asset, ScanCounters counters) {
    try {
      String now = Instant.now().toString();
      Path overlayPath = findSiblingOverlay(filePath);

      return new SnapMemory(
          UUID.randomUUID().toString(),
          source.getId(),
          asset.memoryId(),
          asset.capturedAt(),
          asset.mediaType(),
          filePath.toAbsolutePath().normalize().toString(),
          overlayPath == null ? null : overlayPath.toString(),
          Files.size(filePath),
          Files.getLastModifiedTime(filePath).toInstant().toString(),
          now,
          now);
    } catch (IOException exception) {
      counters.unreadableFiles++;
      return null;
    }
  }

  private Path findSiblingOverlay(Path mainFilePath) {
    String mainFileName = mainFilePath.getFileName().toString();

    String overlayFileName =
        mainFileName.replaceFirst("(?i)-main\\.(jpg|jpeg|mp4|mov)$", "-overlay.png");

    if (overlayFileName.equals(mainFileName)) {
      return null;
    }

    Path candidate = mainFilePath.resolveSibling(overlayFileName).toAbsolutePath().normalize();

    return Files.isRegularFile(candidate) ? candidate : null;
  }

  private boolean hasSiblingMainFile(Path overlayFilePath) {
    String overlayFileName = overlayFilePath.getFileName().toString();

    String prefix = overlayFileName.replaceFirst("(?i)-overlay\\.png$", "");

    if (prefix.equals(overlayFileName)) {
      return false;
    }

    Path directory = overlayFilePath.getParent();

    return Files.isRegularFile(directory.resolve(prefix + "-main.jpg"))
        || Files.isRegularFile(directory.resolve(prefix + "-main.jpeg"))
        || Files.isRegularFile(directory.resolve(prefix + "-main.mp4"))
        || Files.isRegularFile(directory.resolve(prefix + "-main.mov"));
  }

  private ParsedSnapchatAsset parseSnapchatAsset(Path filePath) {
    String fileName = filePath.getFileName().toString().toLowerCase(Locale.ROOT);

    Matcher matcher = SNAPCHAT_FILE_PATTERN.matcher(fileName);

    if (!matcher.matches()) {
      return null;
    }

    String capturedAt = matcher.group("date");
    String memoryId = matcher.group("memoryId");
    String assetType = matcher.group("asset");
    String extension = matcher.group("extension");

    try {
      LocalDate.parse(capturedAt);
    } catch (RuntimeException exception) {
      return null;
    }

    if ("overlay".equals(assetType)) {
      if (!"png".equals(extension)) {
        return null;
      }

      return new ParsedSnapchatAsset(capturedAt, memoryId, true, null);
    }

    SnapMemoryType mediaType =
        switch (extension) {
          case "jpg", "jpeg" -> SnapMemoryType.IMAGE;
          case "mp4", "mov" -> SnapMemoryType.VIDEO;
          default -> null;
        };

    if (mediaType == null) {
      return null;
    }

    return new ParsedSnapchatAsset(capturedAt, memoryId, false, mediaType);
  }

  private void reportProgressIfNeeded(
      ScanCounters counters, Consumer<ScanProgress> progressListener) {
    if (counters.filesProcessed % PROGRESS_UPDATE_INTERVAL == 0
        || counters.filesProcessed == counters.totalFiles) {
      progressListener.accept(counters.toProgress());
    }
  }

  private record ParsedSnapchatAsset(
      String capturedAt, String memoryId, boolean isOverlay, SnapMemoryType mediaType) {}

  private static class ScanCounters {

    private final long totalFiles;

    private long filesProcessed;
    private long mainImages;
    private long mainVideos;
    private long overlays;
    private long indexedMemories;
    private long attachedOverlays;
    private long unmatchedOverlays;
    private long unsupportedFiles;
    private long unreadableFiles;

    private ScanCounters(long totalFiles) {
      this.totalFiles = totalFiles;
    }

    private ScanProgress toProgress() {
      return new ScanProgress(
          totalFiles,
          filesProcessed,
          mainImages,
          mainVideos,
          overlays,
          indexedMemories,
          attachedOverlays,
          unmatchedOverlays,
          unsupportedFiles,
          unreadableFiles);
    }
  }
}
