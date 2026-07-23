package be.cnoupoue.memoriavault.playback;

import java.util.Map;

record MediaInspectionResult(
    String containerFormat,
    String videoCodec,
    String audioCodec,
    String pixelFormat,
    Integer width,
    Integer height,
    Integer rotationDegrees,
    Map<String, Integer> videoDisposition,
    Double durationSeconds,
    boolean directPlayable,
    String decisionReason) {

  boolean requiresNormalization() {
    return !directPlayable;
  }
}
