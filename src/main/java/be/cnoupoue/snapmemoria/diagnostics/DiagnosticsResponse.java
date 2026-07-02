package be.cnoupoue.snapmemoria.diagnostics;

public record DiagnosticsResponse(
    String appVersion,
    String platform,
    VideoPreviewDiagnosticsResponse videoPreviews,
    SourceDiagnosticsResponse sources,
    DatabaseDiagnosticsResponse database) {}
