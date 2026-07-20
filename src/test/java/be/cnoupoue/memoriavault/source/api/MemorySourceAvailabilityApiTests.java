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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemorySourceAvailabilityApiTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private MemorySourceRepository memorySourceRepository;

  @Autowired private MemoryScanJobRepository memoryScanJobRepository;

  @Autowired private SnapMemoryRepository snapMemoryRepository;

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
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId(source.getId()))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            memoryScanJobRepository.countBySourceId("source-already-deleted"))
        .isZero();
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
    String now = Instant.now().toString();

    snapMemoryRepository.save(
        new SnapMemory(
            id,
            sourceId,
            id + "-external",
            "2020-06-10",
            SnapMemoryType.IMAGE,
            temporaryDirectory.resolve("existing.jpg").toString(),
            null,
            123,
            now,
            now,
            now));
  }
}
