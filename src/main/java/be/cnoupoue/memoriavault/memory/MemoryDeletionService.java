package be.cnoupoue.memoriavault.memory;

import be.cnoupoue.memoriavault.source.SourceCacheCleanupService;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver.ResolvedMemoryPath;
import be.cnoupoue.memoriavault.web.ApiException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemoryDeletionService {

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;
  private final SourceCacheCleanupService sourceCacheCleanupService;
  private final OriginalMediaFileDeleter originalMediaFileDeleter;

  public MemoryDeletionService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver,
      SourceCacheCleanupService sourceCacheCleanupService,
      OriginalMediaFileDeleter originalMediaFileDeleter) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
    this.sourceCacheCleanupService = sourceCacheCleanupService;
    this.originalMediaFileDeleter = originalMediaFileDeleter;
  }

  @Transactional
  public void deleteMemory(String memoryId, DeleteMemoryRequest request) {
    if (request == null || !request.confirmPermanentDelete()) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "DELETE_CONFIRMATION_REQUIRED",
          "Confirm permanent deletion before deleting this memory.");
    }

    SnapMemory memory =
        snapMemoryRepository
            .findById(memoryId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));

    ResolvedMemoryPath resolvedPath =
        secureMemoryPathResolver.resolveForDeletion(memory.getSourceId(), memory.getMainPath());

    if (resolvedPath.exists()) {
      deleteOriginalFile(resolvedPath);
    }

    snapMemoryRepository.delete(memory);
    sourceCacheCleanupService.deleteApplicationCaches(
        memory.getSourceId(), List.of(memory.getId()));
  }

  private void deleteOriginalFile(ResolvedMemoryPath resolvedPath) {
    try {
      originalMediaFileDeleter.delete(resolvedPath.path());
    } catch (AccessDeniedException exception) {
      throw new ApiException(
          HttpStatus.FORBIDDEN,
          "MEMORY_DELETE_PERMISSION_DENIED",
          "Memoria Vault does not have permission to delete the original file.",
          exception);
    } catch (IOException exception) {
      throw new ApiException(
          HttpStatus.CONFLICT,
          "MEMORY_DELETE_FAILED",
          "The original file could not be deleted. It may be locked or in use.",
          exception);
    }
  }
}
