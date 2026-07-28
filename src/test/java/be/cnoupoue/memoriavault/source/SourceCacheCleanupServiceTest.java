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
  void deletesExistingThumbnailsAndIgnoresAlreadyMissingCacheFiles() throws Exception {
    Path thumbnailDirectory = Files.createDirectory(temporaryDirectory.resolve("thumbnails"));
    Path thumbnail = Files.writeString(thumbnailDirectory.resolve("memory-1.jpg"), "thumbnail");
    SourceCacheCleanupService service =
        new SourceCacheCleanupService(thumbnailDirectory.toString());

    service.deleteApplicationCaches("source-1", List.of("memory-1", "memory-missing"));

    assertThat(thumbnail).doesNotExist();
  }
}
