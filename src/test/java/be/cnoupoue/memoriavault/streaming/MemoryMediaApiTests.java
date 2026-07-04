package be.cnoupoue.memoriavault.streaming;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import java.time.Instant;
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
class MemoryMediaApiTests {

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

  @Test
  void servesMediaWhenSourceIsAvailableAndIndexedFileExists() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile = Files.writeString(memories.resolve("2020-06-10_memory-main.jpg"), "image");

    MemorySource source = saveSource("source-available", sourceRoot);
    saveMemory("memory-available", source.getId(), mediaFile);

    mockMvc.perform(get("/api/memories/{id}/media", "memory-available")).andExpect(status().isOk());
  }

  @Test
  void servesMediaFromCurrentSourceRootWhenIndexedAbsolutePathWasMoved() throws Exception {
    Path currentRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));
    Path currentMemories = Files.createDirectory(currentRoot.resolve("memories 2"));
    Files.writeString(currentMemories.resolve("2020-06-10_memory-main.jpg"), "image");

    Path oldRoot = temporaryDirectory.resolve("old-snapchat-memories");
    Path oldIndexedPath = oldRoot.resolve("memories 2").resolve("2020-06-10_memory-main.jpg");

    MemorySource source = saveSource("source-moved", currentRoot);
    saveMemory("memory-moved", source.getId(), oldIndexedPath);

    mockMvc.perform(get("/api/memories/{id}/media", "memory-moved")).andExpect(status().isOk());
  }

  @Test
  void prefersCurrentSourceRootWhenOldIndexedAbsolutePathStillExistsOutsideSource()
      throws Exception {
    Path currentRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));
    Path currentMemories = Files.createDirectory(currentRoot.resolve("memories"));
    Files.writeString(currentMemories.resolve("2020-06-10_memory-main.jpg"), "current image");

    Path oldRoot = Files.createDirectory(temporaryDirectory.resolve("old-snapchat-memories"));
    Path oldMemories = Files.createDirectory(oldRoot.resolve("memories"));
    Path oldIndexedPath =
        Files.writeString(oldMemories.resolve("2020-06-10_memory-main.jpg"), "old image");

    MemorySource source = saveSource("source-current-root", currentRoot);
    saveMemory("memory-current-root", source.getId(), oldIndexedPath);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-current-root"))
        .andExpect(status().isOk());
  }

  @Test
  void refusesExistingMediaOutsideTheConfiguredSourceRoot() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("snapchat-memories"));
    Path outsideRoot = Files.createDirectory(temporaryDirectory.resolve("outside"));
    Path outsideMemories = Files.createDirectory(outsideRoot.resolve("memories"));
    Path outsideFile =
        Files.writeString(outsideMemories.resolve("2020-06-10_memory-main.jpg"), "image");

    MemorySource source = saveSource("source-secure", sourceRoot);
    saveMemory("memory-outside", source.getId(), outsideFile);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-outside"))
        .andExpect(status().isForbidden())
        .andExpect(
            jsonPath("$.message")
                .value("The requested file is outside the configured memory source."))
        .andExpect(jsonPath("$.message", not(containsString(outsideFile.toString()))));
  }

  private MemorySource saveSource(String id, Path rootPath) {
    String now = Instant.now().toString();

    return memorySourceRepository.save(
        new MemorySource(
            id,
            "Snapchat USB",
            rootPath.toAbsolutePath().normalize().toString(),
            null,
            "COMPLETED",
            now,
            now));
  }

  private void saveMemory(String id, String sourceId, Path mainPath) {
    String now = Instant.now().toString();

    snapMemoryRepository.save(
        new SnapMemory(
            id,
            sourceId,
            id + "-external",
            "2020-06-10",
            SnapMemoryType.IMAGE,
            mainPath.toAbsolutePath().normalize().toString(),
            null,
            5,
            now,
            now,
            now));
  }
}
