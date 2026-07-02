package be.cnoupoue.snapmemoria.diagnostics;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/diagnostics")
public class DiagnosticsController {

  private final DiagnosticsService diagnosticsService;

  public DiagnosticsController(DiagnosticsService diagnosticsService) {
    this.diagnosticsService = diagnosticsService;
  }

  @GetMapping
  public DiagnosticsResponse getDiagnostics() {
    return diagnosticsService.getDiagnostics();
  }
}
