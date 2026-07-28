package be.cnoupoue.memoriavault.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SourceCacheCleanupService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SourceCacheCleanupService.class);

  private final Path thumbnailDirectory;

  public SourceCacheCleanupService(
      @Value("${memoriavault.thumbnail.directory}") String thumbnailDirectory) {
    this.thumbnailDirectory = Path.of(thumbnailDirectory).toAbsolutePath().normalize();
  }

  public void deleteApplicationCaches(String sourceId, List<String> memoryIds) {
    for (String memoryId : memoryIds) {
      deleteThumbnail(sourceId, memoryId);
    }
  }

  private void deleteThumbnail(String sourceId, String memoryId) {
    try {
      Files.deleteIfExists(thumbnailDirectory.resolve(memoryId + ".jpg"));
    } catch (IOException exception) {
      LOGGER.warn(
          "Could not delete thumbnail cache for source {} and memory {}.",
          sourceId,
          memoryId,
          exception);
    }
  }
}
