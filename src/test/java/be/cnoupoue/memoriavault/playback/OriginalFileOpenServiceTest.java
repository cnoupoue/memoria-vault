package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OriginalFileOpenServiceTest {

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Mock private SecureMemoryPathResolver secureMemoryPathResolver;

  @Mock private LocalFileOpener localFileOpener;

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
