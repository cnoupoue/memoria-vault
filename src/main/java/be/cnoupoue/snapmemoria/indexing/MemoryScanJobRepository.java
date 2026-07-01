package be.cnoupoue.snapmemoria.indexing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryScanJobRepository
        extends JpaRepository<MemoryScanJob, String> {

    boolean existsBySourceIdAndStatus(String sourceId, String status);
}