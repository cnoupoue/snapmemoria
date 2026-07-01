package be.cnoupoue.snapmemoria.source;

import be.cnoupoue.snapmemoria.source.api.CreateMemorySourceRequest;
import be.cnoupoue.snapmemoria.source.api.MemorySourceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MemorySourceService {

    private final MemorySourceRepository memorySourceRepository;

    public MemorySourceService(MemorySourceRepository memorySourceRepository) {
        this.memorySourceRepository = memorySourceRepository;
    }

    public MemorySourceResponse create(CreateMemorySourceRequest request) {
        String normalizedPath = normalizePath(request.rootPath());

        if (memorySourceRepository.existsByRootPath(normalizedPath)) {
            throw new IllegalArgumentException("A source already exists for this path.");
        }

        String now = Instant.now().toString();

        MemorySource source = new MemorySource(
                UUID.randomUUID().toString(),
                request.name().trim(),
                normalizedPath,
                null,
                "NOT_SCANNED",
                now,
                now
        );

        MemorySource savedSource = memorySourceRepository.save(source);

        return toResponse(savedSource);
    }

    @Transactional(readOnly = true)
    public List<MemorySourceResponse> findAll() {
        return memorySourceRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private String normalizePath(String rawPath) {
        return Path.of(rawPath)
                .toAbsolutePath()
                .normalize()
                .toString();
    }

    private MemorySourceResponse toResponse(MemorySource source) {
        return new MemorySourceResponse(
                source.getId(),
                source.getName(),
                source.getRootPath(),
                source.getLastScanAt(),
                source.getLastScanStatus(),
                source.getCreatedAt(),
                source.getUpdatedAt()
        );
    }
}