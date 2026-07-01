package be.cnoupoue.snapmemoria.indexing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemoryScanJobRepository
        extends JpaRepository<MemoryScanJob, String> {

    boolean existsBySourceIdAndStatus(String sourceId, String status);

    Optional<MemoryScanJob> findTopBySourceIdOrderByStartedAtDesc(
            String sourceId
    );
}