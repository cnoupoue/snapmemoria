package be.cnoupoue.memoriavault.memory.api;

import be.cnoupoue.memoriavault.memory.SnapMemoryService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
public class SnapMemoryController {

  private final SnapMemoryService snapMemoryService;

  public SnapMemoryController(SnapMemoryService snapMemoryService) {
    this.snapMemoryService = snapMemoryService;
  }

  @GetMapping
  public MemoryPageResponse findAll(
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "60") int size) {
    return snapMemoryService.findAll(year, month, page, size);
  }

  @GetMapping("/favorites")
  public MemoryPageResponse findFavorites(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "60") int size) {
    return snapMemoryService.findFavorites(page, size);
  }

  @GetMapping("/{id}")
  public MemoryDetailResponse findById(@PathVariable String id) {
    return snapMemoryService.findById(id);
  }

  @PutMapping("/{id}/favorite")
  public MemoryResponse addFavorite(@PathVariable String id) {
    return snapMemoryService.addFavorite(id);
  }

  @DeleteMapping("/{id}/favorite")
  @ResponseStatus(HttpStatus.OK)
  public MemoryResponse removeFavorite(@PathVariable String id) {
    return snapMemoryService.removeFavorite(id);
  }
}
