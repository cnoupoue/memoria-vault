package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.indexing.MemoryIndexPersistence;
import be.cnoupoue.memoriavault.indexing.MemoryScanJobRepository;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.source.api.CreateMemorySourceRequest;
import be.cnoupoue.memoriavault.source.api.MemorySourceResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemorySourceServiceTest {

  @Mock private MemorySourceRepository memorySourceRepository;
  @Mock private MemoryIndexPersistence memoryIndexPersistence;
  @Mock private MemoryScanJobRepository memoryScanJobRepository;
  @Mock private SnapMemoryRepository snapMemoryRepository;

  @TempDir private Path temporaryDirectory;

  @Test
  void createsSourceWithNormalizedPathAndAvailability() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    Path rawPath = temporaryDirectory.resolve("snapchat-memories").resolve("..").resolve(".");
    String normalizedPath = rawPath.toAbsolutePath().normalize().toString();
    ArgumentCaptor<MemorySource> sourceCaptor = ArgumentCaptor.forClass(MemorySource.class);

    when(memorySourceRepository.existsByRootPath(normalizedPath)).thenReturn(false);
    when(memorySourceRepository.save(any(MemorySource.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MemorySourceResponse response =
        service.create(new CreateMemorySourceRequest(" Snapchat USB ", rawPath.toString()));

    verify(memorySourceRepository).save(sourceCaptor.capture());
    assertThat(sourceCaptor.getValue().getName()).isEqualTo("Snapchat USB");
    assertThat(sourceCaptor.getValue().getRootPath()).isEqualTo(normalizedPath);
    assertThat(sourceCaptor.getValue().getLastScanStatus()).isEqualTo("NOT_SCANNED");
    assertThat(response.rootPath()).isEqualTo(normalizedPath);
    assertThat(response.availabilityStatus()).isEqualTo("AVAILABLE");
  }

  @Test
  void rejectsDuplicateSourcePath() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    String normalizedPath = temporaryDirectory.toAbsolutePath().normalize().toString();

    when(memorySourceRepository.existsByRootPath(normalizedPath)).thenReturn(true);

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateMemorySourceRequest("Snapchat USB", temporaryDirectory.toString())))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("A source already exists for this path.");
  }

  @Test
  void returnsSourcesWithCurrentAvailability() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findAll()).thenReturn(List.of(source));
    when(snapMemoryRepository.countBySourceIdAndIsFavoriteTrue(source.getId())).thenReturn(2L);

    List<MemorySourceResponse> sources = service.findAll();

    assertThat(sources).hasSize(1);
    assertThat(sources.getFirst().availabilityStatus()).isEqualTo("AVAILABLE");
    assertThat(sources.getFirst().favoriteCount()).isEqualTo(2);
  }

  @Test
  void deletesSourceAndRelatedIndexedDataInSafeOrder() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-delete", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    service.delete(source.getId());

    InOrder inOrder =
        inOrder(memoryScanJobRepository, memoryIndexPersistence, memorySourceRepository);
    inOrder.verify(memoryScanJobRepository).deleteOrphaned();
    inOrder.verify(memoryIndexPersistence).deleteOrphaned();
    inOrder.verify(memoryScanJobRepository).deleteBySourceId(source.getId());
    inOrder.verify(memoryIndexPersistence).deleteBySourceId(source.getId());
    inOrder.verify(memorySourceRepository).deleteById(source.getId());
  }

  @Test
  void exportsFavoritesBackupWithStableIdentifiersAndFavoriteDates() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);
    SnapMemory favorite =
        memory(
            "memory-1",
            source.getId(),
            "external-1",
            "2024-01-02",
            SnapMemoryType.IMAGE,
            "/local/export/memory.jpg",
            true,
            "2026-07-18T10:00:00Z");

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndIsFavoriteTrue(source.getId()))
        .thenReturn(List.of(favorite));

    var backup = service.exportFavoritesBackup(source.getId());

    assertThat(backup.version()).isEqualTo(1);
    assertThat(backup.sourceId()).isEqualTo(source.getId());
    assertThat(backup.favorites()).hasSize(1);
    assertThat(backup.favorites().getFirst().memoryId()).isEqualTo("memory-1");
    assertThat(backup.favorites().getFirst().externalMemoryId()).isEqualTo("external-1");
    assertThat(backup.favorites().getFirst().favoritedAt()).isEqualTo("2026-07-18T10:00:00Z");
    assertThat(backup.favorites().getFirst().mainPath()).isEqualTo("/local/export/memory.jpg");
  }

  @Test
  void exportsEmptyFavoritesBackup() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository,
            snapMemoryRepository);
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(snapMemoryRepository.findBySourceIdAndIsFavoriteTrue(source.getId()))
        .thenReturn(List.of());

    var backup = service.exportFavoritesBackup(source.getId());

    assertThat(backup.version()).isEqualTo(1);
    assertThat(backup.sourceId()).isEqualTo(source.getId());
    assertThat(backup.favorites()).isEmpty();
  }

  private MemorySource source(String id, Path rootPath) {
    String now = Instant.now().toString();

    return new MemorySource(
        id,
        "Snapchat USB",
        rootPath.toAbsolutePath().normalize().toString(),
        null,
        "NOT_SCANNED",
        now,
        now);
  }

  private SnapMemory memory(
      String id,
      String sourceId,
      String externalMemoryId,
      String capturedAt,
      SnapMemoryType mediaType,
      String mainPath,
      boolean isFavorite,
      String favoritedAt) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        sourceId,
        externalMemoryId,
        capturedAt,
        mediaType,
        mainPath,
        null,
        123,
        now,
        now,
        now,
        isFavorite,
        favoritedAt);
  }
}
