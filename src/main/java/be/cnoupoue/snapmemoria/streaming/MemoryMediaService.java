package be.cnoupoue.snapmemoria.streaming;

import be.cnoupoue.snapmemoria.memory.SnapMemory;
import be.cnoupoue.snapmemoria.memory.SnapMemoryRepository;
import java.nio.file.Path;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemoryMediaService {

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;

  public MemoryMediaService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
  }

  public FileSystemResource getMainMedia(String memoryId) {
    SnapMemory memory = findMemory(memoryId);

    return createSecureResource(
        memory.getSourceId(), memory.getMainPath(), "The memory media file is unavailable.");
  }

  public FileSystemResource getOverlay(String memoryId) {
    SnapMemory memory = findMemory(memoryId);

    if (memory.getOverlayPath() == null) {
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "This memory does not have an overlay.");
    }

    return createSecureResource(
        memory.getSourceId(), memory.getOverlayPath(), "The memory overlay file is unavailable.");
  }

  private SnapMemory findMemory(String memoryId) {
    return snapMemoryRepository
        .findById(memoryId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));
  }

  private FileSystemResource createSecureResource(
      String sourceId, String storedMediaPath, String unavailableMessage) {
    Path mediaPath =
        secureMemoryPathResolver.resolve(sourceId, storedMediaPath, unavailableMessage);

    return new FileSystemResource(mediaPath);
  }
}
