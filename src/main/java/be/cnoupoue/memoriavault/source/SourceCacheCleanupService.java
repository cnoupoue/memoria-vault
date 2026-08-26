package be.cnoupoue.memoriavault.source;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SourceCacheCleanupService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SourceCacheCleanupService.class);

  private final Path thumbnailDirectory;
  private final Path playbackDirectory;

  public SourceCacheCleanupService(
      @Value("${memoriavault.thumbnail.directory}") String thumbnailDirectory,
      @Value("${memoriavault.playback.directory}") String playbackDirectory) {
    this.thumbnailDirectory = Path.of(thumbnailDirectory).toAbsolutePath().normalize();
    this.playbackDirectory = Path.of(playbackDirectory).toAbsolutePath().normalize();
  }

  public void deleteApplicationCaches(String sourceId, List<String> memoryIds) {
    for (String memoryId : memoryIds) {
      deleteThumbnail(sourceId, memoryId);
      deletePlaybackFiles(sourceId, memoryId);
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

  private void deletePlaybackFiles(String sourceId, String memoryId) {
    if (!Files.isDirectory(playbackDirectory)) {
      return;
    }

    try (Stream<Path> paths = Files.list(playbackDirectory)) {
      paths
          .filter(path -> path.getFileName().toString().startsWith(memoryId + "-"))
          .filter(path -> path.getFileName().toString().endsWith(".mp4"))
          .forEach(path -> deletePlaybackFile(sourceId, memoryId, path));
    } catch (IOException exception) {
      LOGGER.warn(
          "Could not list playback cache for source {} and memory {}.",
          sourceId,
          memoryId,
          exception);
    }
  }

  private void deletePlaybackFile(String sourceId, String memoryId, Path playbackPath) {
    try {
      Files.deleteIfExists(playbackPath);
    } catch (IOException exception) {
      LOGGER.warn(
          "Could not delete playback cache for source {} and memory {}.",
          sourceId,
          memoryId,
          exception);
    }
  }
}
