package be.cnoupoue.snapmemoria.indexing;

public record ScanProgress(
        long totalFiles,
        long filesProcessed,

        long mainImages,
        long mainVideos,
        long overlays,

        long indexedMemories,
        long attachedOverlays,
        long unmatchedOverlays,

        long unsupportedFiles,
        long unreadableFiles
) {
}