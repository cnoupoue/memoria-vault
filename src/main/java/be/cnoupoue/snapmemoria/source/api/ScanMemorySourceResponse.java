package be.cnoupoue.snapmemoria.source.api;

public record ScanMemorySourceResponse(
        String sourceId,
        String sourcePath,
        String status,
        long filesVisited,
        long mainImages,
        long mainVideos,
        long overlays,
        long unsupportedFiles,
        String startedAt,
        String completedAt
) {
}