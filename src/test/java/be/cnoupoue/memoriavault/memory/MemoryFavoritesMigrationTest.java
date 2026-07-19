package be.cnoupoue.memoriavault.memory;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class MemoryFavoritesMigrationTest {

  @Test
  void migrationDefaultsExistingMemoriesToNotFavorite() throws Exception {
    String databaseUrl =
        "jdbc:sqlite:" + Files.createTempFile("memoriavault-favorites-migration", ".db");

    Flyway.configure()
        .dataSource(databaseUrl, null, null)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(databaseUrl);
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO memory_sources (
              id, name, root_path, created_at, updated_at
          ) VALUES (
              'source-1', 'Source', '/tmp/source', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'
          )
          """);
      statement.executeUpdate(
          """
          INSERT INTO memories (
              id, source_id, external_memory_id, captured_at, media_type, main_path,
              file_size_bytes, last_modified_at, created_at, updated_at
          ) VALUES (
              'memory-1', 'source-1', 'external-1', '2026-01-01', 'IMAGE', '/tmp/source/memory.jpg',
              1024, '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'
          )
          """);

      try (var resultSet =
          statement.executeQuery(
              "SELECT is_favorite, favorited_at FROM memories WHERE id = 'memory-1'")) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getInt("is_favorite")).isZero();
        assertThat(resultSet.getString("favorited_at")).isNull();
      }
    }
  }
}
