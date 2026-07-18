package be.cnoupoue.memoriavault.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MemoryIndexPersistenceTest {

  @Autowired private MemoryIndexPersistence memoryIndexPersistence;
  @Autowired private SnapMemoryRepository snapMemoryRepository;
  @Autowired private MemorySourceRepository memorySourceRepository;

  @BeforeEach
  void cleanDatabase() {
    snapMemoryRepository.deleteAll();
    memorySourceRepository.deleteAll();
  }

  @Test
  void synchronizesSourceMemoriesByStableMainPathAndPreservesFavorites() {
    MemorySource source = memorySourceRepository.save(source("source-1"));
    MemorySource otherSource = memorySourceRepository.save(source("source-2"));
    SnapMemory favoriteImage =
        snapMemoryRepository.save(
            memory(
                "favorite-image-row",
                source.getId(),
                "external-image",
                "2024-01-01",
                SnapMemoryType.IMAGE,
                "/stable/image.jpg",
                100,
                true,
                "2026-07-18T10:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "favorite-video-row",
            source.getId(),
            "external-video",
            "2023-01-01",
            SnapMemoryType.VIDEO,
            "/stable/video.mp4",
            200,
            true,
            "2026-07-18T09:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "missing-favorite-row",
            source.getId(),
            "external-missing",
            "2022-01-01",
            SnapMemoryType.IMAGE,
            "/old/missing.jpg",
            300,
            true,
            "2026-07-18T08:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "other-source-favorite-row",
            otherSource.getId(),
            "external-image",
            "2021-01-01",
            SnapMemoryType.IMAGE,
            "/other/image.jpg",
            400,
            true,
            "2026-07-18T07:00:00Z"));

    memoryIndexPersistence.synchronizeSourceMemories(
        source.getId(),
        List.of(
            memory(
                "new-random-image-row",
                source.getId(),
                "external-image",
                "2024-02-03",
                SnapMemoryType.IMAGE,
                "/stable/image.jpg",
                111,
                false,
                null),
            memory(
                "new-random-video-row",
                source.getId(),
                "external-video",
                "2023-02-03",
                SnapMemoryType.VIDEO,
                "/stable/video.mp4",
                222,
                false,
                null),
            memory(
                "new-memory-row",
                source.getId(),
                "external-new",
                "2025-02-03",
                SnapMemoryType.IMAGE,
                "/new/new.jpg",
                333,
                false,
                null)));

    List<SnapMemory> sourceMemories = snapMemoryRepository.findBySourceId(source.getId());

    assertThat(sourceMemories)
        .extracting(SnapMemory::getExternalMemoryId)
        .containsExactlyInAnyOrder("external-image", "external-video", "external-new");
    assertThat(snapMemoryRepository.findById("missing-favorite-row")).isEmpty();

    SnapMemory preservedImage = snapMemoryRepository.findById(favoriteImage.getId()).orElseThrow();
    assertThat(preservedImage.getExternalMemoryId()).isEqualTo("external-image");
    assertThat(preservedImage.getCapturedAt()).isEqualTo("2024-02-03");
    assertThat(preservedImage.getMainPath()).isEqualTo("/stable/image.jpg");
    assertThat(preservedImage.getFileSizeBytes()).isEqualTo(111);
    assertThat(preservedImage.isFavorite()).isTrue();
    assertThat(preservedImage.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");

    SnapMemory preservedVideo = snapMemoryRepository.findById("favorite-video-row").orElseThrow();
    assertThat(preservedVideo.getMainPath()).isEqualTo("/stable/video.mp4");
    assertThat(preservedVideo.isFavorite()).isTrue();
    assertThat(preservedVideo.getFavoritedAt()).isEqualTo("2026-07-18T09:00:00Z");

    SnapMemory newMemory =
        snapMemoryRepository.findBySourceId(source.getId()).stream()
            .filter(memory -> memory.getExternalMemoryId().equals("external-new"))
            .findFirst()
            .orElseThrow();
    assertThat(newMemory.isFavorite()).isFalse();
    assertThat(newMemory.getFavoritedAt()).isNull();

    SnapMemory otherSourceFavorite =
        snapMemoryRepository.findById("other-source-favorite-row").orElseThrow();
    assertThat(otherSourceFavorite.isFavorite()).isTrue();
    assertThat(otherSourceFavorite.getMainPath()).isEqualTo("/other/image.jpg");
  }

  @Test
  void doesNotInsertDuplicateRowsWhenExternalIdsAreDuplicatedAcrossDifferentFiles() {
    MemorySource source = memorySourceRepository.save(source("source-duplicate-external"));
    snapMemoryRepository.save(
        memory(
            "first-existing-row",
            source.getId(),
            "duplicated-external-id",
            "2024-01-01",
            SnapMemoryType.IMAGE,
            "/stable/first.jpg",
            100,
            true,
            "2026-07-18T10:00:00Z"));
    snapMemoryRepository.save(
        memory(
            "second-existing-row",
            source.getId(),
            "duplicated-external-id",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/stable/second.jpg",
            200,
            false,
            null));

    assertThatCode(
            () ->
                memoryIndexPersistence.synchronizeSourceMemories(
                    source.getId(),
                    List.of(
                        memory(
                            "first-new-row",
                            source.getId(),
                            "duplicated-external-id",
                            "2024-02-01",
                            SnapMemoryType.IMAGE,
                            "/stable/first.jpg",
                            101,
                            false,
                            null),
                        memory(
                            "second-new-row",
                            source.getId(),
                            "duplicated-external-id",
                            "2024-02-02",
                            SnapMemoryType.IMAGE,
                            "/stable/second.jpg",
                            202,
                            false,
                            null))))
        .doesNotThrowAnyException();

    List<SnapMemory> memories = snapMemoryRepository.findBySourceId(source.getId());

    assertThat(memories).hasSize(2);
    assertThat(snapMemoryRepository.findById("first-existing-row"))
        .get()
        .satisfies(
            memory -> {
              assertThat(memory.getCapturedAt()).isEqualTo("2024-02-01");
              assertThat(memory.getFileSizeBytes()).isEqualTo(101);
              assertThat(memory.isFavorite()).isTrue();
              assertThat(memory.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
            });
    assertThat(snapMemoryRepository.findById("second-existing-row"))
        .get()
        .satisfies(
            memory -> {
              assertThat(memory.getCapturedAt()).isEqualTo("2024-02-02");
              assertThat(memory.getFileSizeBytes()).isEqualTo(202);
              assertThat(memory.isFavorite()).isFalse();
            });
  }

  @Test
  void repeatedSynchronizationsAreIdempotentWhenNothingChanges() {
    MemorySource source = memorySourceRepository.save(source("source-idempotent"));
    List<SnapMemory> scannedMemories =
        List.of(
            memory(
                "first-scan-row",
                source.getId(),
                "external-one",
                "2024-01-01",
                SnapMemoryType.IMAGE,
                "/stable/one.jpg",
                100,
                false,
                null),
            memory(
                "second-scan-row",
                source.getId(),
                "external-two",
                "2024-01-02",
                SnapMemoryType.VIDEO,
                "/stable/two.mp4",
                200,
                false,
                null));

    memoryIndexPersistence.synchronizeSourceMemories(source.getId(), scannedMemories);
    List<String> idsAfterInitialScan =
        snapMemoryRepository.findBySourceId(source.getId()).stream()
            .map(SnapMemory::getId)
            .toList();

    assertThatCode(
            () -> {
              memoryIndexPersistence.synchronizeSourceMemories(source.getId(), scannedMemories);
              memoryIndexPersistence.synchronizeSourceMemories(source.getId(), scannedMemories);
            })
        .doesNotThrowAnyException();

    List<SnapMemory> memoriesAfterRescans = snapMemoryRepository.findBySourceId(source.getId());

    assertThat(memoriesAfterRescans).hasSize(2);
    assertThat(memoriesAfterRescans)
        .extracting(SnapMemory::getId)
        .containsExactlyInAnyOrderElementsOf(idsAfterInitialScan);
  }

  private MemorySource source(String id) {
    String now = Instant.now().toString();

    return new MemorySource(id, "Source " + id, "/tmp/" + id, null, "NOT_SCANNED", now, now);
  }

  private SnapMemory memory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
      long fileSizeBytes,
      boolean isFavorite,
      String favoritedAt) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        sourceId,
        externalMemoryId,
        capturedAt,
        mediaType,
        mainPath,
        null,
        fileSizeBytes,
        now,
        now,
        now,
        isFavorite,
        favoritedAt);
  }
}
