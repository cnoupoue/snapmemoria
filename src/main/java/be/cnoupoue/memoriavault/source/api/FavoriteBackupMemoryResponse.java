package be.cnoupoue.memoriavault.source.api;

public record FavoriteBackupMemoryResponse(
    String memoryId,
    String externalMemoryId,
    String capturedAt,
    String mediaType,
    String mainPath,
    String favoritedAt) {}
