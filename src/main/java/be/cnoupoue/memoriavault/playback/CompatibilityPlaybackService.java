package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegResolution;
import be.cnoupoue.memoriavault.memory.SnapMemory;
import be.cnoupoue.memoriavault.memory.SnapMemoryRepository;
import be.cnoupoue.memoriavault.streaming.MediaStreamingException;
import be.cnoupoue.memoriavault.streaming.MediaStreamingFailureCategory;
import be.cnoupoue.memoriavault.streaming.SecureMemoryPathResolver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompatibilityPlaybackService {

  private static final Duration TRANSCODE_TIMEOUT = Duration.ofMinutes(20);

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;
  private final FfmpegPathResolver ffmpegPathResolver;
  private final FfmpegEncoderCapabilityService capabilityService;
  private final Path playbackDirectory;
  private final Map<String, Object> playbackLocks = new ConcurrentHashMap<>();

  public CompatibilityPlaybackService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver,
      FfmpegPathResolver ffmpegPathResolver,
      FfmpegEncoderCapabilityService capabilityService,
      @Value("${memoriavault.playback.directory}") String playbackDirectory) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
    this.ffmpegPathResolver = ffmpegPathResolver;
    this.capabilityService = capabilityService;
    this.playbackDirectory = Path.of(playbackDirectory).toAbsolutePath().normalize();
  }

  public CompatibilityPlaybackResponse prepareCompatibilityPlayback(String memoryId) {
    SnapMemory memory = findMemory(memoryId);
    Path originalPath = resolveOriginalPath(memory);
    Path compatibilityPath = compatibilityPath(memory, originalPath);

    try {
      Files.createDirectories(playbackDirectory);

      if (Files.isRegularFile(compatibilityPath) && Files.size(compatibilityPath) > 0) {
        return new CompatibilityPlaybackResponse(
            CompatibilityPlaybackStatus.AVAILABLE,
            "/api/memories/%s/playback/compatible/media".formatted(memoryId),
            "Compatibility playback is ready.");
      }
    } catch (IOException exception) {
      return unavailable("The local playback cache could not be prepared.");
    }

    FfmpegEncoderCapabilities capabilities = capabilityService.getCapabilities();

    if (!capabilities.browserCompatibilityEncodingAvailable()) {
      return unavailable("Compatibility playback is unavailable with the bundled FFmpeg build.");
    }

    Object lock = playbackLocks.computeIfAbsent(memoryId, ignored -> new Object());

    synchronized (lock) {
      try {
        if (Files.isRegularFile(compatibilityPath) && Files.size(compatibilityPath) > 0) {
          return new CompatibilityPlaybackResponse(
              CompatibilityPlaybackStatus.AVAILABLE,
              "/api/memories/%s/playback/compatible/media".formatted(memoryId),
              "Compatibility playback is ready.");
        }

        generateCompatibilityPlayback(originalPath, compatibilityPath, capabilities);

        return new CompatibilityPlaybackResponse(
            CompatibilityPlaybackStatus.GENERATED,
            "/api/memories/%s/playback/compatible/media".formatted(memoryId),
            "Compatibility playback is ready.");
      } catch (IOException exception) {
        return failed();
      } finally {
        playbackLocks.remove(memoryId);
      }
    }
  }

  public FileSystemResource getCompatiblePlaybackMedia(String memoryId) {
    SnapMemory memory = findMemory(memoryId);
    Path originalPath = resolveOriginalPath(memory);
    Path compatibilityPath = compatibilityPath(memory, originalPath);

    if (!Files.isRegularFile(compatibilityPath)) {
      throw new MediaStreamingException(MediaStreamingFailureCategory.MEDIA_FILE_MISSING);
    }

    return new FileSystemResource(compatibilityPath);
  }

  private void generateCompatibilityPlayback(
      Path originalPath, Path compatibilityPath, FfmpegEncoderCapabilities capabilities)
      throws IOException {
    FfmpegResolution resolution = ffmpegPathResolver.resolve();

    if (!resolution.available()) {
      throw new IOException("FFmpeg unavailable.");
    }

    Path temporaryPath =
        playbackDirectory.resolve(compatibilityPath.getFileName().toString() + ".tmp");

    List<String> command =
        List.of(
            resolution.executablePath().toString(),
            "-hide_banner",
            "-loglevel",
            "error",
            "-i",
            originalPath.toString(),
            "-map",
            "0:v:0",
            "-map",
            "0:a:0?",
            "-c:v",
            capabilities.h264EncoderName(),
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-movflags",
            "+faststart",
            "-y",
            temporaryPath.toString());

    Process process = null;

    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      boolean completed = process.waitFor(TRANSCODE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      if (!completed) {
        process.destroyForcibly();
        throw new IOException("Compatibility playback generation timed out.");
      }

      if (process.exitValue() != 0
          || !Files.isRegularFile(temporaryPath)
          || Files.size(temporaryPath) == 0) {
        throw new IOException("Compatibility playback generation failed.");
      }

      Files.move(
          temporaryPath,
          compatibilityPath,
          StandardCopyOption.REPLACE_EXISTING,
          StandardCopyOption.ATOMIC_MOVE);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("Compatibility playback generation was interrupted.", exception);
    } finally {
      try {
        Files.deleteIfExists(temporaryPath);
      } catch (IOException ignored) {
        // A temporary cache file can safely be cleaned up later.
      }
    }
  }

  private CompatibilityPlaybackResponse unavailable(String message) {
    return new CompatibilityPlaybackResponse(
        CompatibilityPlaybackStatus.UNAVAILABLE, null, message);
  }

  private CompatibilityPlaybackResponse failed() {
    return new CompatibilityPlaybackResponse(
        CompatibilityPlaybackStatus.FAILED, null, "Compatibility playback could not be prepared.");
  }

  private SnapMemory findMemory(String memoryId) {
    return snapMemoryRepository
        .findById(memoryId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Memory not found."));
  }

  private Path resolveOriginalPath(SnapMemory memory) {
    return secureMemoryPathResolver.resolve(memory.getSourceId(), memory.getMainPath());
  }

  private Path compatibilityPath(SnapMemory memory, Path originalPath) {
    return playbackDirectory.resolve(cacheKey(memory, originalPath) + ".mp4");
  }

  protected Path compatibilityPathForTest(String memoryId) {
    SnapMemory memory = findMemory(memoryId);
    Path originalPath = resolveOriginalPath(memory);

    return compatibilityPath(memory, originalPath);
  }

  private String cacheKey(SnapMemory memory, Path originalPath) {
    try {
      BasicFileAttributes attributes =
          Files.readAttributes(originalPath, BasicFileAttributes.class);
      String keyMaterial =
          String.join(
              "|",
              memory.getSourceId(),
              String.valueOf(attributes.size()),
              String.valueOf(attributes.lastModifiedTime().toMillis()),
              String.valueOf(attributes.fileKey()));

      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(keyMaterial.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(hash);
    } catch (IOException exception) {
      throw new MediaStreamingException(
          MediaStreamingFailureCategory.MEDIA_FILE_MISSING, exception);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }
}
