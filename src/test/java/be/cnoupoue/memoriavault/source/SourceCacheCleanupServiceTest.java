package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceCacheCleanupServiceTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void deletesExistingMemoryCachesAndIgnoresAlreadyMissingCacheFiles() throws Exception {
    Path thumbnailDirectory = Files.createDirectory(temporaryDirectory.resolve("thumbnails"));
    Path thumbnail = Files.writeString(thumbnailDirectory.resolve("memory-1.jpg"), "thumbnail");
    Path playbackDirectory = Files.createDirectory(temporaryDirectory.resolve("playback"));
    Path playback = Files.writeString(playbackDirectory.resolve("memory-1-abcdef.mp4"), "video");
    Path otherPlayback =
        Files.writeString(playbackDirectory.resolve("memory-2-abcdef.mp4"), "other video");
    SourceCacheCleanupService service =
        new SourceCacheCleanupService(thumbnailDirectory.toString(), playbackDirectory.toString());

    service.deleteApplicationCaches("source-1", List.of("memory-1", "memory-missing"));

    assertThat(thumbnail).doesNotExist();
    assertThat(playback).doesNotExist();
    assertThat(otherPlayback).exists();
  }
}
