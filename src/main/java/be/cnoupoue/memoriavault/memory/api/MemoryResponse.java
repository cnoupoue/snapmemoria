package be.cnoupoue.memoriavault.memory.api;

public record MemoryResponse(
    String id,
    String capturedAt,
    String mediaType,
    boolean hasOverlay,
    long fileSizeBytes,
    String lastModifiedAt,
    String thumbnailUrl,
    boolean isFavorite,
    String favoritedAt) {}
