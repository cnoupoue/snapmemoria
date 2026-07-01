package be.cnoupoue.snapmemoria.source.api;

public record MemorySourceResponse(
        String id,
        String name,
        String rootPath,
        String lastScanAt,
        String lastScanStatus,
        String createdAt,
        String updatedAt
) {
}