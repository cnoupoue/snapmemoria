package be.cnoupoue.memoriavault.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import be.cnoupoue.memoriavault.source.SourceAvailabilityService;
import be.cnoupoue.memoriavault.source.SourceUnavailableDuringScanException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySourceScannerTest {

  private static final String IMAGE_ID = "11111111-1111-1111-1111-111111111111";
  private static final String VIDEO_ID = "22222222-2222-2222-2222-222222222222";
  private static final String OVERLAY_ONLY_ID = "33333333-3333-3333-3333-333333333333";

  @Mock private MemorySourceRepository memorySourceRepository;
  @Mock private MemoryIndexPersistence memoryIndexPersistence;

  @TempDir private Path temporaryDirectory;

  @Test
  void scansSnapchatExportAndPersistsOnlyMainAssetsWithCounters() throws Exception {
    Path exportRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-export"));
    Path memories = Files.createDirectories(exportRoot.resolve("memories"));
    Path image =
        Files.writeString(memories.resolve("2024-07-02_" + IMAGE_ID + "-main.jpg"), "image");
    Path overlay =
        Files.writeString(memories.resolve("2024-07-02_" + IMAGE_ID + "-overlay.png"), "overlay");
    Path video =
        Files.writeString(memories.resolve("2023-01-05_" + VIDEO_ID + "-main.mp4"), "video");
    Files.writeString(memories.resolve("2023-01-05_" + OVERLAY_ONLY_ID + "-overlay.png"), "orphan");
    Files.writeString(memories.resolve("notes.txt"), "unsupported");

    MemorySource source = source("source-1", exportRoot);
    MemorySourceScanner scanner = scanner();
    List<ScanProgress> progressEvents = new ArrayList<>();
    ArgumentCaptor<List<SnapMemory>> batchCaptor = ArgumentCaptor.forClass(List.class);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(memorySourceRepository.save(any(MemorySource.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    ScanProgress result = scanner.scan(source.getId(), progressEvents::add);

    verify(memoryIndexPersistence)
        .synchronizeSourceMemories(eq(source.getId()), batchCaptor.capture());

    assertThat(batchCaptor.getValue()).hasSize(2);
    assertThat(batchCaptor.getValue())
        .extracting(SnapMemory::getExternalMemoryId)
        .containsExactlyInAnyOrder(IMAGE_ID, VIDEO_ID);
    assertThat(batchCaptor.getValue())
        .filteredOn(memory -> memory.getMediaType() == SnapMemoryType.IMAGE)
        .singleElement()
        .satisfies(
            memory -> {
              assertThat(memory.getMainPath())
                  .isEqualTo(image.toAbsolutePath().normalize().toString());
              assertThat(memory.getOverlayPath())
                  .isEqualTo(overlay.toAbsolutePath().normalize().toString());
            });
    assertThat(result.totalFiles()).isEqualTo(5);
    assertThat(result.filesProcessed()).isEqualTo(5);
    assertThat(result.mainImages()).isEqualTo(1);
    assertThat(result.mainVideos()).isEqualTo(1);
    assertThat(result.overlays()).isEqualTo(2);
    assertThat(result.indexedMemories()).isEqualTo(2);
    assertThat(result.attachedOverlays()).isEqualTo(1);
    assertThat(result.unmatchedOverlays()).isEqualTo(1);
    assertThat(result.unsupportedFiles()).isEqualTo(1);
    assertThat(result.unreadableFiles()).isZero();
    assertThat(progressEvents).isNotEmpty();
    assertThat(source.getLastScanStatus()).isEqualTo("COMPLETED");
  }

  @Test
  void refusesUnavailableRootBeforeMutatingIndexedData() {
    Path missingRoot = temporaryDirectory.resolve("missing");
    MemorySource source = source("source-missing", missingRoot);
    MemorySourceScanner scanner = scanner();

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    assertThatThrownBy(() -> scanner.scan(source.getId(), ignored -> {}))
        .isInstanceOf(SourceUnavailableDuringScanException.class);

    verify(memoryIndexPersistence, never()).deleteBySourceId(source.getId());
    verify(memoryIndexPersistence, never()).synchronizeSourceMemories(any(), any());
    verify(memorySourceRepository, never()).save(any(MemorySource.class));
    assertThat(source.getLastScanStatus()).isEqualTo("NOT_SCANNED");
  }

  private MemorySourceScanner scanner() {
    return new MemorySourceScanner(
        memorySourceRepository, memoryIndexPersistence, new SourceAvailabilityService());
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
