package be.cnoupoue.memoriavault.source.api;

import java.util.List;

public record FavoritesBackupResponse(
    int version,
    String exportedAt,
    String sourceId,
    List<FavoriteBackupMemoryResponse> favorites) {}
