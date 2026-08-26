package be.cnoupoue.memoriavault.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.source.SourceCacheCleanupService;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver.ResolvedMemoryPath;
import be.cnoupoue.memoriavault.web.ApiException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class MemoryDeletionServiceTest {

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Mock private SecureMemoryPathResolver secureMemoryPathResolver;

  @Mock private SourceCacheCleanupService sourceCacheCleanupService;

  @TempDir private Path temporaryDirectory;

  @Test
  void deletesImageOriginalThenRemovesIndexAndCache() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.jpg"), "image");
    MemoryDeletionService service = serviceWithRealFileDeleter();
    SnapMemory memory = memory("memory-image", SnapMemoryType.IMAGE, original, false);

    when(snapMemoryRepository.findById("memory-image")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", original.toString()))
        .thenReturn(new ResolvedMemoryPath(original, true));

    service.deleteMemory("memory-image", new DeleteMemoryRequest(true));

    assertThat(original).doesNotExist();
    verify(snapMemoryRepository).delete(memory);
    verify(sourceCacheCleanupService).deleteApplicationCaches("source-1", List.of("memory-image"));
  }

  @Test
  void deletesVideoOriginalThenRemovesIndexAndCache() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.mp4"), "video");
    MemoryDeletionService service = serviceWithRealFileDeleter();
    SnapMemory memory = memory("memory-video", SnapMemoryType.VIDEO, original, false);

    when(snapMemoryRepository.findById("memory-video")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", original.toString()))
        .thenReturn(new ResolvedMemoryPath(original, true));

    service.deleteMemory("memory-video", new DeleteMemoryRequest(true));

    assertThat(original).doesNotExist();
    verify(snapMemoryRepository).delete(memory);
    verify(sourceCacheCleanupService).deleteApplicationCaches("source-1", List.of("memory-video"));
  }

  @Test
  void requiresExplicitConfirmationBeforeDeletingAnything() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.jpg"), "image");
    MemoryDeletionService service = serviceWithRealFileDeleter();

    assertThatThrownBy(() -> service.deleteMemory("memory-image", new DeleteMemoryRequest(false)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getStatus())
                    .isEqualTo(HttpStatus.BAD_REQUEST))
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getCode())
                    .isEqualTo("DELETE_CONFIRMATION_REQUIRED"));

    assertThat(original).exists();
    verify(snapMemoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void cleansIndexedMemoryWhenOriginalFileIsAlreadyMissing() throws Exception {
    Path missingOriginal = temporaryDirectory.resolve("missing-main.jpg");
    MemoryDeletionService service =
        new MemoryDeletionService(
            snapMemoryRepository,
            secureMemoryPathResolver,
            sourceCacheCleanupService,
            path -> {
              throw new AssertionError("Missing originals should not be deleted again.");
            });
    SnapMemory memory = memory("memory-missing", SnapMemoryType.IMAGE, missingOriginal, false);

    when(snapMemoryRepository.findById("memory-missing")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", missingOriginal.toString()))
        .thenReturn(new ResolvedMemoryPath(missingOriginal, false));

    service.deleteMemory("memory-missing", new DeleteMemoryRequest(true));

    verify(snapMemoryRepository).delete(memory);
    verify(sourceCacheCleanupService)
        .deleteApplicationCaches("source-1", List.of("memory-missing"));
  }

  @Test
  void keepsIndexAndCacheWhenPermissionDenied() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.jpg"), "image");
    MemoryDeletionService service =
        new MemoryDeletionService(
            snapMemoryRepository,
            secureMemoryPathResolver,
            sourceCacheCleanupService,
            path -> {
              throw new AccessDeniedException(path.toString());
            });
    SnapMemory memory = memory("memory-denied", SnapMemoryType.IMAGE, original, false);

    when(snapMemoryRepository.findById("memory-denied")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", original.toString()))
        .thenReturn(new ResolvedMemoryPath(original, true));

    assertThatThrownBy(() -> service.deleteMemory("memory-denied", new DeleteMemoryRequest(true)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.FORBIDDEN))
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getCode())
                    .isEqualTo("MEMORY_DELETE_PERMISSION_DENIED"));

    verify(snapMemoryRepository, never()).delete(memory);
    verify(sourceCacheCleanupService, never())
        .deleteApplicationCaches(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void keepsIndexAndCacheWhenOperatingSystemDeletionFails() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.mp4"), "video");
    MemoryDeletionService service =
        new MemoryDeletionService(
            snapMemoryRepository,
            secureMemoryPathResolver,
            sourceCacheCleanupService,
            path -> {
              throw new IOException("file locked");
            });
    SnapMemory memory = memory("memory-locked", SnapMemoryType.VIDEO, original, false);

    when(snapMemoryRepository.findById("memory-locked")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", original.toString()))
        .thenReturn(new ResolvedMemoryPath(original, true));

    assertThatThrownBy(() -> service.deleteMemory("memory-locked", new DeleteMemoryRequest(true)))
        .isInstanceOf(ApiException.class)
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getStatus()).isEqualTo(HttpStatus.CONFLICT))
        .satisfies(
            exception ->
                assertThat(((ApiException) exception).getCode()).isEqualTo("MEMORY_DELETE_FAILED"));

    verify(snapMemoryRepository, never()).delete(memory);
    verify(sourceCacheCleanupService, never())
        .deleteApplicationCaches(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
  }

  @Test
  void deletingFavoritedMemoryRemovesFavoriteWithIndexedMemory() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("memory-main.jpg"), "image");
    MemoryDeletionService service = serviceWithRealFileDeleter();
    SnapMemory memory = memory("memory-favorite", SnapMemoryType.IMAGE, original, true);

    when(snapMemoryRepository.findById("memory-favorite")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolveForDeletion("source-1", original.toString()))
        .thenReturn(new ResolvedMemoryPath(original, true));

    service.deleteMemory("memory-favorite", new DeleteMemoryRequest(true));

    assertThat(memory.isFavorite()).isTrue();
    verify(snapMemoryRepository).delete(memory);
  }

  private MemoryDeletionService serviceWithRealFileDeleter() {
    return new MemoryDeletionService(
        snapMemoryRepository,
        secureMemoryPathResolver,
        sourceCacheCleanupService,
        new DefaultOriginalMediaFileDeleter());
  }

  private SnapMemory memory(String id, SnapMemoryType mediaType, Path mainPath, boolean favorite) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        "source-1",
        id + "-external",
        "2026-01-01",
        mediaType,
        mainPath.toString(),
        null,
        123,
        now,
        now,
        now,
        favorite,
        favorite ? now : null);
  }
}
