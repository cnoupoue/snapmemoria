package be.cnoupoue.memoriavault.memory;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SnapMemoryRepository extends JpaRepository<SnapMemory, String> {

  Page<SnapMemory> findByCapturedAtStartingWith(String capturedAtPrefix, Pageable pageable);

  List<SnapMemory> findBySourceId(String sourceId);

  List<SnapMemory> findBySourceIdAndExternalMemoryId(String sourceId, String externalMemoryId);

  List<SnapMemory> findBySourceIdAndMainPath(String sourceId, String mainPath);

  List<SnapMemory> findBySourceIdAndIsFavoriteTrue(String sourceId);

  long countBySourceIdAndIsFavoriteTrue(String sourceId);

  @Query(
      """
      SELECT memory
      FROM SnapMemory memory
      WHERE memory.isFavorite = true
      """)
  Page<SnapMemory> findFavorites(Pageable pageable);

  long countBySourceId(String sourceId);

  @Query(
      value =
          """
        SELECT
            CAST(substr(captured_at, 1, 4) AS INTEGER) AS year,
            COUNT(*) AS memoryCount
        FROM memories
        GROUP BY substr(captured_at, 1, 4)
        ORDER BY year DESC
        """,
      nativeQuery = true)
  List<YearMemoryCount> countMemoriesByYear();

  @Query(
      value =
          """
        SELECT
            CAST(substr(captured_at, 6, 2) AS INTEGER) AS month,
            COUNT(*) AS memoryCount
        FROM memories
        WHERE substr(captured_at, 1, 4) = CAST(:year AS TEXT)
        GROUP BY substr(captured_at, 6, 2)
        ORDER BY month ASC
        """,
      nativeQuery = true)
  List<MonthMemoryCount> countMemoriesByMonth(int year);

  @Query(
      value =
          """
        SELECT *
        FROM memories
        WHERE substr(captured_at, 6, 5) = :monthDay
          AND CAST(substr(captured_at, 1, 4) AS INTEGER) < :currentYear
        ORDER BY captured_at DESC, created_at DESC
        """,
      nativeQuery = true)
  List<SnapMemory> findFlashbacks(
      @Param("monthDay") String monthDay, @Param("currentYear") int currentYear);
}
