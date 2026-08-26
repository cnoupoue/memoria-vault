package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OriginalFileOpenService {

  private static final Logger LOGGER = LoggerFactory.getLogger(OriginalFileOpenService.class);

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;
  private final LocalFileOpener localFileOpener;

  public OriginalFileOpenService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver,
      LocalFileOpener localFileOpener) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
    this.localFileOpener = localFileOpener;
  }

  public void openOriginal(String memoryId) {
    SnapMemory memory =
        snapMemoryRepository
            .findById(memoryId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));

    Path originalPath =
        secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath());

    localFileOpener.open(originalPath);
  }

  public void revealOriginal(String memoryId) {
    Path originalPath = resolveAvailableOriginalPath(memoryId);

    localFileOpener.reveal(originalPath);
  }

  public void shareOriginal(String memoryId) {
    Path originalPath = resolveAvailableOriginalPath(memoryId);

    if (localFileOpener.supportsNativeShare()) {
      localFileOpener.share(originalPath);
      return;
    }

    LOGGER.info(
        "Native share is unavailable; revealing original file {} as share fallback.", originalPath);
    localFileOpener.reveal(originalPath);
  }

  private Path resolveAvailableOriginalPath(String memoryId) {
    SnapMemory memory =
        snapMemoryRepository
            .findById(memoryId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));

    Path originalPath =
        secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath());

    if (!Files.exists(originalPath)) {
      LOGGER.warn("Original file for memory {} no longer exists at {}.", memoryId, originalPath);
      throw new OriginalFileActionException(
          "ORIGINAL_FILE_MISSING", "The original file cannot be found.");
    }

    if (!Files.isRegularFile(originalPath)) {
      LOGGER.warn(
          "Original file path for memory {} is not a regular file: {}.", memoryId, originalPath);
      throw new OriginalFileActionException(
          "ORIGINAL_FILE_UNAVAILABLE", "The original file cannot be opened from its location.");
    }

    if (!Files.isReadable(originalPath)) {
      LOGGER.warn("Original file for memory {} is not readable: {}.", memoryId, originalPath);
      throw new OriginalFileActionException(
          "ORIGINAL_FILE_ACCESS_DENIED",
          "Memoria Vault does not have permission to access the original file.");
    }

    return originalPath.toAbsolutePath().normalize();
  }
}
