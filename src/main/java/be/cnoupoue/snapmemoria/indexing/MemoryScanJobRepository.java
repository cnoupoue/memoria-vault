package be.cnoupoue.snapmemoria.indexing;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemoryScanJobRepository extends JpaRepository<MemoryScanJob, String> {

  boolean existsBySourceIdAndStatus(String sourceId, String status);

  Optional<MemoryScanJob> findTopBySourceIdOrderByStartedAtDesc(String sourceId);

  long countBySourceId(String sourceId);

  void deleteBySourceId(String sourceId);

  @Modifying
  @Query(
      """
      DELETE FROM MemoryScanJob job
      WHERE job.sourceId NOT IN (
          SELECT source.id
          FROM MemorySource source
      )
      """)
  void deleteOrphaned();
}
