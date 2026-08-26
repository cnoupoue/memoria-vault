package be.cnoupoue.memoriavault.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ApplicationDataResetServiceTest {

  @Mock private MemorySourceRepository memorySourceRepository;

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Mock private MemoryScanJobRepository memoryScanJobRepository;

  @TempDir Path tempDir;

  @Test
  void resetWithSourcesAndFavoritesClearsDatabaseAndCachesButKeepsOriginalMedia()
      throws IOException {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    Path appRoot = Files.createDirectories(home.resolve(".memoria-vault"));
    Path database = write(appRoot.resolve("data").resolve("memoriavault.db"), "db");
    Path thumbnail =
        write(appRoot.resolve("cache").resolve("thumbnails").resolve("memory-1.jpg"), "thumb");
    Path playback =
        write(appRoot.resolve("cache").resolve("playback").resolve("memory-1.mp4"), "video");
    Path metadata = write(appRoot.resolve("metadata").resolve("state.json"), "{}");
    Path originalSource = Files.createDirectories(tempDir.resolve("original-export"));
    Path originalMedia = write(originalSource.resolve("memories").resolve("photo.jpg"), "photo");
    MemorySource source = source("source-1", originalSource);

    when(memoryScanJobRepository.existsByStatus("RUNNING")).thenReturn(false);
    when(memorySourceRepository.findAll()).thenReturn(List.of(source));

    ApplicationDataResetResponse response =
        service(
                home,
                appRoot,
                database,
                appRoot.resolve("cache/thumbnails"),
                appRoot.resolve("cache/playback"))
            .reset();

    assertThat(response.reset()).isTrue();
    assertThat(response.restartRequired()).isFalse();
    assertThat(thumbnail).doesNotExist();
    assertThat(playback).doesNotExist();
    assertThat(metadata).doesNotExist();
    assertThat(database).exists();
    assertThat(originalSource).exists();
    assertThat(originalMedia).exists();
    verify(memoryScanJobRepository).deleteAllInBatch();
    verify(snapMemoryRepository).deleteAllInBatch();
    verify(memorySourceRepository).deleteAllInBatch();
  }

  @Test
  void resetHandlesEmptyStateAndMissingCacheDirectories() throws IOException {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    Path appRoot = Files.createDirectories(home.resolve(".memoria-vault"));
    Path database = write(appRoot.resolve("data").resolve("memoriavault.db"), "db");

    when(memoryScanJobRepository.existsByStatus("RUNNING")).thenReturn(false);
    when(memorySourceRepository.findAll()).thenReturn(List.of());

    ApplicationDataResetResponse response =
        service(
                home,
                appRoot,
                database,
                appRoot.resolve("missing-thumbnails"),
                appRoot.resolve("missing-playback"))
            .reset();

    assertThat(response.reset()).isTrue();
    assertThat(database).exists();
    assertThat(appRoot.resolve("missing-thumbnails")).isDirectory();
    assertThat(appRoot.resolve("missing-playback")).isDirectory();
  }

  @Test
  void resetRemovesInactiveLegacyApplicationData() throws IOException {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    Path appRoot = Files.createDirectories(home.resolve(".memoria-vault"));
    Path legacyRoot = Files.createDirectories(home.resolve(".snapmemoria"));
    Path database = write(appRoot.resolve("data").resolve("memoriavault.db"), "db");
    Path legacyDatabase = write(legacyRoot.resolve("data").resolve("snapmemoria.db"), "legacy");
    Path legacyThumbnail =
        write(legacyRoot.resolve("cache").resolve("thumbnails").resolve("old.jpg"), "old");

    when(memoryScanJobRepository.existsByStatus("RUNNING")).thenReturn(false);
    when(memorySourceRepository.findAll()).thenReturn(List.of());

    service(
            home,
            appRoot,
            database,
            appRoot.resolve("cache/thumbnails"),
            appRoot.resolve("cache/playback"))
        .reset();

    assertThat(legacyDatabase).doesNotExist();
    assertThat(legacyThumbnail).doesNotExist();
    assertThat(legacyRoot).doesNotExist();
  }

  @Test
  void resetDoesNotDeleteOriginalMediaNestedUnderApplicationDataDirectory() throws IOException {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    Path appRoot = Files.createDirectories(home.resolve(".memoria-vault"));
    Path database = write(appRoot.resolve("data").resolve("memoriavault.db"), "db");
    Path sourceRoot = Files.createDirectories(appRoot.resolve("original-export"));
    Path originalMedia = write(sourceRoot.resolve("memories").resolve("photo.jpg"), "photo");

    when(memoryScanJobRepository.existsByStatus("RUNNING")).thenReturn(false);
    when(memorySourceRepository.findAll()).thenReturn(List.of(source("source-1", sourceRoot)));

    service(
            home,
            appRoot,
            database,
            appRoot.resolve("cache/thumbnails"),
            appRoot.resolve("cache/playback"))
        .reset();

    assertThat(sourceRoot).exists();
    assertThat(originalMedia).exists();
  }

  @Test
  void resetRejectsActiveScansBeforeDeletingAnything() throws IOException {
    Path home = Files.createDirectories(tempDir.resolve("home"));
    Path appRoot = Files.createDirectories(home.resolve(".memoria-vault"));
    Path database = write(appRoot.resolve("data").resolve("memoriavault.db"), "db");

    when(memoryScanJobRepository.existsByStatus("RUNNING")).thenReturn(true);

    assertThatThrownBy(
            () ->
                service(
                        home,
                        appRoot,
                        database,
                        appRoot.resolve("cache/thumbnails"),
                        appRoot.resolve("cache/playback"))
                    .reset())
        .isInstanceOf(ApplicationDataResetException.class)
        .hasMessage(
            "A scan is still running. Wait for it to finish before resetting application data.");

    assertThat(database).exists();
    verify(memorySourceRepository, never()).deleteAllInBatch();
  }

  private ApplicationDataResetService service(
      Path home, Path appRoot, Path database, Path thumbnails, Path playback) {
    return new ApplicationDataResetService(
        memorySourceRepository,
        snapMemoryRepository,
        memoryScanJobRepository,
        appRoot.toString(),
        "jdbc:sqlite:" + database,
        thumbnails.toString(),
        playback.toString(),
        home.toString());
  }

  private MemorySource source(String id, Path rootPath) {
    String now = Instant.now().toString();

    return new MemorySource(id, "Source", rootPath.toString(), now, "COMPLETED", now, now);
  }

  private Path write(Path path, String contents) throws IOException {
    Files.createDirectories(path.getParent());
    return Files.writeString(path, contents);
  }
}
