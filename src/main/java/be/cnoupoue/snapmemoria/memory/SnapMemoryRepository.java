package be.cnoupoue.snapmemoria.memory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface SnapMemoryRepository extends JpaRepository<SnapMemory, String> {

    Page<SnapMemory> findByCapturedAtStartingWith(
            String capturedAtPrefix,
            Pageable pageable
    );

    @Query(value = """
        SELECT
            CAST(substr(captured_at, 1, 4) AS INTEGER) AS year,
            COUNT(*) AS memoryCount
        FROM memories
        GROUP BY substr(captured_at, 1, 4)
        ORDER BY year DESC
        """, nativeQuery = true)
    List<YearMemoryCount> countMemoriesByYear();

    @Query(value = """
        SELECT
            CAST(substr(captured_at, 6, 2) AS INTEGER) AS month,
            COUNT(*) AS memoryCount
        FROM memories
        WHERE substr(captured_at, 1, 4) = CAST(:year AS TEXT)
        GROUP BY substr(captured_at, 6, 2)
        ORDER BY month ASC
        """, nativeQuery = true)
    List<MonthMemoryCount> countMemoriesByMonth(int year);
}