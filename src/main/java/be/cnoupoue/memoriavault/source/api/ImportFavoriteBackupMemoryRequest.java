package be.cnoupoue.memoriavault.source.api;

import jakarta.validation.constraints.NotBlank;

public record ImportFavoriteBackupMemoryRequest(
    String memoryId,
    @NotBlank String externalMemoryId,
    String capturedAt,
    String mediaType,
    @NotBlank String mainPath,
    String favoritedAt) {}
