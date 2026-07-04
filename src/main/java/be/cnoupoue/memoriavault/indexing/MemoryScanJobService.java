package be.cnoupoue.memoriavault.indexing;

import be.cnoupoue.memoriavault.source.MemorySource;
import be.cnoupoue.memoriavault.source.MemorySourceRepository;
import be.cnoupoue.memoriavault.source.SourceAvailability;
import be.cnoupoue.memoriavault.source.SourceAvailabilityService;
import be.cnoupoue.memoriavault.source.SourceUnavailableException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemoryScanJobService {

  private final MemorySourceRepository memorySourceRepository;
  private final MemoryScanJobRepository memoryScanJobRepository;
  private final MemoryScanWorker memoryScanWorker;
  private final SourceAvailabilityService sourceAvailabilityService;

  public MemoryScanJobService(
      MemorySourceRepository memorySourceRepository,
      MemoryScanJobRepository memoryScanJobRepository,
      MemoryScanWorker memoryScanWorker,
      SourceAvailabilityService sourceAvailabilityService) {
    this.memorySourceRepository = memorySourceRepository;
    this.memoryScanJobRepository = memoryScanJobRepository;
    this.memoryScanWorker = memoryScanWorker;
    this.sourceAvailabilityService = sourceAvailabilityService;
  }

  public MemoryScanJob startScan(String sourceId) {
    MemorySource source =
        memorySourceRepository
            .findById(sourceId)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory source not found."));

    SourceAvailability availability = sourceAvailabilityService.check(source);

    if (!availability.isAvailable()) {
      throw new SourceUnavailableException();
    }

    if (memoryScanJobRepository.existsBySourceIdAndStatus(sourceId, "RUNNING")) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A scan is already running for this source.");
    }

    MemoryScanJob scanJob =
        new MemoryScanJob(UUID.randomUUID().toString(), sourceId, Instant.now().toString());

    MemoryScanJob savedScanJob = memoryScanJobRepository.save(scanJob);

    memoryScanWorker.execute(savedScanJob.getId());

    return savedScanJob;
  }

  public MemoryScanJob findById(String scanJobId) {
    return memoryScanJobRepository
        .findById(scanJobId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Scan job not found."));
  }

  public MemoryScanJob findLatestBySourceId(String sourceId) {
    if (!memorySourceRepository.existsById(sourceId)) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory source not found.");
    }

    return memoryScanJobRepository
        .findTopBySourceIdOrderByStartedAtDesc(sourceId)
        .orElseThrow(
            () ->
                new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "No scan job found for this source."));
  }
}
