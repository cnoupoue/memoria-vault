package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegResolution;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MediaInspectionServiceTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void h264AacYuv420pMp4CanPlayDirectly() throws Exception {
    MediaInspectionResult result = inspect(mp4Fixture("h264", "aac", "yuv420p", null, null));

    assertThat(result.directPlayable()).isTrue();
    assertThat(result.requiresNormalization()).isFalse();
    assertThat(result.containerFormat()).isEqualTo("mov,mp4,m4a,3gp,3g2,mj2");
    assertThat(result.videoCodec()).isEqualTo("h264");
    assertThat(result.audioCodec()).isEqualTo("aac");
    assertThat(result.pixelFormat()).isEqualTo("yuv420p");
    assertThat(result.width()).isEqualTo(1920);
    assertThat(result.height()).isEqualTo(1080);
    assertThat(result.durationSeconds()).isEqualTo(12.5);
    assertThat(result.videoDisposition()).containsEntry("default", 1);
  }

  @Test
  void hevcVideoRequiresNormalizationEvenWhenAudioIsPlayable() throws Exception {
    MediaInspectionResult result = inspect(mp4Fixture("hevc", "aac", "yuv420p", null, null));

    assertThat(result.directPlayable()).isFalse();
    assertThat(result.decisionReason()).isEqualTo("video_codec_requires_normalization");
  }

  @Test
  void unsupportedPixelFormatRequiresNormalization() throws Exception {
    MediaInspectionResult result = inspect(mp4Fixture("h264", "aac", "yuv420p10le", null, null));

    assertThat(result.directPlayable()).isFalse();
    assertThat(result.decisionReason()).isEqualTo("pixel_format_requires_normalization");
  }

  @Test
  void detectsRotationFromRotateTags() throws Exception {
    assertThat(inspect(mp4Fixture("h264", "aac", "yuv420p", 90, null)).rotationDegrees())
        .isEqualTo(90);
    assertThat(inspect(mp4Fixture("h264", "aac", "yuv420p", 180, null)).rotationDegrees())
        .isEqualTo(180);
    assertThat(inspect(mp4Fixture("h264", "aac", "yuv420p", 270, null)).rotationDegrees())
        .isEqualTo(270);
  }

  @Test
  void detectsRotationFromDisplayMatrixSideData() throws Exception {
    MediaInspectionResult result = inspect(mp4Fixture("h264", "aac", "yuv420p", null, -90));

    assertThat(result.rotationDegrees()).isEqualTo(270);
    assertThat(result.directPlayable()).isFalse();
    assertThat(result.decisionReason()).isEqualTo("rotation_requires_normalization");
  }

  private MediaInspectionResult inspect(String ffprobeJson) throws Exception {
    Path ffprobeOutput =
        Files.writeString(temporaryDirectory.resolve("probe-output.json"), ffprobeJson);
    Path ffmpeg = fakeExecutable(ffmpegBinaryName(), ffmpegScript());
    fakeExecutable(ffprobeBinaryName(), ffprobeScript(ffprobeOutput));
    Path media = Files.writeString(temporaryDirectory.resolve("video with ünicode.mp4"), "video");
    MediaInspectionService service =
        new MediaInspectionService(resolver(ffmpeg), new ObjectMapper(), ignored -> true);

    return service.inspect(media);
  }

  private String mp4Fixture(
      String videoCodec,
      String audioCodec,
      String pixelFormat,
      Integer tagRotation,
      Integer displayMatrixRotation) {
    String tags =
        tagRotation == null ? "" : ", \"tags\": { \"rotate\": \"%d\" }".formatted(tagRotation);
    String sideData =
        displayMatrixRotation == null
            ? ""
            : ", \"side_data_list\": [{ \"side_data_type\": \"Display Matrix\", \"rotation\": %d }]"
                .formatted(displayMatrixRotation);

    return """
        {
          "streams": [
            {
              "codec_type": "video",
              "codec_name": "%s",
              "pix_fmt": "%s",
              "width": 1920,
              "height": 1080,
              "duration": "12.5",
              "disposition": { "default": 1, "attached_pic": 0 }%s%s
            },
            {
              "codec_type": "audio",
              "codec_name": "%s",
              "disposition": { "default": 1 }
            }
          ],
          "format": {
            "format_name": "mov,mp4,m4a,3gp,3g2,mj2",
            "duration": "12.5"
          }
        }
        """
        .formatted(videoCodec, pixelFormat, tags, sideData, audioCodec);
  }

  private Path fakeExecutable(String name, String script) throws Exception {
    Path executable = temporaryDirectory.resolve(name);
    Files.writeString(executable, script);
    executable.toFile().setExecutable(true);

    return executable;
  }

  private String ffmpegBinaryName() {
    return isWindows() ? "ffmpeg.cmd" : "ffmpeg";
  }

  private String ffprobeBinaryName() {
    return isWindows() ? "ffprobe.cmd" : "ffprobe";
  }

  private String ffmpegScript() {
    return isWindows()
        ? """
            @echo off
            exit /b 0
            """
        : """
            #!/bin/sh
            exit 0
            """;
  }

  private String ffprobeScript(Path ffprobeOutput) {
    return isWindows()
        ? """
            @echo off
            type "%s"
            exit /b 0
            """
            .formatted(ffprobeOutput)
        : """
            #!/bin/sh
            cat "%s"
            """
            .formatted(ffprobeOutput);
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private FfmpegPathResolver resolver(Path ffmpeg) {
    return new FfmpegPathResolver("", Optional::empty, "") {
      @Override
      public FfmpegResolution resolve() {
        return FfmpegResolution.available(ffmpeg, FfmpegSource.CONFIGURED, "Using test FFmpeg.");
      }
    };
  }
}
