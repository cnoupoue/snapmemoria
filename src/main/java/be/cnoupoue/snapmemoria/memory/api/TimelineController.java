package be.cnoupoue.snapmemoria.memory.api;

import be.cnoupoue.snapmemoria.memory.SnapMemoryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/timeline")
public class TimelineController {

    private final SnapMemoryService snapMemoryService;

    public TimelineController(SnapMemoryService snapMemoryService) {
        this.snapMemoryService = snapMemoryService;
    }

    @GetMapping("/years")
    public List<TimelineYearResponse> findYears() {
        return snapMemoryService.findTimelineYears();
    }

    @GetMapping("/years/{year}/months")
    public List<TimelineMonthResponse> findMonths(
            @PathVariable int year
    ) {
        return snapMemoryService.findTimelineMonths(year);
    }
}