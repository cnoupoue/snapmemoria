package be.cnoupoue.memoriavault.indexing;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemoryIndexPersistence {

  @PersistenceContext private EntityManager entityManager;

  private final SnapMemoryRepository snapMemoryRepository;

  public MemoryIndexPersistence(SnapMemoryRepository snapMemoryRepository) {
    this.snapMemoryRepository = snapMemoryRepository;
  }

  @Transactional
  public void deleteBySourceId(String sourceId) {
    entityManager
        .createQuery(
            """
                DELETE FROM SnapMemory memory
                WHERE memory.sourceId = :sourceId
                """)
        .setParameter("sourceId", sourceId)
        .executeUpdate();
  }

  @Transactional
  public void deleteOrphaned() {
    entityManager
        .createQuery(
            """
                DELETE FROM SnapMemory memory
                WHERE memory.sourceId NOT IN (
                    SELECT source.id
                    FROM MemorySource source
                )
                """)
        .executeUpdate();
  }

  @Transactional
  public void saveBatch(List<SnapMemory> memories) {
    for (SnapMemory memory : memories) {
      entityManager.persist(memory);
    }

    entityManager.flush();
    entityManager.clear();
  }

  @Transactional
  public void synchronizeSourceMemories(String sourceId, List<SnapMemory> scannedMemories) {
    Map<String, SnapMemory> existingMemoriesByMainPath = new HashMap<>();

    for (SnapMemory existingMemory : snapMemoryRepository.findBySourceId(sourceId)) {
      existingMemoriesByMainPath.put(existingMemory.getMainPath(), existingMemory);
    }

    for (SnapMemory scannedMemory : scannedMemories) {
      SnapMemory existingMemory = existingMemoriesByMainPath.remove(scannedMemory.getMainPath());

      if (existingMemory == null) {
        entityManager.persist(scannedMemory);
      } else {
        existingMemory.updateIndexedMetadata(scannedMemory);
      }
    }

    Set<String> missingMemoryIds =
        existingMemoriesByMainPath.values().stream()
            .map(SnapMemory::getId)
            .collect(Collectors.toSet());

    if (!missingMemoryIds.isEmpty()) {
      entityManager
          .createQuery(
              """
              DELETE FROM SnapMemory memory
              WHERE memory.id IN :memoryIds
              """)
          .setParameter("memoryIds", missingMemoryIds)
          .executeUpdate();
    }

    entityManager.flush();
    entityManager.clear();
  }

  @Transactional
  public boolean attachOverlay(
      String sourceId, String externalMemoryId, String overlayPath, String updatedAt) {
    int updatedRows =
        entityManager
            .createQuery(
                """
                UPDATE SnapMemory memory
                SET memory.overlayPath = :overlayPath,
                    memory.updatedAt = :updatedAt
                WHERE memory.sourceId = :sourceId
                  AND memory.externalMemoryId = :externalMemoryId
                """)
            .setParameter("sourceId", sourceId)
            .setParameter("externalMemoryId", externalMemoryId)
            .setParameter("overlayPath", overlayPath)
            .setParameter("updatedAt", updatedAt)
            .executeUpdate();

    return updatedRows == 1;
  }
}
