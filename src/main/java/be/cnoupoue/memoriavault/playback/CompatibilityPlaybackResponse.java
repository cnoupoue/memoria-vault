package be.cnoupoue.memoriavault.playback;

public record CompatibilityPlaybackResponse(
    CompatibilityPlaybackStatus status, String mediaUrl, String message) {}
