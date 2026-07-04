package be.cnoupoue.memoriavault.memory.api;

import java.util.List;

public record FlashbackResponse(String date, List<FlashbackMemoryResponse> memories) {}
