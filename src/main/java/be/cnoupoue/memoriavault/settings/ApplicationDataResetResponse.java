package be.cnoupoue.memoriavault.settings;

import java.util.List;

public record ApplicationDataResetResponse(
    boolean reset, boolean restartRequired, List<String> removedLocations, String message) {}
