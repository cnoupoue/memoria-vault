package be.cnoupoue.memoriavault.source.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import jakarta.persistence.EntityManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemorySourceAvailabilityApiTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private EntityManager entityManager;

  @Autowired private MemorySourceRepository memorySourceRepository;

  @Autowired private MemoryScanJobRepository memoryScanJobRepository;

  @Autowired private SnapMemoryRepository snapMemoryRepository;

  @Value("${memoriavault.thumbnail.directory}")
  private String thumbnailDirectory;

  @TempDir private Path temporaryDirectory;

  @BeforeEach
  void cleanDatabase() {
    snapMemoryRepository.deleteAll();
    memoryScanJobRepository.deleteAll();
    memorySourceRepository.deleteAll();
  }

  @AfterEach
  void restorePermissions() throws Exception {
    Path unreadable = temporaryDirectory.resolve("unreadable");

    if (Files.exists(unreadable)) {
      Assumptions.assumeTrue(Files.getFileStore(unreadable).supportsFileAttributeView("posix"));
      Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Test
  void reportsAvailableSource() throws Exception {
    MemorySource source = saveSource("source-available", temporaryDirectory);

    mockMvc
        .perform(get("/api/sources/{id}/availability", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$.availabilityMessage").value("Source folder is available."));
  }

  @Test
  void reportsMissingSourceFolder() throws Exception {
    Path missingPath = temporaryDirectory.resolve("missing");
    MemorySource source = saveSource("source-missing", missingPath);

    mockMvc
        .perform(get("/api/sources/{id}/availability", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityStatus").value("UNAVAILABLE"))
        .andExpect(
            jsonPath("$.availabilityMessage")
                .value("Connect the drive containing this source, then refresh its status."));
  }

  @Test
  void reportsFilePathInsteadOfDirectory() throws Exception {
    Path filePath = Files.createFile(temporaryDirectory.resolve("not-a-directory"));
    MemorySource source = saveSource("source-file", filePath);

    mockMvc
        .perform(get("/api/sources/{id}/availability", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityStatus").value("NOT_A_DIRECTORY"))
        .andExpect(
            jsonPath("$.availabilityMessage")
                .value("The configured source location is not a folder."));
  }

  @Test
  void reportsUnreadableSourceFolder() throws Exception {
    Assumptions.assumeTrue(
        Files.getFileStore(temporaryDirectory).supportsFileAttributeView("posix"));

    Path unreadable = Files.createDirectory(temporaryDirectory.resolve("unreadable"));
    Files.setPosixFilePermissions(unreadable, PosixFilePermissions.fromString("---------"));

    Assumptions.assumeFalse(Files.isReadable(unreadable));

    MemorySource source = saveSource("source-unreadable", unreadable);

    mockMvc
        .perform(get("/api/sources/{id}/availability", source.getId()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.availabilityStatus").value("NOT_READABLE"))
        .andExpect(
            jsonPath("$.availabilityMessage")
                .value("The configured source folder is not readable."));
  }

  @Test
  void includesCurrentAvailabilityInSourceList() throws Exception {
    saveSource("source-list", temporaryDirectory);

    mockMvc
        .perform(get("/api/sources"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].availabilityStatus").value("AVAILABLE"))
        .andExpect(jsonPath("$[0].availabilityMessage").value("Source folder is available."));
  }

  @Test
  void blocksScanWhenSourceIsUnavailableWithoutCreatingJobOrDeletingMemories() throws Exception {
    Path missingPath = temporaryDirectory.resolve("missing");
    MemorySource source = saveSource("source-blocked", missingPath);
    saveMemory(source.getId());

    mockMvc
        .perform(post("/api/sources/{id}/scan", source.getId()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.status").value(409))
        .andExpect(jsonPath("$.code").value("SOURCE_UNAVAILABLE"))
        .andExpect(
            jsonPath("$.message").value("The configured source folder is currently unavailable."))
        .andExpect(jsonPath("$.timestamp").isString())
        .andExpect(jsonPath("$.message", not(containsString(missingPath.toString()))));

    org.assertj.core.api.Assertions.assertThat(memoryScanJobRepository.count()).isZero();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(source.getId()))
        .isEqualTo(1);
  }

  @Test
  void deletesSourceWithIndexedMemoriesAndScanJobs() throws Exception {
    MemorySource source = saveSource("source-delete", temporaryDirectory);
    saveMemory("memory-delete", source.getId());
    saveMemory("memory-orphaned", "source-already-deleted");
    memoryScanJobRepository.save(
        new be.cnoupoue.memoriavault.indexing.MemoryScanJob(
            "scan-delete", source.getId(), Instant.now().toString()));
    memoryScanJobRepository.save(
        new be.cnoupoue.memoriavault.indexing.MemoryScanJob(
            "scan-orphaned", "source-already-deleted", Instant.now().toString()));

    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(source.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(source.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            snapMemoryRepository.countBySourceId("source-already-deleted"))
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId(source.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId("source-already-deleted"))
        .isEqualTo(1);
  }

  @Test
  void deletesSourceWhoseFolderIsMissingWithoutTouchingOriginalMedia() throws Exception {
    Path sourceFolder = Files.createDirectory(temporaryDirectory.resolve("source-folder"));
    Path originalMedia = Files.writeString(sourceFolder.resolve("original.jpg"), "media");
    MemorySource source = saveSource("source-missing-delete", sourceFolder);
    saveMemory("memory-missing-delete", source.getId(), originalMedia);
    Files.delete(originalMedia);
    Files.delete(sourceFolder);

    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(source.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(source.getId()))
        .isZero();
  }

  @Test
  void deletesSourceOnDisconnectedDrivePath() throws Exception {
    Path disconnectedDrivePath =
        temporaryDirectory.resolve("disconnected-drive").resolve("snapchat-export");
    MemorySource source = saveSource("source-disconnected-delete", disconnectedDrivePath);
    saveMemory(source.getId());

    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(source.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(source.getId()))
        .isZero();
  }

  @Test
  void deletesDuplicateSourceRecordsIndependentlyById() throws Exception {
    Path duplicatePath = temporaryDirectory.resolve("duplicate-path");
    MemorySource first = saveSource("source-duplicate-first", duplicatePath);
    MemorySource second = saveSource("source-duplicate-second", duplicatePath);
    saveMemory("memory-duplicate-first", first.getId());
    saveMemory("memory-duplicate-second", second.getId());

    mockMvc.perform(delete("/api/sources/{id}", first.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(first.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(second.getId()))
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(first.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(second.getId()))
        .isEqualTo(1);

    entityManager.clear();

    mockMvc.perform(delete("/api/sources/{id}", second.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(second.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(second.getId()))
        .isZero();
  }

  @Test
  void deletingDuplicateSourcesAcrossPersistenceBoundariesLeavesNoSelectedOrphans()
      throws Exception {
    Path duplicatePath = temporaryDirectory.resolve("duplicate-restart-path");
    MemorySource first = saveSource("source-restart-first", duplicatePath);
    MemorySource second = saveSource("source-restart-second", duplicatePath);
    saveMemory("memory-restart-first", first.getId());
    saveMemory("memory-restart-second", second.getId());
    memoryScanJobRepository.save(
        new be.cnoupoue.memoriavault.indexing.MemoryScanJob(
            "scan-restart-first", first.getId(), Instant.now().toString()));
    memoryScanJobRepository.save(
        new be.cnoupoue.memoriavault.indexing.MemoryScanJob(
            "scan-restart-second", second.getId(), Instant.now().toString()));

    mockMvc.perform(delete("/api/sources/{id}", first.getId())).andExpect(status().isNoContent());

    entityManager.clear();

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(first.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(second.getId()))
        .isTrue();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(first.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(second.getId()))
        .isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId(first.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId(second.getId()))
        .isEqualTo(1);

    mockMvc.perform(delete("/api/sources/{id}", second.getId())).andExpect(status().isNoContent());

    entityManager.clear();

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(second.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(snapMemoryRepository.countBySourceId(second.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId(second.getId()))
        .isZero();
  }

  @Test
  void favoritesBackupDeleteAndPartialRestoreDoNotAffectUnrelatedSource() throws Exception {
    MemorySource removedSource = saveSource("source-favorites-removed", temporaryDirectory);
    MemorySource activeSource = saveSource("source-favorites-active", temporaryDirectory);
    saveFavoriteMemory(
        "memory-removed-favorite",
        removedSource.getId(),
        "shared-external",
        temporaryDirectory.resolve("removed.jpg"));
    saveFavoriteMemory(
        "memory-active-existing-favorite",
        activeSource.getId(),
        "existing-active-external",
        temporaryDirectory.resolve("active-existing.jpg"));
    saveMemory(
        "memory-active-restorable",
        activeSource.getId(),
        "shared-external",
        temporaryDirectory.resolve("active-restorable.jpg"));

    String exportedBackup =
        mockMvc
            .perform(get("/api/sources/{id}/favorites-backup", removedSource.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorites.length()").value(1))
            .andReturn()
            .getResponse()
            .getContentAsString();

    mockMvc
        .perform(delete("/api/sources/{id}", removedSource.getId()))
        .andExpect(status().isNoContent());

    entityManager.clear();

    org.assertj.core.api.Assertions.assertThat(
            snapMemoryRepository.countBySourceId(removedSource.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            snapMemoryRepository.countBySourceIdAndIsFavoriteTrue(activeSource.getId()))
        .isEqualTo(1);

    mockMvc
        .perform(
            post("/api/sources/{id}/favorites-backup/restore", activeSource.getId())
                .contentType("application/json")
                .content(
                    """
                    {
                      "version": 1,
                      "exportedAt": "2026-07-18T00:00:00Z",
                      "sourceId": "%s",
                      "favorites": [
                        {
                          "memoryId": "memory-removed-favorite",
                          "externalMemoryId": "shared-external",
                          "capturedAt": "2020-06-10",
                          "mediaType": "IMAGE",
                          "mainPath": "%s",
                          "favoritedAt": "2026-07-18T00:00:00Z"
                        },
                        {
                          "memoryId": "missing-memory",
                          "externalMemoryId": "missing-external",
                          "capturedAt": "2020-01-01",
                          "mediaType": "IMAGE",
                          "mainPath": "%s",
                          "favoritedAt": null
                        }
                      ]
                    }
                    """
                        .formatted(
                            removedSource.getId(),
                            temporaryDirectory.resolve("removed.jpg"),
                            temporaryDirectory.resolve("missing.jpg"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalFavorites").value(2))
        .andExpect(jsonPath("$.restorable").value(1))
        .andExpect(jsonPath("$.restored").value(1))
        .andExpect(jsonPath("$.alreadyFavorite").value(0))
        .andExpect(jsonPath("$.notFound").value(1))
        .andExpect(jsonPath("$.skipped").value(1));

    entityManager.clear();

    org.assertj.core.api.Assertions.assertThat(
            snapMemoryRepository.countBySourceIdAndIsFavoriteTrue(activeSource.getId()))
        .isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(
            memorySourceRepository.existsById(removedSource.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(
            memorySourceRepository.existsById(activeSource.getId()))
        .isTrue();
  }

  @Test
  void deletingSourceIsIdempotentWhenApplicationCacheIsAlreadyMissing() throws Exception {
    MemorySource source = saveSource("source-idempotent-delete", temporaryDirectory);
    saveMemory("memory-idempotent-delete", source.getId());
    Files.deleteIfExists(Path.of(thumbnailDirectory).resolve("memory-idempotent-delete.jpg"));

    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(memorySourceRepository.existsById(source.getId()))
        .isFalse();
  }

  @Test
  void deletingSourceDoesNotDeleteOriginalMediaFiles() throws Exception {
    Path originalMedia =
        Files.writeString(temporaryDirectory.resolve("original-media.jpg"), "media");
    MemorySource source = saveSource("source-preserve-media", temporaryDirectory);
    saveMemory("memory-preserve-media", source.getId(), originalMedia);

    mockMvc.perform(delete("/api/sources/{id}", source.getId())).andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(Files.readString(originalMedia)).isEqualTo("media");
  }

  private MemorySource saveSource(String id, Path rootPath) {
    String now = Instant.now().toString();

    return memorySourceRepository.save(
        new MemorySource(
            id,
            "Snapchat USB",
            rootPath.toAbsolutePath().normalize().toString(),
            null,
            "NOT_SCANNED",
            now,
            now));
  }

  private void saveMemory(String sourceId) {
    saveMemory("memory-1", sourceId);
  }

  private void saveMemory(String id, String sourceId) {
    saveMemory(id, sourceId, id + "-external", temporaryDirectory.resolve("existing.jpg"));
  }

  private void saveMemory(String id, String sourceId, Path mediaPath) {
    saveMemory(id, sourceId, id + "-external", mediaPath);
  }

  private void saveMemory(String id, String sourceId, String externalMemoryId, Path mediaPath) {
    String now = Instant.now().toString();

    snapMemoryRepository.save(
        new SnapMemory(
            id,
            sourceId,
            externalMemoryId,
            "2020-06-10",
            SnapMemoryType.IMAGE,
            mediaPath.toString(),
            null,
            123,
            now,
            now,
            now));
  }

  private void saveFavoriteMemory(String id, String sourceId, String externalMemoryId, Path path) {
    String now = Instant.now().toString();
    SnapMemory memory =
        new SnapMemory(
            id,
            sourceId,
            externalMemoryId,
            "2020-06-10",
            SnapMemoryType.IMAGE,
            path.toString(),
            null,
            123,
            now,
            now,
            now);
    memory.markFavorite("2026-07-18T00:00:00Z");
    snapMemoryRepository.save(memory);
  }
}
