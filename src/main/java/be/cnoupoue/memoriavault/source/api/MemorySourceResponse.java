package be.cnoupoue.memoriavault.source.api;

public record MemorySourceResponse(
    String id,
    String name,
    String rootPath,
    String lastScanAt,
    String lastScanStatus,
    String availabilityStatus,
    String availabilityMessage,
    long favoriteCount,
    String createdAt,
    String updatedAt) {}
