package be.cnoupoue.snapmemoria.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemorySourceRepository extends JpaRepository<MemorySource, String> {

    boolean existsByRootPath(String rootPath);

    Optional<MemorySource> findByRootPath(String rootPath);
}