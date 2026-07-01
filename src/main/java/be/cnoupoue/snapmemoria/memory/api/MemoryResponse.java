package be.cnoupoue.snapmemoria.memory.api;

public record MemoryResponse(
        String id,
        String capturedAt,
        String mediaType,
        boolean hasOverlay,
        long fileSizeBytes,
        String lastModifiedAt,
        String thumbnailUrl
) {
}