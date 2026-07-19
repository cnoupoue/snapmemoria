package be.cnoupoue.memoriavault.memory.api;

public record MemoryDetailResponse(
    String id,
    String capturedAt,
    String mediaType,
    boolean hasOverlay,
    long fileSizeBytes,
    String lastModifiedAt,
    String mediaUrl,
    String overlayUrl,
    boolean isFavorite,
    String favoritedAt) {}
