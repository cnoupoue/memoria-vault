package be.cnoupoue.snapmemoria.streaming;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SecureMemoryPathResolverTest {

  @Mock private MemorySourceRepository memorySourceRepository;

  @TempDir private Path temporaryDirectory;

  @Test
  void rebasesMovedAbsoluteSnapchatExportPathToCurrentSourceRoot() throws Exception {
    Path currentRoot = Files.createDirectory(temporaryDirectory.resolve("current"));
    Path currentMemories = Files.createDirectory(currentRoot.resolve("memories 2"));
    Path currentFile = Files.writeString(currentMemories.resolve("memory-main.jpg"), "current");
    Path oldFile =
        temporaryDirectory.resolve("old").resolve("memories 2").resolve("memory-main.jpg");
    SecureMemoryPathResolver resolver = resolverFor(source("source-1", currentRoot));

    Path resolved = resolver.resolve("source-1", oldFile.toString(), "Unavailable.");

    assertThat(resolved).isEqualTo(currentFile.toRealPath());
  }

  @Test
  void resolvesRelativePathInsideSourceRoot() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("source"));
    Path media = Files.createDirectories(sourceRoot.resolve("memories"));
    Path file = Files.writeString(media.resolve("memory-main.jpg"), "image");
    SecureMemoryPathResolver resolver = resolverFor(source("source-1", sourceRoot));

    Path resolved = resolver.resolve("source-1", "memories/memory-main.jpg", "Unavailable.");

    assertThat(resolved).isEqualTo(file.toRealPath());
  }

  @Test
  void rejectsTraversalOutsideSourceRoot() throws Exception {
    Path sourceRoot = Files.createDirectory(temporaryDirectory.resolve("source"));
    Path outside = Files.writeString(temporaryDirectory.resolve("outside.jpg"), "outside");
    SecureMemoryPathResolver resolver = resolverFor(source("source-1", sourceRoot));

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    "source-1",
                    sourceRoot.resolve("..").resolve(outside.getFileName()).toString(),
                    "Unavailable."))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.FORBIDDEN));
  }

  @Test
  void returnsNotFoundWhenSourceIsMissing() {
    SecureMemoryPathResolver resolver = new SecureMemoryPathResolver(memorySourceRepository);

    when(memorySourceRepository.findById("missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> resolver.resolve("missing", "memories/file.jpg", "Unavailable."))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  private SecureMemoryPathResolver resolverFor(MemorySource source) {
    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    return new SecureMemoryPathResolver(memorySourceRepository);
  }

  private MemorySource source(String id, Path rootPath) {
    String now = Instant.now().toString();

    return new MemorySource(
        id,
        "Snapchat USB",
        rootPath.toAbsolutePath().normalize().toString(),
        null,
        "NOT_SCANNED",
        now,
        now);
  }
}
