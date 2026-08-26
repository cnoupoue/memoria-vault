package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.sql.DriverManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class SourcePathMigrationTest {

  @Test
  void v7PreservesExistingSourcesAndAllowsDuplicatePathsForCleanup() throws Exception {
    String databaseUrl =
        "jdbc:sqlite:" + Files.createTempFile("memoriavault-source-path-migration", ".db");

    Flyway.configure()
        .dataSource(databaseUrl, null, null)
        .locations("classpath:db/migration")
        .target("6")
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(databaseUrl);
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          INSERT INTO memory_sources (
              id, name, root_path, created_at, updated_at
          ) VALUES (
              'source-before-v7', 'Before V7', '/tmp/source', '2026-01-01T00:00:00Z',
              '2026-01-01T00:00:00Z'
          )
          """);
    }

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
              'source-duplicate-after-v7', 'Duplicate After V7', '/tmp/source',
              '2026-01-01T00:00:00Z', '2026-01-01T00:00:00Z'
          )
          """);

      try (var resultSet =
          statement.executeQuery(
              """
              SELECT id, root_path
              FROM memory_sources
              WHERE root_path = '/tmp/source'
              ORDER BY id
              """)) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("id")).isEqualTo("source-before-v7");
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString("id")).isEqualTo("source-duplicate-after-v7");
        assertThat(resultSet.next()).isFalse();
      }
    }
  }

  @Test
  void freshSchemaCreatesNonUniqueSourcePathIndex() throws Exception {
    String databaseUrl =
        "jdbc:sqlite:" + Files.createTempFile("memoriavault-fresh-source-path-schema", ".db");

    Flyway.configure()
        .dataSource(databaseUrl, null, null)
        .locations("classpath:db/migration")
        .load()
        .migrate();

    try (var connection = DriverManager.getConnection(databaseUrl);
        var statement = connection.createStatement()) {
      try (var indexList = statement.executeQuery("PRAGMA index_list('memory_sources')")) {
        boolean foundRootPathIndex = false;

        while (indexList.next()) {
          if ("idx_memory_sources_root_path".equals(indexList.getString("name"))) {
            foundRootPathIndex = true;
            assertThat(indexList.getInt("unique")).isZero();
          }
        }

        assertThat(foundRootPathIndex).isTrue();
      }
    }
  }
}
