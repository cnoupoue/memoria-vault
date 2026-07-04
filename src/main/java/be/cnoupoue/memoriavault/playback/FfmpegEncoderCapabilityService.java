package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegResolution;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class FfmpegEncoderCapabilityService {

  private static final Duration CAPABILITY_TIMEOUT = Duration.ofSeconds(5);

  private final FfmpegPathResolver ffmpegPathResolver;
  private volatile FfmpegEncoderCapabilities cachedCapabilities;

  public FfmpegEncoderCapabilityService(FfmpegPathResolver ffmpegPathResolver) {
    this.ffmpegPathResolver = ffmpegPathResolver;
  }

  public FfmpegEncoderCapabilities getCapabilities() {
    FfmpegEncoderCapabilities capabilities = cachedCapabilities;

    if (capabilities != null) {
      return capabilities;
    }

    capabilities = inspectCapabilities();
    cachedCapabilities = capabilities;

    return capabilities;
  }

  private FfmpegEncoderCapabilities inspectCapabilities() {
    FfmpegResolution resolution = ffmpegPathResolver.resolve();

    if (!resolution.available()) {
      return new FfmpegEncoderCapabilities(false, false, false, null);
    }

    Process process = null;
    try {
      process =
          new ProcessBuilder(resolution.executablePath().toString(), "-hide_banner", "-encoders")
              .redirectErrorStream(true)
              .start();

      boolean completed = process.waitFor(CAPABILITY_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      if (!completed) {
        process.destroyForcibly();
        return new FfmpegEncoderCapabilities(true, false, false, null);
      }

      String encoders = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      String h264EncoderName = h264EncoderName(encoders);

      return new FfmpegEncoderCapabilities(
          true, h264EncoderName != null, encoders.contains(" A....D aac "), h264EncoderName);
    } catch (IOException exception) {
      return new FfmpegEncoderCapabilities(true, false, false, null);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return new FfmpegEncoderCapabilities(true, false, false, null);
    }
  }

  private String h264EncoderName(String encoders) {
    if (encoders.contains(" libx264")) {
      return "libx264";
    }

    if (encoders.contains(" h264_videotoolbox")) {
      return "h264_videotoolbox";
    }

    if (encoders.contains(" h264_nvenc")) {
      return "h264_nvenc";
    }

    if (encoders.contains(" h264_qsv")) {
      return "h264_qsv";
    }

    if (encoders.contains(" h264_amf")) {
      return "h264_amf";
    }

    return null;
  }
}
