package be.cnoupoue.memoriavault.streaming;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.playback.CompatibilityPlaybackService;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemoryMediaService {

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;
  private final CompatibilityPlaybackService compatibilityPlaybackService;

  public MemoryMediaService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver,
      CompatibilityPlaybackService compatibilityPlaybackService) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
    this.compatibilityPlaybackService = compatibilityPlaybackService;
  }

  public FileSystemResource getMainMedia(String memoryId) {
    SnapMemory memory = findMemory(memoryId);

    return createSecureResource(memory.getSourceId(), memory.getMainPath());
  }

  public FileSystemResource getOverlay(String memoryId) {
    SnapMemory memory = findMemory(memoryId);

    if (memory.getOverlayPath() == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "This memory does not have an overlay.");
    }

    return createSecureResource(memory.getSourceId(), memory.getOverlayPath());
  }

  public FileSystemResource getCompatiblePlaybackMedia(String memoryId) {
    return compatibilityPlaybackService.getCompatiblePlaybackMedia(memoryId);
  }

  private SnapMemory findMemory(String memoryId) {
    return snapMemoryRepository
        .findById(memoryId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));
  }

  private FileSystemResource createSecureResource(String sourceId, String storedMediaPath) {
    Path mediaPath = secureMemoryPathResolver.resolve(sourceId, storedMediaPath);

    return new FileSystemResource(mediaPath);
  }
}
