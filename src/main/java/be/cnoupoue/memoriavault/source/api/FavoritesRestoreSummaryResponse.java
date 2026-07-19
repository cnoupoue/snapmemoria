package be.cnoupoue.memoriavault.source.api;

public record FavoritesRestoreSummaryResponse(
    int totalFavorites, int restorable, int restored, int alreadyFavorite, int notFound) {}
