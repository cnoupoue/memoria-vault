package be.cnoupoue.memoriavault.source;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemorySourceRepository extends JpaRepository<MemorySource, String> {

  boolean existsByRootPath(String rootPath);

  Optional<MemorySource> findByRootPath(String rootPath);

  Optional<MemorySource> findById(String id);
}
