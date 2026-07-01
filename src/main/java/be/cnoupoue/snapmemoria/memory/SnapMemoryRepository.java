package be.cnoupoue.snapmemoria.memory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SnapMemoryRepository extends JpaRepository<SnapMemory, String> {

    Page<SnapMemory> findByCapturedAtStartingWith(
            String capturedAtPrefix,
            Pageable pageable
    );
}