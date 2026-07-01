package be.cnoupoue.snapmemoria.indexing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryScanJobRepository extends JpaRepository<MemoryScanJob, String> {

  boolean existsBySourceIdAndStatus(String sourceId, String status);

  Optional<MemoryScanJob> findTopBySourceIdOrderByStartedAtDesc(String sourceId);
}
