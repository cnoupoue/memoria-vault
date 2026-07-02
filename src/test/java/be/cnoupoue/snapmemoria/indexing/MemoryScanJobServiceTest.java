package be.cnoupoue.snapmemoria.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import be.cnoupoue.snapmemoria.source.MemorySource;
import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import be.cnoupoue.snapmemoria.source.SourceAvailabilityService;
import be.cnoupoue.snapmemoria.source.SourceUnavailableException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class MemoryScanJobServiceTest {

  @Mock private MemorySourceRepository memorySourceRepository;
  @Mock private MemoryScanJobRepository memoryScanJobRepository;
  @Mock private MemoryScanWorker memoryScanWorker;

  @TempDir private Path temporaryDirectory;

  @Test
  void startsScanForAvailableSource() {
    MemorySource source = source("source-1", temporaryDirectory);
    MemoryScanJobService service = service();
    ArgumentCaptor<MemoryScanJob> jobCaptor = ArgumentCaptor.forClass(MemoryScanJob.class);

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(memoryScanJobRepository.existsBySourceIdAndStatus(source.getId(), "RUNNING"))
        .thenReturn(false);
    when(memoryScanJobRepository.save(any(MemoryScanJob.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    MemoryScanJob scanJob = service.startScan(source.getId());

    verify(memoryScanJobRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getSourceId()).isEqualTo(source.getId());
    assertThat(jobCaptor.getValue().getStatus()).isEqualTo("RUNNING");
    verify(memoryScanWorker).execute(scanJob.getId());
  }

  @Test
  void blocksScanWhenSourceIsUnavailable() {
    Path missingPath = temporaryDirectory.resolve("missing");
    MemorySource source = source("source-missing", missingPath);
    MemoryScanJobService service = service();

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));

    assertThatThrownBy(() -> service.startScan(source.getId()))
        .isInstanceOf(SourceUnavailableException.class);
    verify(memoryScanJobRepository, never()).save(any());
    verify(memoryScanWorker, never()).execute(any());
  }

  @Test
  void blocksScanWhenAnotherScanIsRunning() {
    MemorySource source = source("source-running", temporaryDirectory);
    MemoryScanJobService service = service();

    when(memorySourceRepository.findById(source.getId())).thenReturn(Optional.of(source));
    when(memoryScanJobRepository.existsBySourceIdAndStatus(source.getId(), "RUNNING"))
        .thenReturn(true);

    assertThatThrownBy(() -> service.startScan(source.getId()))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.CONFLICT));
    verify(memoryScanJobRepository, never()).save(any());
  }

  @Test
  void returnsLatestScanOnlyForExistingSource() {
    MemorySource source = source("source-latest", temporaryDirectory);
    MemoryScanJob scanJob = new MemoryScanJob("scan-1", source.getId(), Instant.now().toString());
    MemoryScanJobService service = service();

    when(memorySourceRepository.existsById(source.getId())).thenReturn(true);
    when(memoryScanJobRepository.findTopBySourceIdOrderByStartedAtDesc(source.getId()))
        .thenReturn(Optional.of(scanJob));

    assertThat(service.findLatestBySourceId(source.getId())).isSameAs(scanJob);
  }

  @Test
  void reportsMissingSourceWhenFindingLatestScan() {
    MemoryScanJobService service = service();

    when(memorySourceRepository.existsById("missing")).thenReturn(false);

    assertThatThrownBy(() -> service.findLatestBySourceId("missing"))
        .isInstanceOf(ResponseStatusException.class)
        .satisfies(
            exception ->
                assertThat(((ResponseStatusException) exception).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND));
  }

  private MemoryScanJobService service() {
    return new MemoryScanJobService(
        memorySourceRepository,
        memoryScanJobRepository,
        memoryScanWorker,
        new SourceAvailabilityService());
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
