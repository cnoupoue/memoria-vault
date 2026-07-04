package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import java.nio.file.Path;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OriginalFileOpenService {

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
}
