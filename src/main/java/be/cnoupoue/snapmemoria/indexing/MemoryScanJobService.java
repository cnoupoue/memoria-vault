package be.cnoupoue.snapmemoria.indexing;

import be.cnoupoue.snapmemoria.source.MemorySourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class MemoryScanJobService {

    private final MemorySourceRepository memorySourceRepository;
    private final MemoryScanJobRepository memoryScanJobRepository;
    private final MemoryScanWorker memoryScanWorker;

    public MemoryScanJobService(
            MemorySourceRepository memorySourceRepository,
            MemoryScanJobRepository memoryScanJobRepository,
            MemoryScanWorker memoryScanWorker
    ) {
        this.memorySourceRepository = memorySourceRepository;
        this.memoryScanJobRepository = memoryScanJobRepository;
        this.memoryScanWorker = memoryScanWorker;
    }

    public MemoryScanJob startScan(String sourceId) {
        if (!memorySourceRepository.existsById(sourceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Memory source not found."
            );
        }

        if (memoryScanJobRepository.existsBySourceIdAndStatus(sourceId, "RUNNING")) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A scan is already running for this source."
            );
        }

        MemoryScanJob scanJob = new MemoryScanJob(
                UUID.randomUUID().toString(),
                sourceId,
                Instant.now().toString()
        );

        MemoryScanJob savedScanJob = memoryScanJobRepository.save(scanJob);

        memoryScanWorker.execute(savedScanJob.getId());

        return savedScanJob;
    }

    public MemoryScanJob findById(String scanJobId) {
        return memoryScanJobRepository.findById(scanJobId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Scan job not found."
                ));
    }

    public MemoryScanJob findLatestBySourceId(String sourceId) {
        if (!memorySourceRepository.existsById(sourceId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Memory source not found."
            );
        }

        return memoryScanJobRepository
                .findTopBySourceIdOrderByStartedAtDesc(sourceId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No scan job found for this source."
                ));
    }
}