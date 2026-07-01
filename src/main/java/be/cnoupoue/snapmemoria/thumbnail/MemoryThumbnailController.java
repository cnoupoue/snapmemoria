package be.cnoupoue.snapmemoria.thumbnail;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/memories")
public class MemoryThumbnailController {

    private final MemoryThumbnailService memoryThumbnailService;

    public MemoryThumbnailController(
            MemoryThumbnailService memoryThumbnailService
    ) {
        this.memoryThumbnailService = memoryThumbnailService;
    }

    @GetMapping("/{id}/thumbnail")
    public ResponseEntity<FileSystemResource> getThumbnail(
            @PathVariable String id
    ) {
        FileSystemResource thumbnail =
                memoryThumbnailService.getThumbnail(id);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)))
                .body(thumbnail);
    }

    @ExceptionHandler(ThumbnailUnavailableException.class)
    public ResponseEntity<Void> handleUnavailableThumbnail(
            ThumbnailUnavailableException exception
    ) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .build();
    }
}