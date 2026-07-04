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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
  void ffmpegUnavailabilityProducesSafeFallback() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    CompatibilityPlaybackService service =
        service(original, unavailableCapabilities(), unavailableFfmpegResolver());

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.UNAVAILABLE);
    assertThat(response.mediaUrl()).isNull();
  }

  @Test
  void generatedFallbackFilesAreStoredOnlyInLocalCacheAndOriginalIsUnchanged() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(original, availableCapabilities(), resolver(fakeFfmpeg));

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
        service(original, availableCapabilities(), unavailableFfmpegResolver());
    Path cachedPath = service.getCompatiblePlaybackMediaPathForTest("memory-1");
    Files.createDirectories(cachedPath.getParent());
    Files.writeString(cachedPath, "cached");

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.AVAILABLE);
    assertThat(Files.readString(cachedPath)).isEqualTo("cached");
  }

  @Test
  void failedConversionsDoNotLeavePartialPlayableCacheFiles() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(1);
    CompatibilityPlaybackService service =
        service(original, availableCapabilities(), resolver(fakeFfmpeg));

    CompatibilityPlaybackResponse response = service.prepareCompatibilityPlayback("memory-1");

    assertThat(response.status()).isEqualTo(CompatibilityPlaybackStatus.FAILED);
    assertThat(Files.list(temporaryDirectory.resolve("playback")).toList()).isEmpty();
  }

  @Test
  void compatibilityOutputUsesBrowserCompatibleParametersWhenEncoderIsAvailable() throws Exception {
    Path original = Files.writeString(temporaryDirectory.resolve("original.mov"), "video");
    Path fakeFfmpeg = fakeFfmpeg(0);
    CompatibilityPlaybackService service =
        service(original, availableCapabilities(), resolver(fakeFfmpeg));

    service.prepareCompatibilityPlayback("memory-1");

    String commandLog = Files.readString(temporaryDirectory.resolve("ffmpeg-command.txt"));

    assertThat(commandLog).contains("-c:v libx264");
    assertThat(commandLog).contains("-pix_fmt yuv420p");
    assertThat(commandLog).contains("-c:a aac");
    assertThat(commandLog).contains("-movflags +faststart");
  }

  private TestCompatibilityPlaybackService service(
      Path originalPath,
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
        temporaryDirectory.resolve("playback").toString());
  }

  private Path fakeFfmpeg(int exitCode) throws Exception {
    Path fakeFfmpeg = temporaryDirectory.resolve("ffmpeg");
    Files.writeString(
        fakeFfmpeg,
        """
        #!/bin/sh
        printf '%%s ' "$@" > "%s"
        if [ "%d" -eq 0 ]; then
          for arg in "$@"; do out="$arg"; done
          printf converted > "$out"
        fi
        exit %d
        """
            .formatted(temporaryDirectory.resolve("ffmpeg-command.txt"), exitCode, exitCode));
    fakeFfmpeg.toFile().setExecutable(true);

    return fakeFfmpeg;
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
        String playbackDirectory) {
      super(
          snapMemoryRepository,
          secureMemoryPathResolver,
          ffmpegPathResolver,
          new TestFfmpegEncoderCapabilityService(capabilities),
          playbackDirectory);
    }

    private Path getCompatiblePlaybackMediaPathForTest(String memoryId) {
      return super.compatibilityPathForTest(memoryId);
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
}
