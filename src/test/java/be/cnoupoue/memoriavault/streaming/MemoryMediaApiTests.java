package be.cnoupoue.memoriavault.streaming;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.hamcrest.Matchers;
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

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-available"))
        .andExpect(status().isOk())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().longValue("Content-Length", 5));
  }

  @Test
  void normalVideoRequestReturnsExpectedMimeType() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("video-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile = Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "video");

    MemorySource source = saveSource("source-video", sourceRoot);
    saveMemory("memory-video", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-video"))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", Matchers.startsWith("video/mp4")))
        .andExpect(header().string("Accept-Ranges", "bytes"));
  }

  @Test
  void validByteRangeRequestReturnsPartialContentHeaders() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("range-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile =
        Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "0123456789");

    MemorySource source = saveSource("source-range", sourceRoot);
    saveMemory("memory-range", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-range").header("Range", "bytes=1-3"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().string("Content-Range", "bytes 1-3/10"))
        .andExpect(header().longValue("Content-Length", 3));
  }

  @Test
  void safariInitialByteRangeRequestReturnsPartialContentHeaders() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("safari-range-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile =
        Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "0123456789");

    MemorySource source = saveSource("source-safari-range", sourceRoot);
    saveMemory("memory-safari-range", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(
            get("/api/memories/{id}/media", "memory-safari-range").header("Range", "bytes=0-1"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().string("Content-Range", "bytes 0-1/10"))
        .andExpect(header().longValue("Content-Length", 2));
  }

  @Test
  void openEndedByteRangeRequestReturnsPartialContentHeaders() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("open-range-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile =
        Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "0123456789");

    MemorySource source = saveSource("source-open-range", sourceRoot);
    saveMemory("memory-open-range", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-open-range").header("Range", "bytes=4-"))
        .andExpect(status().isPartialContent())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().string("Content-Range", "bytes 4-9/10"))
        .andExpect(header().longValue("Content-Length", 6));
  }

  @Test
  void headRequestReturnsMediaHeadersWithoutBody() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("head-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile = Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "video");

    MemorySource source = saveSource("source-head", sourceRoot);
    saveMemory("memory-head", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(head("/api/memories/{id}/media", "memory-head"))
        .andExpect(status().isOk())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().string("Content-Type", Matchers.startsWith("video/mp4")))
        .andExpect(header().longValue("Content-Length", 5));
  }

  @Test
  void supportedVideoExtensionsReturnExplicitMimeTypes() throws Exception {
    assertVideoMimeType("mov", "video/quicktime");
    assertVideoMimeType("webm", "video/webm");
    assertVideoMimeType("m4v", "video/x-m4v");
  }

  @Test
  void invalidByteRangeRequestReturnsSafeRangeResponse() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("invalid-range-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile = Files.writeString(memories.resolve("2020-06-10_memory-main.mp4"), "video");

    MemorySource source = saveSource("source-invalid-range", sourceRoot);
    saveMemory("memory-invalid-range", source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(
            get("/api/memories/{id}/media", "memory-invalid-range").header("Range", "bytes=9-10"))
        .andExpect(status().isRequestedRangeNotSatisfiable())
        .andExpect(header().string("Accept-Ranges", "bytes"))
        .andExpect(header().string("Content-Range", "bytes */5"))
        .andExpect(jsonPath("$.code").value("RANGE_REQUEST_INVALID"))
        .andExpect(jsonPath("$.message", not(containsString(mediaFile.toString()))));
  }

  @Test
  void sourceUnavailableRemainsDistinctFromMissingFile() throws Exception {
    Path missingSourceRoot = temporaryDirectory.resolve("missing-source");

    MemorySource source = saveSource("source-unavailable", missingSourceRoot);
    saveMemory(
        "memory-source-unavailable",
        source.getId(),
        missingSourceRoot.resolve("memories/2020-06-10_memory-main.mp4"),
        SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-source-unavailable"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SOURCE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message").value(containsString("archive source")));
  }

  @Test
  void missingOriginalFileRemainsDistinctFromUnavailableSource() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("missing-file-source"));
    Path missingMedia = sourceRoot.resolve("memories/2020-06-10_memory-main.mp4");

    MemorySource source = saveSource("source-missing-file", sourceRoot);
    saveMemory("memory-missing-file", source.getId(), missingMedia, SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-missing-file"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEDIA_FILE_MISSING"))
        .andExpect(jsonPath("$.message").value(containsString("original file")))
        .andExpect(jsonPath("$.message", not(containsString(missingMedia.toString()))));
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
        .andExpect(jsonPath("$.code").value("MEDIA_PATH_REJECTED"))
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

  private void assertVideoMimeType(String extension, String expectedMimeType) throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve(extension + "-source"));
    Path memories = Files.createDirectory(sourceRoot.resolve("memories"));
    Path mediaFile =
        Files.writeString(memories.resolve("2020-06-10_memory-main." + extension), "video");

    MemorySource source = saveSource("source-" + extension, sourceRoot);
    saveMemory("memory-" + extension, source.getId(), mediaFile, SnapMemoryType.VIDEO);

    mockMvc
        .perform(get("/api/memories/{id}/media", "memory-" + extension))
        .andExpect(status().isOk())
        .andExpect(header().string("Content-Type", Matchers.startsWith(expectedMimeType)));
  }

  private void saveMemory(String id, String sourceId, Path mainPath) {
    saveMemory(id, sourceId, mainPath, SnapMemoryType.IMAGE);
  }

  private void saveMemory(String id, String sourceId, Path mainPath, SnapMemoryType mediaType) {
    String now = Instant.now().toString();

    snapMemoryRepository.save(
        new SnapMemory(
            id,
            sourceId,
            id + "-external",
            "2020-06-10",
            mediaType,
            mainPath.toAbsolutePath().normalize().toString(),
            null,
            5,
            now,
            now,
            now));
  }
}
