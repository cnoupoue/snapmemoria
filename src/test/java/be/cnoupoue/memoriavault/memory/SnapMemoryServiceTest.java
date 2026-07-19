package be.cnoupoue.memoriavault.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SnapMemoryServiceTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-07-18T10:15:30Z"), ZoneOffset.UTC);

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Test
  void findsAllWithValidatedPagingAndDefaultSort() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);
    SnapMemory memory = memory("memory-1", "2024-06-10", SnapMemoryType.IMAGE, null);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    when(snapMemoryRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(memory), PageRequest.of(0, 100), 1));

    var response = service.findAll(null, null, -5, 500);

    verify(snapMemoryRepository).findAll(pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("capturedAt").isDescending())
        .isTrue();
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().thumbnailUrl())
        .isEqualTo("/api/memories/memory-1/thumbnail");
    assertThat(response.content().getFirst().isFavorite()).isFalse();
    assertThat(response.content().getFirst().favoritedAt()).isNull();
  }

  @Test
  void filtersByYearAndMonthUsingCapturedAtPrefix() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);
    ArgumentCaptor<String> prefixCaptor = ArgumentCaptor.forClass(String.class);

    when(snapMemoryRepository.findByCapturedAtStartingWith(any(String.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.findAll(2024, 2, 0, 60);

    verify(snapMemoryRepository)
        .findByCapturedAtStartingWith(prefixCaptor.capture(), any(Pageable.class));
    assertThat(prefixCaptor.getValue()).isEqualTo("2024-02");
  }

  @Test
  void rejectsInvalidDateFilters() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);

    assertThatThrownBy(() -> service.findAll(null, 6, 0, 60))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A month filter requires a year filter.");
    assertThatThrownBy(() -> service.findAll(1999, null, 0, 60))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Year must be between 2000 and 2100.");
    assertThatThrownBy(() -> service.findAll(2024, 13, 0, 60))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Month must be between 1 and 12.");
  }

  @Test
  void mapsFlashbacksWithAnniversaryAgeAndExcludesCurrentYearInRepositoryQuery() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);
    SnapMemory memory = memory("memory-older", "2020-07-02", SnapMemoryType.VIDEO, "overlay.png");

    when(snapMemoryRepository.findFlashbacks("07-02", 2026)).thenReturn(List.of(memory));

    var response = service.findFlashbacks(LocalDate.of(2026, 7, 2));

    assertThat(response.date()).isEqualTo("2026-07-02");
    assertThat(response.memories()).hasSize(1);
    assertThat(response.memories().getFirst().year()).isEqualTo(2020);
    assertThat(response.memories().getFirst().yearsAgo()).isEqualTo(6);
    assertThat(response.memories().getFirst().hasOverlay()).isTrue();
  }

  @Test
  void returnsMemoryDetailsWithMediaAndOptionalOverlayUrls() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);

    when(snapMemoryRepository.findById("memory-detail"))
        .thenReturn(
            Optional.of(memory("memory-detail", "2021-05-01", SnapMemoryType.IMAGE, "overlay")));

    var response = service.findById("memory-detail");

    assertThat(response.mediaUrl()).isEqualTo("/api/memories/memory-detail/media");
    assertThat(response.overlayUrl()).isEqualTo("/api/memories/memory-detail/overlay");
    assertThat(response.hasOverlay()).isTrue();
    assertThat(response.isFavorite()).isFalse();
    assertThat(response.favoritedAt()).isNull();
  }

  @Test
  void throwsNotFoundWhenMemoryDetailsAreMissing() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);

    when(snapMemoryRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.findById("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  @Test
  void addsFavoriteAndReturnsUpdatedMemoryState() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository, FIXED_CLOCK);
    SnapMemory memory = memory("memory-1", "2024-06-10", SnapMemoryType.IMAGE, null);

    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));

    var response = service.addFavorite("memory-1");

    assertThat(response.isFavorite()).isTrue();
    assertThat(response.favoritedAt()).isEqualTo("2026-07-18T10:15:30Z");
    assertThat(memory.isFavorite()).isTrue();
    assertThat(memory.getFavoritedAt()).isEqualTo("2026-07-18T10:15:30Z");
  }

  @Test
  void addFavoriteIsIdempotentAndKeepsOriginalFavoriteDate() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository, FIXED_CLOCK);
    SnapMemory memory =
        memory("memory-1", "2024-06-10", SnapMemoryType.IMAGE, null, true, "2026-07-01T00:00:00Z");

    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));

    var response = service.addFavorite("memory-1");

    assertThat(response.isFavorite()).isTrue();
    assertThat(response.favoritedAt()).isEqualTo("2026-07-01T00:00:00Z");
  }

  @Test
  void removesFavoriteAndReturnsUpdatedMemoryState() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository, FIXED_CLOCK);
    SnapMemory memory =
        memory("memory-1", "2024-06-10", SnapMemoryType.IMAGE, null, true, "2026-07-01T00:00:00Z");

    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));

    var response = service.removeFavorite("memory-1");

    assertThat(response.isFavorite()).isFalse();
    assertThat(response.favoritedAt()).isNull();
    assertThat(memory.isFavorite()).isFalse();
    assertThat(memory.getFavoritedAt()).isNull();
  }

  @Test
  void removeFavoriteIsIdempotent() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository, FIXED_CLOCK);
    SnapMemory memory = memory("memory-1", "2024-06-10", SnapMemoryType.IMAGE, null);

    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));

    var response = service.removeFavorite("memory-1");

    assertThat(response.isFavorite()).isFalse();
    assertThat(response.favoritedAt()).isNull();
  }

  @Test
  void listsFavoritesWithMemoryDateNewestFirstSortAndStableFallbacks() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository);
    SnapMemory favorite =
        memory(
            "favorite-1", "2024-06-10", SnapMemoryType.IMAGE, null, true, "2026-07-18T10:15:30Z");
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);

    when(snapMemoryRepository.findFavorites(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(favorite), PageRequest.of(0, 60), 1));

    var response = service.findFavorites(-2, 500);

    verify(snapMemoryRepository).findFavorites(pageableCaptor.capture());
    assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
    assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("capturedAt").isDescending())
        .isTrue();
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("lastModifiedAt").isDescending())
        .isTrue();
    assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt").isDescending())
        .isTrue();
    assertThat(response.content()).hasSize(1);
    assertThat(response.content().getFirst().isFavorite()).isTrue();
    assertThat(response.content().getFirst().favoritedAt()).isEqualTo("2026-07-18T10:15:30Z");
  }

  @Test
  void throwsNotFoundWhenFavoritingMissingMemory() {
    SnapMemoryService service = new SnapMemoryService(snapMemoryRepository, FIXED_CLOCK);

    when(snapMemoryRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.addFavorite("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("404 NOT_FOUND");
  }

  private SnapMemory memory(
      String id, String capturedAt, SnapMemoryType mediaType, String overlayPath) {
    return memory(id, capturedAt, mediaType, overlayPath, false, null);
  }

  private SnapMemory memory(
      String id,
      String capturedAt,
      SnapMemoryType mediaType,
      String overlayPath,
      boolean isFavorite,
      String favoritedAt) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        "source-1",
        id + "-external",
        capturedAt,
        mediaType,
        "/memories/" + id,
        overlayPath,
        123,
        now,
        now,
        now,
        isFavorite,
        favoritedAt);
  }
}
