package be.cnoupoue.snapmemoria.memory.api;

public record FlashbackMemoryResponse(
        String id,
        String capturedAt,
        int year,
        int yearsAgo,
        String mediaType,
        boolean hasOverlay,
        long fileSizeBytes
) {
}