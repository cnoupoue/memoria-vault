package be.cnoupoue.snapmemoria.source;

import be.cnoupoue.snapmemoria.source.api.CreateMemorySourceRequest;
import be.cnoupoue.snapmemoria.source.api.MemorySourceResponse;
import be.cnoupoue.snapmemoria.source.api.SourceAvailabilityResponse;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MemorySourceService {

  private final MemorySourceRepository memorySourceRepository;
  private final SourceAvailabilityService sourceAvailabilityService;

  public MemorySourceService(
      MemorySourceRepository memorySourceRepository,
      SourceAvailabilityService sourceAvailabilityService) {
    this.memorySourceRepository = memorySourceRepository;
    this.sourceAvailabilityService = sourceAvailabilityService;
  }

  public MemorySourceResponse create(CreateMemorySourceRequest request) {
    String normalizedPath = normalizePath(request.rootPath());

    if (memorySourceRepository.existsByRootPath(normalizedPath)) {
      throw new IllegalArgumentException("A source already exists for this path.");
    }

    String now = Instant.now().toString();

    MemorySource source =
        new MemorySource(
            UUID.randomUUID().toString(),
            request.name().trim(),
            normalizedPath,
            null,
            "NOT_SCANNED",
            now,
            now);

    MemorySource savedSource = memorySourceRepository.save(source);

    return toResponse(savedSource);
  }

  @Transactional(readOnly = true)
  public List<MemorySourceResponse> findAll() {
    return memorySourceRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Transactional(readOnly = true)
  public SourceAvailabilityResponse checkAvailability(String sourceId) {
    MemorySource source = findById(sourceId);
    SourceAvailability availability = sourceAvailabilityService.check(source);

    return new SourceAvailabilityResponse(availability.status().name(), availability.message());
  }

  @Transactional(readOnly = true)
  public MemorySource findById(String sourceId) {
    return memorySourceRepository
        .findById(sourceId)
        .orElseThrow(() -> new IllegalArgumentException("Memory source not found."));
  }

  private String normalizePath(String rawPath) {
    return Path.of(rawPath).toAbsolutePath().normalize().toString();
  }

  private MemorySourceResponse toResponse(MemorySource source) {
    SourceAvailability availability = sourceAvailabilityService.check(source);

    return new MemorySourceResponse(
        source.getId(),
        source.getName(),
        source.getRootPath(),
        source.getLastScanAt(),
        source.getLastScanStatus(),
        availability.status().name(),
        availability.message(),
        source.getCreatedAt(),
        source.getUpdatedAt());
  }

  public void delete(String sourceId) {
    findById(sourceId);
    memorySourceRepository.deleteById(sourceId);
  }
}
