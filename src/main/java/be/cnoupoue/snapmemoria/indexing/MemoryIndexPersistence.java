package be.cnoupoue.snapmemoria.indexing;

import be.cnoupoue.snapmemoria.memory.SnapMemory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MemoryIndexPersistence {

  @PersistenceContext private EntityManager entityManager;

  @Transactional
  public void deleteBySourceId(String sourceId) {
    entityManager
        .createQuery(
            """
                DELETE FROM SnapMemory memory
                WHERE memory.sourceId = :sourceId
                """)
        .setParameter("sourceId", sourceId)
        .executeUpdate();
  }

  @Transactional
  public void saveBatch(List<SnapMemory> memories) {
    for (SnapMemory memory : memories) {
      entityManager.persist(memory);
    }

    entityManager.flush();
    entityManager.clear();
  }

  @Transactional
  public boolean attachOverlay(
      String sourceId, String externalMemoryId, String overlayPath, String updatedAt) {
    int updatedRows =
        entityManager
            .createQuery(
                """
                UPDATE SnapMemory memory
                SET memory.overlayPath = :overlayPath,
                    memory.updatedAt = :updatedAt
                WHERE memory.sourceId = :sourceId
                  AND memory.externalMemoryId = :externalMemoryId
                """)
            .setParameter("sourceId", sourceId)
            .setParameter("externalMemoryId", externalMemoryId)
            .setParameter("overlayPath", overlayPath)
            .setParameter("updatedAt", updatedAt)
            .executeUpdate();

    return updatedRows == 1;
  }
}
