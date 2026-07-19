package be.cnoupoue.memoriavault.source.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ImportFavoritesBackupRequest(
    int version,
    String exportedAt,
    FavoritesBackupSourceResponse source,
    String sourceId,
    @NotNull List<@Valid ImportFavoriteBackupMemoryRequest> favorites) {}
