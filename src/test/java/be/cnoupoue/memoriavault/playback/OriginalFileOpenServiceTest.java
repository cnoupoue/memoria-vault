package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OriginalFileOpenServiceTest {

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Mock private SecureMemoryPathResolver secureMemoryPathResolver;

  @Mock private LocalFileOpener localFileOpener;

  @TempDir Path tempDir;

  @Test
  void opensOriginalOnlyAfterSecurePathResolution() {
    Path resolvedPath = Path.of("/safe/resolved/video.mov");
    SnapMemory memory = memory("memory-1", "/unsafe/stored/video.mov");
    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(resolvedPath);

    new OriginalFileOpenService(snapMemoryRepository, secureMemoryPathResolver, localFileOpener)
        .openOriginal("memory-1");

    ArgumentCaptor<Path> openedPath = ArgumentCaptor.forClass(Path.class);
    verify(localFileOpener).open(openedPath.capture());
    assertThat(openedPath.getValue()).isEqualTo(resolvedPath);
  }

  @Test
  void revealsOriginalFileLocationForValidFilePath() throws IOException {
    Path resolvedPath = Files.writeString(tempDir.resolve("video.mov"), "media");
    SnapMemory memory = memory("memory-1", "/stored/video.mov");
    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(resolvedPath);

    new OriginalFileOpenService(snapMemoryRepository, secureMemoryPathResolver, localFileOpener)
        .revealOriginal("memory-1");

    verify(localFileOpener).reveal(resolvedPath.toAbsolutePath().normalize());
  }

  @Test
  void reportsMissingOriginalFileWithoutLaunchingPlatformHandler() {
    Path resolvedPath = tempDir.resolve("missing.mov");
    SnapMemory memory = memory("memory-1", "/stored/missing.mov");
    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(resolvedPath);

    assertThatThrownBy(
            () ->
                new OriginalFileOpenService(
                        snapMemoryRepository, secureMemoryPathResolver, localFileOpener)
                    .revealOriginal("memory-1"))
        .isInstanceOf(OriginalFileActionException.class)
        .hasMessage("The original file cannot be found.");

    verify(localFileOpener, never()).reveal(resolvedPath);
  }

  @Test
  void reportsInaccessibleOriginalFileWithoutLaunchingPlatformHandler() throws IOException {
    Path resolvedPath = Files.writeString(tempDir.resolve("locked.mov"), "media");
    boolean changed = resolvedPath.toFile().setReadable(false, false);
    Assumptions.assumeTrue(changed);
    Assumptions.assumeFalse(Files.isReadable(resolvedPath));
    SnapMemory memory = memory("memory-1", "/stored/locked.mov");
    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(resolvedPath);

    try {
      assertThatThrownBy(
              () ->
                  new OriginalFileOpenService(
                          snapMemoryRepository, secureMemoryPathResolver, localFileOpener)
                      .shareOriginal("memory-1"))
          .isInstanceOf(OriginalFileActionException.class)
          .hasMessage("Memoria Vault does not have permission to access the original file.");

      verify(localFileOpener, never()).share(resolvedPath);
      verify(localFileOpener, never()).reveal(resolvedPath);
    } finally {
      resolvedPath.toFile().setReadable(true, false);
    }
  }

  @Test
  void usesRevealFallbackWhenNativeShareIsUnsupported() throws IOException {
    Path resolvedPath = Files.writeString(tempDir.resolve("photo.jpg"), "media");
    SnapMemory memory = memory("memory-1", "/stored/photo.jpg");
    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(resolvedPath);
    when(localFileOpener.supportsNativeShare()).thenReturn(false);

    new OriginalFileOpenService(snapMemoryRepository, secureMemoryPathResolver, localFileOpener)
        .shareOriginal("memory-1");

    verify(localFileOpener, never()).share(resolvedPath);
    verify(localFileOpener).reveal(resolvedPath.toAbsolutePath().normalize());
  }

  private SnapMemory memory(String id, String originalPath) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        "source-1",
        id + "-external",
        "2020-06-10",
        SnapMemoryType.VIDEO,
        originalPath,
        null,
        5,
        now,
        now,
        now);
  }
}
