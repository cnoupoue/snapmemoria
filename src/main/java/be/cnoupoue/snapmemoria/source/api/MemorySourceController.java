package be.cnoupoue.snapmemoria.source.api;

import be.cnoupoue.snapmemoria.source.MemorySourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sources")
public class MemorySourceController {

    private final MemorySourceService memorySourceService;

    public MemorySourceController(MemorySourceService memorySourceService) {
        this.memorySourceService = memorySourceService;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public MemorySourceResponse create(
            @Valid @RequestBody CreateMemorySourceRequest request
    ) {
        return memorySourceService.create(request);
    }

    @GetMapping
    public List<MemorySourceResponse> findAll() {
        return memorySourceService.findAll();
    }
}