package be.cnoupoue.snapmemoria.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.snapmemoria.indexing.MemoryIndexPersistence;
import be.cnoupoue.snapmemoria.indexing.MemoryScanJobRepository;
import be.cnoupoue.snapmemoria.source.api.CreateMemorySourceRequest;
import be.cnoupoue.snapmemoria.source.api.MemorySourceResponse;
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

  @TempDir private Path temporaryDirectory;

  @Test
  void createsSourceWithNormalizedPathAndAvailability() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository);
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
            memoryScanJobRepository);
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
            memoryScanJobRepository);
    MemorySource source = source("source-1", temporaryDirectory);

    when(memorySourceRepository.findAll()).thenReturn(List.of(source));

    List<MemorySourceResponse> sources = service.findAll();

    assertThat(sources).hasSize(1);
    assertThat(sources.getFirst().availabilityStatus()).isEqualTo("AVAILABLE");
  }

  @Test
  void deletesSourceAndRelatedIndexedDataInSafeOrder() {
    SourceAvailabilityService availabilityService = new SourceAvailabilityService();
    MemorySourceService service =
        new MemorySourceService(
            memorySourceRepository,
            availabilityService,
            memoryIndexPersistence,
            memoryScanJobRepository);
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
}
