package be.cnoupoue.memoriavault.source;

import be.cnoupoue.memoriavault.indexing.MemoryIndexPersistence;
import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.source.api.CreateMemorySourceRequest;
import be.cnoupoue.memoriavault.source.api.FavoriteBackupMemoryResponse;
import be.cnoupoue.memoriavault.source.api.FavoritesBackupResponse;
import be.cnoupoue.memoriavault.source.api.FavoritesBackupSourceResponse;
import be.cnoupoue.memoriavault.source.api.FavoritesRestoreSummaryResponse;
import be.cnoupoue.memoriavault.source.api.ImportFavoriteBackupMemoryRequest;
import be.cnoupoue.memoriavault.source.api.ImportFavoritesBackupRequest;
import be.cnoupoue.memoriavault.source.api.MemorySourceResponse;
import be.cnoupoue.memoriavault.source.api.SourceAvailabilityResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemorySourceService {

  private final MemorySourceRepository memorySourceRepository;
  private final SourceAvailabilityService sourceAvailabilityService;
  private final MemoryIndexPersistence memoryIndexPersistence;
  private final MemoryScanJobRepository memoryScanJobRepository;
  private final SnapMemoryRepository snapMemoryRepository;

  public MemorySourceService(
      MemorySourceRepository memorySourceRepository,
      SourceAvailabilityService sourceAvailabilityService,
      MemoryIndexPersistence memoryIndexPersistence,
      MemoryScanJobRepository memoryScanJobRepository,
      SnapMemoryRepository snapMemoryRepository) {
    this.memorySourceRepository = memorySourceRepository;
    this.sourceAvailabilityService = sourceAvailabilityService;
    this.memoryIndexPersistence = memoryIndexPersistence;
    this.memoryScanJobRepository = memoryScanJobRepository;
    this.snapMemoryRepository = snapMemoryRepository;
  }

  public MemorySourceResponse create(CreateMemorySourceRequest request) {
    String normalizedPath = normalizePath(request.rootPath());

    if (memorySourceRepository.existsByRootPath(normalizedPath)) {
      throw new IllegalArgumentException("A source already exists for this path.");
    }

    String now = Instant.now().toString();

    MemorySource source =
        new MemorySource(
            UUID.randomUUID().toString(),
            request.name().trim(),
            normalizedPath,
            null,
            "NOT_SCANNED",
            now,
            now);

    MemorySource savedSource = memorySourceRepository.save(source);

    return toResponse(savedSource);
  }

  @Transactional(readOnly = true)
  public List<MemorySourceResponse> findAll() {
    return memorySourceRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public SourceAvailabilityResponse checkAvailability(String sourceId) {
    MemorySource source = findById(sourceId);
    SourceAvailability availability = sourceAvailabilityService.check(source);

    return new SourceAvailabilityResponse(availability.status().name(), availability.message());
  }

  @Transactional(readOnly = true)
  public FavoritesBackupResponse exportFavoritesBackup(String sourceId) {
    MemorySource source = findById(sourceId);

    List<FavoriteBackupMemoryResponse> favorites =
        snapMemoryRepository.findBySourceIdAndIsFavoriteTrue(source.getId()).stream()
            .map(this::toFavoriteBackupMemoryResponse)
            .toList();

    return new FavoritesBackupResponse(
        1,
        Instant.now().toString(),
        new FavoritesBackupSourceResponse(source.getId(), source.getName()),
        source.getId(),
        favorites);
  }

  @Transactional(readOnly = true)
  public FavoritesRestoreSummaryResponse previewFavoritesRestore(
      String sourceId, ImportFavoritesBackupRequest request) {
    findById(sourceId);
    validateFavoritesBackup(request);

    FavoriteRestorePlan plan = buildFavoriteRestorePlan(sourceId, request);

    return plan.toPreviewResponse();
  }

  public FavoritesRestoreSummaryResponse restoreFavoritesBackup(
      String sourceId, ImportFavoritesBackupRequest request) {
    findById(sourceId);
    validateFavoritesBackup(request);

    FavoriteRestorePlan plan = buildFavoriteRestorePlan(sourceId, request);
    String now = Instant.now().toString();
    FavoritesRestoreSummaryResponse summary = plan.toRestoreResponse();

    for (FavoriteRestoreCandidate candidate : plan.candidates().values()) {
      SnapMemory memory = candidate.memory();

      if (!memory.isFavorite()) {
        memory.markFavorite(resolveFavoritedAt(candidate.backupMemory(), now));
      }
    }

    return summary;
  }

  @Transactional(readOnly = true)
  public MemorySource findById(String sourceId) {
    return memorySourceRepository
        .findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("Memory source not found."));
  }

  private String normalizePath(String rawPath) {
    return Path.of(rawPath).toAbsolutePath().normalize().toString();
  }

  private MemorySourceResponse toResponse(MemorySource source) {
    SourceAvailability availability = sourceAvailabilityService.check(source);

    return new MemorySourceResponse(
        source.getId(),
        source.getName(),
        source.getRootPath(),
        source.getLastScanAt(),
        source.getLastScanStatus(),
        availability.status().name(),
        availability.message(),
        snapMemoryRepository.countBySourceIdAndIsFavoriteTrue(source.getId()),
        source.getCreatedAt(),
        source.getUpdatedAt());
  }

  private FavoriteBackupMemoryResponse toFavoriteBackupMemoryResponse(SnapMemory memory) {
    return new FavoriteBackupMemoryResponse(
        memory.getId(),
        memory.getExternalMemoryId(),
        memory.getCapturedAt(),
        memory.getMediaType().name(),
        memory.getMainPath(),
        memory.getFavoritedAt());
  }

  private void validateFavoritesBackup(ImportFavoritesBackupRequest request) {
    if (request.version() != 1) {
      throw new IllegalArgumentException("Unsupported favorites backup version.");
    }

    if (request.favorites() == null) {
      throw new IllegalArgumentException("Favorites backup must include a favorites array.");
    }
  }

  private FavoriteRestorePlan buildFavoriteRestorePlan(
      String sourceId, ImportFavoritesBackupRequest request) {
    Map<String, FavoriteRestoreCandidate> candidates = new LinkedHashMap<>();
    int notFound = 0;

    for (ImportFavoriteBackupMemoryRequest backupMemory : request.favorites()) {
      SnapMemory memory = findRestorableMemory(sourceId, backupMemory);

      if (memory == null) {
        notFound++;
        continue;
      }

      candidates.putIfAbsent(memory.getId(), new FavoriteRestoreCandidate(memory, backupMemory));
    }

    return new FavoriteRestorePlan(request.favorites().size(), candidates, notFound);
  }

  private SnapMemory findRestorableMemory(
      String sourceId, ImportFavoriteBackupMemoryRequest backupMemory) {
    List<SnapMemory> externalIdMatches =
        snapMemoryRepository.findBySourceIdAndExternalMemoryId(
            sourceId, backupMemory.externalMemoryId());

    if (externalIdMatches.size() == 1) {
      return externalIdMatches.getFirst();
    }

    List<SnapMemory> mainPathMatches =
        snapMemoryRepository.findBySourceIdAndMainPath(sourceId, backupMemory.mainPath());

    if (mainPathMatches.size() == 1) {
      return mainPathMatches.getFirst();
    }

    return null;
  }

  private String resolveFavoritedAt(ImportFavoriteBackupMemoryRequest backupMemory, String now) {
    if (backupMemory.favoritedAt() == null || backupMemory.favoritedAt().isBlank()) {
      return now;
    }

    return backupMemory.favoritedAt();
  }

  public void delete(String sourceId) {
    findById(sourceId);
    memoryScanJobRepository.deleteOrphaned();
    memoryIndexPersistence.deleteOrphaned();
    memoryScanJobRepository.deleteBySourceId(sourceId);
    memoryIndexPersistence.deleteBySourceId(sourceId);
    memorySourceRepository.deleteById(sourceId);
  }

  private record FavoriteRestoreCandidate(
      SnapMemory memory, ImportFavoriteBackupMemoryRequest backupMemory) {}

  private record FavoriteRestorePlan(
      int totalFavorites, Map<String, FavoriteRestoreCandidate> candidates, int notFound) {

    private FavoritesRestoreSummaryResponse toPreviewResponse() {
      return new FavoritesRestoreSummaryResponse(
          totalFavorites, restorableCount(), 0, alreadyFavoriteCount(), notFound);
    }

    private FavoritesRestoreSummaryResponse toRestoreResponse() {
      return new FavoritesRestoreSummaryResponse(
          totalFavorites, restorableCount(), restoredCount(), alreadyFavoriteCount(), notFound);
    }

    private int restorableCount() {
      return candidates.size();
    }

    private int restoredCount() {
      return (int)
          candidates.values().stream()
              .filter(candidate -> !candidate.memory().isFavorite())
              .count();
    }

    private int alreadyFavoriteCount() {
      return (int)
          candidates.values().stream().filter(candidate -> candidate.memory().isFavorite()).count();
    }
  }
}
