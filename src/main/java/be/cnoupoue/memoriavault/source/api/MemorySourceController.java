package be.cnoupoue.memoriavault.source.api;

import be.cnoupoue.memoriavault.indexing.MemoryScanJobService;
import be.cnoupoue.memoriavault.source.MemorySourceService;
import be.cnoupoue.memoriavault.source.NativeFolderPickerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/sources")
public class MemorySourceController {

  private final MemorySourceService memorySourceService;
  private final MemoryScanJobService memoryScanJobService;
  private final NativeFolderPickerService nativeFolderPickerService;

  public MemorySourceController(
      MemorySourceService memorySourceService,
      MemoryScanJobService memoryScanJobService,
      NativeFolderPickerService nativeFolderPickerService) {
    this.memorySourceService = memorySourceService;
    this.memoryScanJobService = memoryScanJobService;
    this.nativeFolderPickerService = nativeFolderPickerService;
  }

  @PostMapping
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
  public MemorySourceResponse create(@Valid @RequestBody CreateMemorySourceRequest request) {
    return memorySourceService.create(request);
  }

  @GetMapping
  public List<MemorySourceResponse> findAll() {
    return memorySourceService.findAll();
  }

  @PostMapping("/select-folder")
  public FolderSelectionResponse selectFolder() {
    return nativeFolderPickerService.selectFolder();
  }

  @GetMapping("/{id}/availability")
  public SourceAvailabilityResponse checkAvailability(@PathVariable String id) {
    return memorySourceService.checkAvailability(id);
  }

  @GetMapping("/{id}/favorites-backup")
  public FavoritesBackupResponse exportFavoritesBackup(@PathVariable String id) {
    return memorySourceService.exportFavoritesBackup(id);
  }

  @PostMapping("/{id}/favorites-backup/preview")
  public FavoritesRestoreSummaryResponse previewFavoritesRestore(
      @PathVariable String id, @Valid @RequestBody ImportFavoritesBackupRequest request) {
    return memorySourceService.previewFavoritesRestore(id, request);
  }

  @PostMapping("/{id}/favorites-backup/restore")
  public FavoritesRestoreSummaryResponse restoreFavoritesBackup(
      @PathVariable String id, @Valid @RequestBody ImportFavoritesBackupRequest request) {
    return memorySourceService.restoreFavoritesBackup(id, request);
  }

  @PostMapping("/{id}/scan")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public MemoryScanJobResponse scan(@PathVariable String id) {
    return MemoryScanJobController.toResponse(memoryScanJobService.startScan(id));
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    memorySourceService.delete(id);
  }
}
