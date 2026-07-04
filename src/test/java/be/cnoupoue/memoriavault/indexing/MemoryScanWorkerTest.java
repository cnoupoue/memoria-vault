package be.cnoupoue.memoriavault.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemoryScanWorkerTest {

  @Mock private MemoryScanJobRepository memoryScanJobRepository;
  @Mock private MemorySourceScanner memorySourceScanner;

  @Test
  void updatesProgressAndMarksJobCompleted() {
    MemoryScanJob job = new MemoryScanJob("scan-1", "source-1", Instant.now().toString());
    MemoryScanWorker worker = new MemoryScanWorker(memoryScanJobRepository, memorySourceScanner);
    ArgumentCaptor<MemoryScanJob> savedJobs = ArgumentCaptor.forClass(MemoryScanJob.class);
    ScanProgress finalProgress = new ScanProgress(3, 3, 1, 1, 1, 2, 1, 0, 0, 0);

    when(memoryScanJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(memorySourceScanner.scan(any(), any()))
        .thenAnswer(
            invocation -> {
              invocation
                  .<java.util.function.Consumer<ScanProgress>>getArgument(1)
                  .accept(new ScanProgress(3, 1, 1, 0, 0, 1, 0, 0, 0, 0));
              return finalProgress;
            });

    worker.execute(job.getId());

    verify(memorySourceScanner).scan(eq("source-1"), any());
    verify(memoryScanJobRepository, org.mockito.Mockito.atLeastOnce()).save(savedJobs.capture());
    assertThat(savedJobs.getAllValues().getLast().getStatus()).isEqualTo("COMPLETED");
    assertThat(savedJobs.getAllValues().getLast().getFilesProcessed()).isEqualTo(3);
    assertThat(savedJobs.getAllValues().getLast().getIndexedMemories()).isEqualTo(2);
  }

  @Test
  void marksJobFailedWithFallbackMessageWhenScannerFailsWithoutMessage() {
    MemoryScanJob job = new MemoryScanJob("scan-failed", "source-1", Instant.now().toString());
    MemoryScanWorker worker = new MemoryScanWorker(memoryScanJobRepository, memorySourceScanner);
    ArgumentCaptor<MemoryScanJob> savedJob = ArgumentCaptor.forClass(MemoryScanJob.class);

    when(memoryScanJobRepository.findById(job.getId())).thenReturn(Optional.of(job));
    when(memorySourceScanner.scan(any(), any())).thenThrow(new RuntimeException());

    worker.execute(job.getId());

    verify(memoryScanJobRepository).save(savedJob.capture());
    assertThat(savedJob.getValue().getStatus()).isEqualTo("FAILED");
    assertThat(savedJob.getValue().getErrorMessage()).isEqualTo("The scan failed unexpectedly.");
    assertThat(savedJob.getValue().getCompletedAt()).isNotBlank();
  }
}
