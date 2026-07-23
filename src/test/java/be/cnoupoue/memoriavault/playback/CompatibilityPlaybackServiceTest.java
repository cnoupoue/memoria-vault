package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import be.cnoupoue.memoriavault.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegResolution;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegSource;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.memory.SnapMemoryType;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompatibilityPlaybackServiceTest {

  @Mock private SnapMemoryRepository snapMemoryRepository;

  @Mock private SecureMemoryPathResolver secureMemoryPathResolver;

  @TempDir private Path temporaryDirectory;

  @Test
  void directPlaybackDecisionAvoidsUnnecessaryTranscoding() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mp4"), "video");
    CompatibilityPlaybackService service =
        service(
            original,
            directPlayableInspection(),
            unavailableCapabilities(),
            unavailableFfmpegResolver());

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.DIRECT);
    assertThat(response.mediaUrl()).isEqualTo("/api/memories/memory-1/media");
  }

  @Test
  void forcedNormalizationRetriesEvenWhenInspectionAllowsDirectPlayback() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mp4"), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(
            original, directPlayableInspection(), availableCapabilities(), resolver(fakeFfmpeg));

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1", true);

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.GENERATED);
    assertThat(response.mediaUrl()).isEqualTo("/api/memories/memory-1/playback/compatible/media");
  }

  @Test
  void ffmpegUnavailabilityProducesSafeFallbackWhenNormalizationIsRequired() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    CompatibilityPlaybackService service =
        service(
            original,
            normalizationInspection(null),
            unavailableCapabilities(),
            unavailableFfmpegResolver());

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.FAILED);
    assertThat(response.mediaUrl()).isNull();
  }

  @Test
  void generatedFallbackFilesAreStoredOnlyInLocalCacheAndOriginalIsUnchanged() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original ü video.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(
            original, normalizationInspection(null), availableCapabilities(), resolver(fakeFfmpeg));

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.GENERATED);
    assertThat(response.mediaUrl()).isEqualTo("/api/memories/memory-1/playback/compatible/media");
    assertThat(Files.readString(original)).isEqualTo("video");

    Path playbackDirectory = temporaryDirectory.resolve("playback");
    assertThat(Files.list(playbackDirectory).toList())
        .singleElement()
        .satisfies(
            path -> {
              assertThat(path.getParent()).isEqualTo(playbackDirectory);
              assertThat(path.getFileName().toString()).endsWith(".mp4");
              assertThat(path.getFileName().toString()).doesNotContain("original");
            });
  }

  @Test
  void existingValidCompatibilityCacheEntryIsReused() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    TestCompatibilityPlaybackService service =
        service(
            original,
            normalizationInspection(null),
            availableCapabilities(),
            unavailableFfmpegResolver());
    Path cachedPath = service.getCompatiblePlaybackMediaPathForTest("memory-1");
    Files.createDirectories(cachedPath.getParent());
    Files.writeString(cachedPath, "cached");

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.AVAILABLE);
    assertThat(Files.readString(cachedPath)).isEqualTo("cached");
  }

  @Test
  void cacheIsInvalidatedWhenSourceFileChanges() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    TestCompatibilityPlaybackService service =
        service(
            original,
            normalizationInspection(null),
            availableCapabilities(),
            unavailableFfmpegResolver());
    Path firstCachePath = service.getCompatiblePlaybackMediaPathForTest("memory-1");

    Files.writeString(original, "changed video");
    Files.setLastModifiedTime(original, FileTime.from(Instant.now().plusSeconds(5)));

    Path secondCachePath = service.getCompatiblePlaybackMediaPathForTest("memory-1");

    assertThat(secondCachePath).isNotEqualTo(firstCachePath);
  }

  @Test
  void failedConversionsDoNotLeavePartialPlayableCacheFiles() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(1);
    CompatibilityPlaybackService service =
        service(
            original, normalizationInspection(null), availableCapabilities(), resolver(fakeFfmpeg));

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.FAILED);
    assertThat(Files.list(temporaryDirectory.resolve("playback")).toList()).isEmpty();
  }

  @Test
  void compatibilityOutputUsesBrowserCompatibleParametersWhenEncoderIsAvailable() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(
            original, normalizationInspection(90), availableCapabilities(), resolver(fakeFfmpeg));

    service.prepareCompatibilityPlayback("memory-1");

    String commandLog = Files.readString(temporaryDirectory.resolve("ffmpeg-command.txt"));

    assertThat(commandLog).contains("-c:v libx264");
    assertThat(commandLog).contains("-pix_fmt yuv420p");
    assertThat(commandLog).contains("-c:a aac");
    assertThat(commandLog).contains("-movflags +faststart");
    assertThat(commandLog).contains("-vf transpose=clock");
    assertThat(commandLog).contains("-metadata:s:v:0 rotate=0");
  }

  @Test
  void rotationNormalizationFiltersHandleRightAngles() throws Exception {
    assertCommandForRotation(90, "-vf transpose=clock");
    assertCommandForRotation(180, "-vf hflip,vflip");
    assertCommandForRotation(270, "-vf transpose=cclock");
  }

  private TestCompatibilityPlaybackService service(
      Path originalPath,
      MediaInspectionResult inspection,
      FfmpegEncoderCapabilities capabilities,
      FfmpegPathResolver ffmpegPathResolver) {
    SnapMemory memory = memory("memory-1", originalPath);

    when(snapMemoryRepository.findById("memory-1")).thenReturn(Optional.of(memory));
    when(secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath()))
        .thenReturn(originalPath);

    return new TestCompatibilityPlaybackService(
        snapMemoryRepository,
        secureMemoryPathResolver,
        ffmpegPathResolver,
        capabilities,
        inspection,
        temporaryDirectory.resolve("playback").toString());
  }

  private void assertCommandForRotation(Integer rotation, String expectedFilter) throws Exception {
    Path original =
        Files.writeString(
            temporaryDirectory.resolve("rotation-%d.mov".formatted(rotation)), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(
            original,
            normalizationInspection(rotation),
            availableCapabilities(),
            resolver(fakeFfmpeg));

    service.prepareCompatibilityPlayback("memory-1");

    assertThat(Files.readString(temporaryDirectory.resolve("ffmpeg-command.txt")))
        .contains(expectedFilter);
  }

  private Path fakeFfmpeg(int exitCode) throws Exception {
    Path fakeFfmpeg = temporaryDirectory.resolve(isWindows() ? "ffmpeg.cmd" : "ffmpeg");
    String commandLog = temporaryDirectory.resolve("ffmpeg-command.txt").toString();
    String script =
        isWindows()
            ? windowsFakeFfmpeg(commandLog, exitCode)
            : unixFakeFfmpeg(commandLog, exitCode);
    Files.writeString(fakeFfmpeg, script);
    fakeFfmpeg.toFile().setExecutable(true);

    return fakeFfmpeg;
  }

  private String unixFakeFfmpeg(String commandLog, int exitCode) {
    return """
        #!/bin/sh
        printf '%%s ' "$@" > "%s"
        if [ "%d" -eq 0 ]; then
          for arg in "$@"; do out="$arg"; done
          printf converted > "$out"
        fi
        exit %d
        """
        .formatted(commandLog, exitCode, exitCode);
  }

  private String windowsFakeFfmpeg(String commandLog, int exitCode) {
    return """
        @echo off
        setlocal enabledelayedexpansion
        break > "%s"
        >> "%s" echo(%%*
        set "out="
        :args
        if "%%~1"=="" goto done
        set "out=%%~1"
        shift
        goto args
        :done
        if "%d"=="0" (
          > "!out!" echo converted
        )
        exit /b %d
        """
        .formatted(commandLog, commandLog, exitCode, exitCode);
  }

  private FfmpegEncoderCapabilities availableCapabilities() {
    return new FfmpegEncoderCapabilities(true, true, true, "libx264");
  }

  private FfmpegEncoderCapabilities unavailableCapabilities() {
    return new FfmpegEncoderCapabilities(true, false, true, null);
  }

  private FfmpegPathResolver resolver(Path ffmpeg) {
    return new FfmpegPathResolver("", Optional::empty, "") {
      @Override
      public FfmpegResolution resolve() {
        return FfmpegResolution.available(ffmpeg, FfmpegSource.CONFIGURED, "Using test FFmpeg.");
      }
    };
  }

  private FfmpegPathResolver unavailableFfmpegResolver() {
    return new FfmpegPathResolver("", Optional::empty, "") {
      @Override
      public FfmpegResolution resolve() {
        return FfmpegResolution.unavailable("FFmpeg unavailable.");
      }
    };
  }

  private SnapMemory memory(String id, Path originalPath) {
    String now = Instant.now().toString();

    return new SnapMemory(
        id,
        "source-1",
        id + "-external",
        "2020-06-10",
        SnapMemoryType.VIDEO,
        originalPath.toString(),
        null,
        5,
        now,
        now,
        now);
  }

  private static class TestCompatibilityPlaybackService extends CompatibilityPlaybackService {

    private TestCompatibilityPlaybackService(
        SnapMemoryRepository snapMemoryRepository,
        SecureMemoryPathResolver secureMemoryPathResolver,
        FfmpegPathResolver ffmpegPathResolver,
        FfmpegEncoderCapabilities capabilities,
        MediaInspectionResult inspection,
        String playbackDirectory) {
      super(
          snapMemoryRepository,
          secureMemoryPathResolver,
          ffmpegPathResolver,
          new TestFfmpegEncoderCapabilityService(capabilities),
          new TestMediaInspectionService(inspection),
          playbackDirectory);
    }

    private Path getCompatiblePlaybackMediaPathForTest(String memoryId) {
      return super.compatibilityPathForTest(memoryId);
    }
  }

  private static class TestMediaInspectionService extends MediaInspectionService {

    private final MediaInspectionResult inspection;

    private TestMediaInspectionService(MediaInspectionResult inspection) {
      super(null, new ObjectMapper(), ignored -> true);
      this.inspection = inspection;
    }

    @Override
    public MediaInspectionResult inspect(Path mediaPath) {
      return inspection;
    }
  }

  private static class TestFfmpegEncoderCapabilityService extends FfmpegEncoderCapabilityService {

    private final FfmpegEncoderCapabilities capabilities;

    private TestFfmpegEncoderCapabilityService(FfmpegEncoderCapabilities capabilities) {
      super(null);
      this.capabilities = capabilities;
    }

    @Override
    public FfmpegEncoderCapabilities getCapabilities() {
      return capabilities;
    }
  }

  private boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase().contains("win");
  }

  private MediaInspectionResult directPlayableInspection() {
    return new MediaInspectionResult(
        "mov,mp4,m4a,3gp,3g2,mj2",
        "h264",
        "aac",
        "yuv420p",
        1920,
        1080,
        null,
        Map.of("default", 1),
        5.0,
        true,
        "direct");
  }

  private MediaInspectionResult normalizationInspection(Integer rotationDegrees) {
    return new MediaInspectionResult(
        "mov,mp4,m4a,3gp,3g2,mj2",
        "hevc",
        "aac",
        "yuv420p",
        1920,
        1080,
        rotationDegrees,
        Map.of("default", 1),
        5.0,
        false,
        rotationDegrees == null
            ? "video_codec_requires_normalization"
            : "rotation_requires_normalization");
  }
}
