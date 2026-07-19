package be.cnoupoue.memoriavault.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MemorySourceScannerIntegrationTest {

  private static final String IMAGE_ID = "11111111-1111-1111-1111-111111111111";
  private static final String VIDEO_ID = "22222222-2222-2222-2222-222222222222";
  private static final String NEW_IMAGE_ID = "33333333-3333-3333-3333-333333333333";

  @Autowired private MemorySourceScanner memorySourceScanner;
  @Autowired private MemorySourceRepository memorySourceRepository;
  @Autowired private SnapMemoryRepository snapMemoryRepository;

  @TempDir private Path temporaryDirectory;

  @BeforeEach
  void cleanDatabase() {
    snapMemoryRepository.deleteAll();
    memorySourceRepository.deleteAll();
  }

  @Test
  void rescansAreIdempotentAndOnlyAddOrRemoveChangedFiles() throws Exception {
    Path exportRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-export"));
    Path memoriesDirectory = Files.createDirectories(exportRoot.resolve("memories"));
    Path imagePath =
        Files.writeString(
            memoriesDirectory.resolve("2024-07-02_" + IMAGE_ID + "-main.jpg"), "image");
    Path videoPath =
        Files.writeString(
            memoriesDirectory.resolve("2023-01-05_" + VIDEO_ID + "-main.mp4"), "video");
    MemorySource source = memorySourceRepository.save(source("source-1", exportRoot));
    List<ScanProgress> progressEvents = new ArrayList<>();

    memorySourceScanner.scan(source.getId(), progressEvents::add);

    SnapMemory favoriteImage =
        snapMemoryRepository.findBySourceId(source.getId()).stream()
            .filter(memory -> memory.getMediaType() == SnapMemoryType.IMAGE)
            .findFirst()
            .orElseThrow();
    favoriteImage.markFavorite("2026-07-18T10:00:00Z");
    snapMemoryRepository.save(favoriteImage);
    String favoriteImageRowId = favoriteImage.getId();

    assertThatCode(
            () -> {
              memorySourceScanner.scan(source.getId(), ignored -> {});
              memorySourceScanner.scan(source.getId(), ignored -> {});
            })
        .doesNotThrowAnyException();

    assertThat(snapMemoryRepository.findBySourceId(source.getId())).hasSize(2);
    assertThat(snapMemoryRepository.findById(favoriteImageRowId))
        .get()
        .satisfies(
            memory -> {
              assertThat(memory.isFavorite()).isTrue();
              assertThat(memory.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
              assertThat(memory.getMainPath())
                  .isEqualTo(imagePath.toAbsolutePath().normalize().toString());
            });

    Files.writeString(
        memoriesDirectory.resolve("2025-02-03_" + NEW_IMAGE_ID + "-main.jpg"), "new-image");
    memorySourceScanner.scan(source.getId(), ignored -> {});

    assertThat(snapMemoryRepository.findBySourceId(source.getId()))
        .extracting(SnapMemory::getExternalMemoryId)
        .containsExactlyInAnyOrder(IMAGE_ID, VIDEO_ID, NEW_IMAGE_ID);

    Files.delete(videoPath);
    memorySourceScanner.scan(source.getId(), ignored -> {});

    assertThat(snapMemoryRepository.findBySourceId(source.getId()))
        .extracting(SnapMemory::getExternalMemoryId)
        .containsExactlyInAnyOrder(IMAGE_ID, NEW_IMAGE_ID);
    assertThat(snapMemoryRepository.findById(favoriteImageRowId))
        .get()
        .satisfies(
            memory -> {
              assertThat(memory.isFavorite()).isTrue();
              assertThat(memory.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
            });
  }

  private MemorySource source(String id, Path rootPath) {
    String now = Instant.now().toString();

    return new MemorySource(
        id,
        "Snapchat Export",
        rootPath.toAbsolutePath().normalize().toString(),
        null,
        "NOT_SCANNED",
        now,
        now);
  }
}
