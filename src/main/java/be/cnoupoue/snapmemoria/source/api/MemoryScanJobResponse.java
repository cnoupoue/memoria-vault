package be.cnoupoue.snapmemoria.source.api;

public record MemoryScanJobResponse(
    String id,
    String sourceId,
    String status,
    long totalFiles,
    long filesProcessed,
    long mainImages,
    long mainVideos,
    long overlays,
    long indexedMemories,
    long attachedOverlays,
    long unmatchedOverlays,
    long unsupportedFiles,
    long unreadableFiles,
    String errorMessage,
    String startedAt,
    String completedAt,
    String updatedAt) {}
