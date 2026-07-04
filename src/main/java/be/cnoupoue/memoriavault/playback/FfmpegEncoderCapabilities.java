package be.cnoupoue.memoriavault.playback;

public record FfmpegEncoderCapabilities(
    boolean ffmpegAvailable,
    boolean h264EncodingAvailable,
    boolean aacEncodingAvailable,
    String h264EncoderName) {

  public boolean browserCompatibilityEncodingAvailable() {
    return ffmpegAvailable && h264EncodingAvailable && aacEncodingAvailable;
  }
}
