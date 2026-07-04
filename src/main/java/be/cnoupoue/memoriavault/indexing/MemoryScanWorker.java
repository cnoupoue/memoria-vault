package be.cnoupoue.memoriavault.indexing;

import java.time.Instant;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class MemoryScanWorker {

  private final MemoryScanJobRepository memoryScanJobRepository;
  private final MemorySourceScanner memorySourceScanner;

  public MemoryScanWorker(
      MemoryScanJobRepository memoryScanJobRepository, MemorySourceScanner memorySourceScanner) {
    this.memoryScanJobRepository = memoryScanJobRepository;
    this.memorySourceScanner = memorySourceScanner;
  }

  @Async("memoryScanExecutor")
  public void execute(String scanJobId) {
    MemoryScanJob scanJob =
        memoryScanJobRepository
            .findById(scanJobId)
            .orElseThrow(() -> new IllegalStateException("Scan job not found."));

    try {
      ScanProgress finalProgress =
          memorySourceScanner.scan(
              scanJob.getSourceId(), progress -> updateProgress(scanJobId, progress));

      markCompleted(scanJobId, finalProgress);
    } catch (RuntimeException exception) {
      markFailed(scanJobId, exception);
    }
  }

  private void updateProgress(String scanJobId, ScanProgress progress) {
    MemoryScanJob scanJob =
        memoryScanJobRepository
            .findById(scanJobId)
            .orElseThrow(() -> new IllegalStateException("Scan job not found."));

    scanJob.updateProgress(progress, Instant.now().toString());

    memoryScanJobRepository.save(scanJob);
  }

  private void markCompleted(String scanJobId, ScanProgress finalProgress) {
    MemoryScanJob scanJob =
        memoryScanJobRepository
            .findById(scanJobId)
            .orElseThrow(() -> new IllegalStateException("Scan job not found."));

    scanJob.markCompleted(finalProgress, Instant.now().toString());

    memoryScanJobRepository.save(scanJob);
  }

  private void markFailed(String scanJobId, RuntimeException exception) {
    MemoryScanJob scanJob =
        memoryScanJobRepository
            .findById(scanJobId)
            .orElseThrow(() -> new IllegalStateException("Scan job not found."));

    scanJob.markFailed(toUserFacingMessage(exception), Instant.now().toString());

    memoryScanJobRepository.save(scanJob);
  }

  private String toUserFacingMessage(RuntimeException exception) {
    String message = exception.getMessage();

    if (message == null || message.isBlank()) {
      return "The scan failed unexpectedly.";
    }

    return message;
  }
}
