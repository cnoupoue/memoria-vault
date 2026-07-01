package be.cnoupoue.snapmemoria.source.api;

public record ScanMemorySourceResponse(
        String sourceId,
        String sourcePath,
        String status,
        long filesVisited,
        long mainImages,
        long mainVideos,
        long overlays,
        long indexedMemories,
        long attachedOverlays,
        long unmatchedOverlays,
        long unsupportedFiles,
        long unreadableFiles,
        String startedAt,
        String completedAt
) {
}