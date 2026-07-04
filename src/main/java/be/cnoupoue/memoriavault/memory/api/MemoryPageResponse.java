package be.cnoupoue.memoriavault.memory.api;

import java.util.List;

public record MemoryPageResponse(
    List<MemoryResponse> content, int page, int size, long totalElements, int totalPages) {}
