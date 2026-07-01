package be.cnoupoue.snapmemoria.memory.api;

import java.util.List;

public record FlashbackResponse(String date, List<FlashbackMemoryResponse> memories) {}
