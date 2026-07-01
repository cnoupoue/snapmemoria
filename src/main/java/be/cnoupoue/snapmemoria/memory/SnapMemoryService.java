package be.cnoupoue.snapmemoria.memory;

import be.cnoupoue.snapmemoria.memory.api.MemoryPageResponse;
import be.cnoupoue.snapmemoria.memory.api.MemoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import be.cnoupoue.snapmemoria.memory.api.TimelineMonthResponse;
import be.cnoupoue.snapmemoria.memory.api.TimelineYearResponse;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SnapMemoryService {

    private static final int DEFAULT_PAGE_SIZE = 60;
    private static final int MAX_PAGE_SIZE = 100;

    private final SnapMemoryRepository snapMemoryRepository;

    public SnapMemoryService(SnapMemoryRepository snapMemoryRepository) {
        this.snapMemoryRepository = snapMemoryRepository;
    }

    public MemoryPageResponse findAll(
            Integer year,
            Integer month,
            int page,
            int size
    ) {
        validateDateFilter(year, month);

        int validatedPage = Math.max(page, 0);
        int validatedSize = Math.clamp(size, 1, MAX_PAGE_SIZE);

        PageRequest pageRequest = PageRequest.of(
                validatedPage,
                validatedSize,
                Sort.by(
                        Sort.Order.desc("capturedAt"),
                        Sort.Order.desc("createdAt")
                )
        );

        Page<SnapMemory> memoryPage;

        String capturedAtPrefix = buildCapturedAtPrefix(year, month);

        if (capturedAtPrefix == null) {
            memoryPage = snapMemoryRepository.findAll(pageRequest);
        } else {
            memoryPage = snapMemoryRepository.findByCapturedAtStartingWith(
                    capturedAtPrefix,
                    pageRequest
            );
        }

        List<MemoryResponse> content = memoryPage.getContent()
                .stream()
                .map(this::toResponse)
                .toList();

        return new MemoryPageResponse(
                content,
                memoryPage.getNumber(),
                memoryPage.getSize(),
                memoryPage.getTotalElements(),
                memoryPage.getTotalPages()
        );
    }

    public List<TimelineYearResponse> findTimelineYears() {
        return snapMemoryRepository.countMemoriesByYear()
                .stream()
                .map(item -> new TimelineYearResponse(
                        item.getYear(),
                        item.getMemoryCount()
                ))
                .toList();
    }

    public List<TimelineMonthResponse> findTimelineMonths(int year) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100.");
        }

        return snapMemoryRepository.countMemoriesByMonth(year)
                .stream()
                .map(item -> new TimelineMonthResponse(
                        item.getMonth(),
                        item.getMemoryCount()
                ))
                .toList();
    }

    private void validateDateFilter(Integer year, Integer month) {
        if (month != null && year == null) {
            throw new IllegalArgumentException(
                    "A month filter requires a year filter."
            );
        }

        if (year != null && (year < 2000 || year > 2100)) {
            throw new IllegalArgumentException("Year must be between 2000 and 2100.");
        }

        if (month != null && (month < 1 || month > 12)) {
            throw new IllegalArgumentException("Month must be between 1 and 12.");
        }
    }

    private String buildCapturedAtPrefix(Integer year, Integer month) {
        if (year == null) {
            return null;
        }

        if (month == null) {
            return String.valueOf(year);
        }

        return "%d-%02d".formatted(year, month);
    }

    private MemoryResponse toResponse(SnapMemory memory) {
        return new MemoryResponse(
                memory.getId(),
                memory.getCapturedAt(),
                memory.getMediaType().name(),
                memory.getOverlayPath() != null,
                memory.getFileSizeBytes(),
                memory.getLastModifiedAt()
        );
    }
}