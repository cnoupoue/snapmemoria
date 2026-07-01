package be.cnoupoue.snapmemoria.memory;

import be.cnoupoue.snapmemoria.memory.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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

    public FlashbackResponse findFlashbacks(LocalDate date) {
        String monthDay = "%02d-%02d".formatted(
                date.getMonthValue(),
                date.getDayOfMonth()
        );

        List<FlashbackMemoryResponse> memories = snapMemoryRepository
                .findFlashbacks(monthDay, date.getYear())
                .stream()
                .map(memory -> toFlashbackResponse(memory, date.getYear()))
                .toList();

        return new FlashbackResponse(
                date.toString(),
                memories
        );
    }

    public MemoryDetailResponse findById(String memoryId) {
        SnapMemory memory = snapMemoryRepository.findById(memoryId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Memory not found."
                ));

        return new MemoryDetailResponse(
                memory.getId(),
                memory.getCapturedAt(),
                memory.getMediaType().name(),
                memory.getOverlayPath() != null,
                memory.getFileSizeBytes(),
                memory.getLastModifiedAt(),
                "/api/memories/%s/media".formatted(memory.getId()),
                memory.getOverlayPath() == null
                        ? null
                        : "/api/memories/%s/overlay".formatted(memory.getId())
        );
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
        String thumbnailUrl = "/api/memories/%s/thumbnail"
                .formatted(memory.getId());

        return new MemoryResponse(
                memory.getId(),
                memory.getCapturedAt(),
                memory.getMediaType().name(),
                memory.getOverlayPath() != null,
                memory.getFileSizeBytes(),
                memory.getLastModifiedAt(),
                thumbnailUrl
        );
    }

    private FlashbackMemoryResponse toFlashbackResponse(
            SnapMemory memory,
            int currentYear
    ) {
        int memoryYear = Integer.parseInt(memory.getCapturedAt().substring(0, 4));

        return new FlashbackMemoryResponse(
                memory.getId(),
                memory.getCapturedAt(),
                memoryYear,
                currentYear - memoryYear,
                memory.getMediaType().name(),
                memory.getOverlayPath() != null,
                memory.getFileSizeBytes()
        );
    }
}