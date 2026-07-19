package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.indexing.MemoryIndexPersistence;
import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.api.CreateMemorySourceRequest;
import be.cnoupoue.memoriavault.source.api.ImportFavoriteBackupMemoryRequest;
import be.cnoupoue.memoriavault.source.api.ImportFavoritesBackupRequest;
import be.cnoupoue.memoriavault.source.api.MemorySourceResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySourceServiceTest {

  @Mock private MemorySourceRepository memorySourceRepository;
  @Mock private MemoryIndexPersistence memoryIndexPersistence;
  @Mock private MemoryScanJobRepository memoryScanJobRepository;
  @Mock private SnapMemoryRepository snapMemoryRepository;

  @TempDir private Path temporaryDirectory;

  @Test
  void createsSourceWithNormalizedPathAndAvailability() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    Path rawPath = temporaryDirectory.resolve("snapchat-memories").resolve("..").resolve(".");
    String normalizedPath = rawPath.toAbsolutePath().normalize().toString();
    ArgumentCaptor<MemorySource> sourceCaptor = ArgumentCaptor.forClass(MemorySource.class);

    when(memorySourceRepository.existsByRootPath(normalizedPath)).thenReturn(false);
    when(memorySourceRepository.save(any(MemorySource.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MemorySourceResponse response =
        service.create(new CreateMemorySourceRequest(" Snapchat USB ", rawPath.toString()));

    verify(memorySourceRepository).save(sourceCaptor.capture());
    assertThat(sourceCaptor.getValue().getName()).isEqualTo("Snapchat USB");
    assertThat(sourceCaptor.getValue().getRootPath()).isEqualTo(normalizedPath);
    assertThat(sourceCaptor.getValue().getLastScanStatus()).isEqualTo("NOT_SCANNED");
    assertThat(response.rootPath()).isEqualTo(normalizedPath);
    assertThat(response.availabilityStatus()).isEqualTo("AVAILABLE");
  }

  @Test
  void rejectsDuplicateSourcePath() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    String normalizedPath = temporaryDirectory.toAbsolutePath().normalize().toString();

    when(memorySourceRepository.existsByRootPath(normalizedPath)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateMemorySourceRequest("Snapchat USB", temporaryDirectory.toString())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A source already exists for this path.");
  }

  @Test
  void returnsSourcesWithCurrentAvailability() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findAll()).thenReturn(List.of(source));
    when(snapMemoryRepository.countBySourceIdAndIsFavoriteTrue(source.getId())).thenReturn(2L);

    List<MemorySourceResponse> sources = service.findAll();

    assertThat(sources).hasSize(1);
    assertThat(sources.getFirst().availabilityStatus()).isEqualTo("AVAILABLE");
    assertThat(sources.getFirst().favoriteCount()).isEqualTo(2);
  }

  @Test
  void deletesSourceAndRelatedIndexedDataInSafeOrder() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-delete", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    service.delete(source.getId());

    InOrder inOrder =
        inOrder(memoryScanJobRepository, memoryIndexPersistence, memorySourceRepository);
    inOrder.verify(memoryScanJobRepository).deleteOrphaned();
    inOrder.verify(memoryIndexPersistence).deleteOrphaned();
    inOrder.verify(memoryScanJobRepository).deleteBySourceId(source.getId());
    inOrder.verify(memoryIndexPersistence).deleteBySourceId(source.getId());
    inOrder.verify(memorySourceRepository).deleteById(source.getId());
  }

  @Test
  void exportsFavoritesBackupWithStableIdentifiersAndFavoriteDates() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory favorite =
        memory(
            "memory-1",
            source.getId(),
            "external-1",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/export/memory.jpg",
            true,
            "2026-07-18T10:00:00Z");

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndIsFavoriteTrue(source.getId()))
        .thenReturn(List.of(favorite));

    var backup = service.exportFavoritesBackup(source.getId());

    assertThat(backup.version()).isEqualTo(1);
    assertThat(backup.source().id()).isEqualTo(source.getId());
    assertThat(backup.source().name()).isEqualTo(source.getName());
    assertThat(backup.sourceId()).isEqualTo(source.getId());
    assertThat(backup.favorites()).hasSize(1);
    assertThat(backup.favorites().getFirst().memoryId()).isEqualTo("memory-1");
    assertThat(backup.favorites().getFirst().externalMemoryId()).isEqualTo("external-1");
    assertThat(backup.favorites().getFirst().favoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
    assertThat(backup.favorites().getFirst().mainPath()).isEqualTo("/local/export/memory.jpg");
  }

  @Test
  void exportsEmptyFavoritesBackup() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndIsFavoriteTrue(source.getId()))
        .thenReturn(List.of());

    var backup = service.exportFavoritesBackup(source.getId());

    assertThat(backup.version()).isEqualTo(1);
    assertThat(backup.sourceId()).isEqualTo(source.getId());
    assertThat(backup.favorites()).isEmpty();
  }

  @Test
  void previewsFavoritesRestoreByExternalMemoryIdAndMainPathFallback() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory externalMatch =
        memory(
            "memory-external",
            source.getId(),
            "external-1",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/current/external.jpg",
            false,
            null);
    SnapMemory pathFallback =
        memory(
            "memory-path",
            source.getId(),
            "current-external",
            "2024-01-03",
            SnapMemoryType.VIDEO,
            "/local/current/path.mp4",
            true,
            "2026-07-18T09:00:00Z");

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "external-1"))
        .thenReturn(List.of(externalMatch));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "old-external"))
        .thenReturn(List.of());
    when(snapMemoryRepository.findBySourceIdAndMainPath(source.getId(), "/local/current/path.mp4"))
        .thenReturn(List.of(pathFallback));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "missing"))
        .thenReturn(List.of());
    when(snapMemoryRepository.findBySourceIdAndMainPath(source.getId(), "/missing.jpg"))
        .thenReturn(List.of());

    var preview =
        service.previewFavoritesRestore(
            source.getId(),
            backup(
                favorite("external-1", "/backup/external.jpg", "2026-07-18T10:00:00Z"),
                favorite("old-external", "/local/current/path.mp4", "2026-07-18T11:00:00Z"),
                favorite("missing", "/missing.jpg", "2026-07-18T12:00:00Z")));

    assertThat(preview.totalFavorites()).isEqualTo(3);
    assertThat(preview.restorable()).isEqualTo(2);
    assertThat(preview.restored()).isZero();
    assertThat(preview.alreadyFavorite()).isEqualTo(1);
    assertThat(preview.notFound()).isEqualTo(1);
  }

  @Test
  void restoresFavoritesSafelyAndKeepsExistingFavoritesUnchanged() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory newFavorite =
        memory(
            "memory-new",
            source.getId(),
            "external-new",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/new.jpg",
            false,
            null);
    SnapMemory existingFavorite =
        memory(
            "memory-existing",
            source.getId(),
            "external-existing",
            "2024-01-03",
            SnapMemoryType.VIDEO,
            "/local/existing.mp4",
            true,
            "2026-07-18T09:00:00Z");

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "external-new"))
        .thenReturn(List.of(newFavorite));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(
            source.getId(), "external-existing"))
        .thenReturn(List.of(existingFavorite));

    var summary =
        service.restoreFavoritesBackup(
            source.getId(),
            backup(
                favorite("external-new", "/backup/new.jpg", "2026-07-18T10:00:00Z"),
                favorite("external-existing", "/backup/existing.mp4", "2026-07-18T11:00:00Z")));

    assertThat(summary.totalFavorites()).isEqualTo(2);
    assertThat(summary.restorable()).isEqualTo(2);
    assertThat(summary.restored()).isEqualTo(1);
    assertThat(summary.alreadyFavorite()).isEqualTo(1);
    assertThat(summary.notFound()).isZero();
    assertThat(newFavorite.isFavorite()).isTrue();
    assertThat(newFavorite.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
    assertThat(existingFavorite.isFavorite()).isTrue();
    assertThat(existingFavorite.getFavoritedAt()).isEqualTo("2026-07-18T09:00:00Z");
  }

  @Test
  void restoringSameBackupTwiceIsIdempotentAndDuplicateEntriesAreCountedOnce() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory memory =
        memory(
            "memory-1",
            source.getId(),
            "external-1",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/memory.jpg",
            false,
            null);
    ImportFavoritesBackupRequest backup =
        backup(
            favorite("external-1", "/local/memory.jpg", "2026-07-18T10:00:00Z"),
            favorite("external-1", "/local/memory.jpg", "2026-07-18T10:00:00Z"));

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "external-1"))
        .thenReturn(List.of(memory));

    var firstSummary = service.restoreFavoritesBackup(source.getId(), backup);
    var secondSummary = service.restoreFavoritesBackup(source.getId(), backup);

    assertThat(firstSummary.totalFavorites()).isEqualTo(2);
    assertThat(firstSummary.restorable()).isEqualTo(1);
    assertThat(firstSummary.restored()).isEqualTo(1);
    assertThat(firstSummary.alreadyFavorite()).isZero();
    assertThat(secondSummary.restorable()).isEqualTo(1);
    assertThat(secondSummary.restored()).isZero();
    assertThat(secondSummary.alreadyFavorite()).isEqualTo(1);
    assertThat(memory.getFavoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
  }

  @Test
  void restoreUsesCurrentTimeWhenFavoritedAtIsMissing() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory memory =
        memory(
            "memory-1",
            source.getId(),
            "external-1",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/memory.jpg",
            false,
            null);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndExternalMemoryId(source.getId(), "external-1"))
        .thenReturn(List.of(memory));

    service.restoreFavoritesBackup(
        source.getId(), backup(favorite("external-1", "/local/memory.jpg", null)));

    assertThat(memory.isFavorite()).isTrue();
    assertThat(memory.getFavoritedAt()).isNotBlank();
  }

  @Test
  void rejectsUnsupportedBackupVersionWithoutModifyingFavorites() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    assertThatThrownBy(
            () ->
                service.restoreFavoritesBackup(
                    source.getId(),
                    new ImportFavoritesBackupRequest(2, null, null, source.getId(), List.of())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unsupported favorites backup version.");
  }

  @Test
  void rejectsBackupWithoutFavoritesArray() {
    MemorySourceService service = service();
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    assertThatThrownBy(
            () ->
                service.previewFavoritesRestore(
                    source.getId(),
                    new ImportFavoritesBackupRequest(1, null, null, source.getId(), null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Favorites backup must include a favorites array.");
  }

  private MemorySourceService service() {
    return new MemorySourceService(
        memorySourceRepository,
        new SourceAvailabilityService(),
        memoryIndexPersistence,
        memoryScanJobRepository,
        snapMemoryRepository);
  }

  private ImportFavoritesBackupRequest backup(ImportFavoriteBackupMemoryRequest... favorites) {
    return new ImportFavoritesBackupRequest(
        1, "2026-07-18T20:30:00Z", null, "source-1", List.of(favorites));
  }

  private ImportFavoriteBackupMemoryRequest favorite(
      String externalMemoryId, String mainPath, String favoritedAt) {
    return new ImportFavoriteBackupMemoryRequest(
        null, externalMemoryId, "2024-01-02", "IMAGE", mainPath, favoritedAt);
  }

  private MemorySource source(String id, Path rootPath) {
    String now = Instant.now().toString();

    return new MemorySource(
        id,
        "Snapchat USB",
        rootPath.toAbsolutePath().normalize().toString(),
        null,
        "NOT_SCANNED",
        now,
        now);
  }

  private SnapMemory memory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
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
        123,
        now,
        now,
        now,
        isFavorite,
        favoritedAt);
  }
}
