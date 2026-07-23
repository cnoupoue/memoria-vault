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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompatibilityPlaybackService {

  private static final Logger LOGGER = LoggerFactory.getLogger(CompatibilityPlaybackService.class);
  private static final Duration TRANSCODE_TIMEOUT = Duration.ofMinutes(20);
  private static final int NORMALIZATION_VERSION = 2;

  private final SnapMemoryRepository snapMemoryRepository;
  private final SecureMemoryPathResolver secureMemoryPathResolver;
  private final FfmpegPathResolver ffmpegPathResolver;
  private final FfmpegEncoderCapabilityService capabilityService;
  private final MediaInspectionService mediaInspectionService;
  private final Path playbackDirectory;
  private final Map<String, Object> playbackLocks = new ConcurrentHashMap<>();

  public CompatibilityPlaybackService(
      SnapMemoryRepository snapMemoryRepository,
      SecureMemoryPathResolver secureMemoryPathResolver,
      FfmpegPathResolver ffmpegPathResolver,
      FfmpegEncoderCapabilityService capabilityService,
      MediaInspectionService mediaInspectionService,
      @Value("${memoriavault.playback.directory}") String playbackDirectory) {
    this.snapMemoryRepository = snapMemoryRepository;
    this.secureMemoryPathResolver = secureMemoryPathResolver;
    this.ffmpegPathResolver = ffmpegPathResolver;
    this.capabilityService = capabilityService;
    this.mediaInspectionService = mediaInspectionService;
    this.playbackDirectory = Path.of(playbackDirectory).toAbsolutePath().normalize();
  }

  public CompatibilityPlaybackResponse prepareCompatibilityPlayback(String memoryId) {
    return prepareCompatibilityPlayback(memoryId, false);
  }

  public CompatibilityPlaybackResponse prepareCompatibilityPlayback(
      String memoryId, boolean forceNormalization) {
    SnapMemory memory = findMemory(memoryId);
    Path originalPath = resolveOriginalPath(memory);
    MediaInspectionResult inspection;

    try {
      inspection = mediaInspectionService.inspect(originalPath);
    } catch (IOException exception) {
      LOGGER.warn(
          "video_playback_inspection_failed mediaId={} reason={}",
          memoryId,
          exception.getMessage());
      return unavailable("Video compatibility could not be inspected.");
    }

    logInspection(memoryId, inspection);

    if (inspection.directPlayable() && !forceNormalization) {
      LOGGER.info(
          "video_playback_decision mediaId={} selected=direct reason={} rotation={}",
          memoryId,
          inspection.decisionReason(),
          inspection.rotationDegrees());
      return new CompatibilityPlaybackResponse(
          CompatibilityPlaybackStatus.DIRECT,
          "/api/memories/%s/media".formatted(memoryId),
          "Original playback is compatible.");
    }

    Path compatibilityPath = compatibilityPath(memory, originalPath);
    String compatibilityCacheName = compatibilityPath.getFileName().toString();

    try {
      Files.createDirectories(playbackDirectory);

      if (Files.isRegularFile(compatibilityPath) && Files.size(compatibilityPath) > 0) {
        LOGGER.info(
            "video_playback_decision mediaId={} selected=normalized-cache reason={} rotation={} cacheFile={}",
            memoryId,
            inspection.decisionReason(),
            inspection.rotationDegrees(),
            compatibilityCacheName);
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
      LOGGER.warn(
          "video_playback_normalization_unavailable mediaId={} reason={} h264Available={} aacAvailable={}",
          memoryId,
          inspection.decisionReason(),
          capabilities.h264EncodingAvailable(),
          capabilities.aacEncodingAvailable());
      return failed();
    }

    Object lock =
        playbackLocks.computeIfAbsent(
            compatibilityPath.getFileName().toString(), ignored -> new Object());

    synchronized (lock) {
      try {
        if (Files.isRegularFile(compatibilityPath) && Files.size(compatibilityPath) > 0) {
          LOGGER.info(
              "video_playback_decision mediaId={} selected=normalized-cache reason={} rotation={} cacheFile={}",
              memoryId,
              inspection.decisionReason(),
              inspection.rotationDegrees(),
              compatibilityCacheName);
          return new CompatibilityPlaybackResponse(
              CompatibilityPlaybackStatus.AVAILABLE,
              "/api/memories/%s/playback/compatible/media".formatted(memoryId),
              "Compatibility playback is ready.");
        }

        LOGGER.info(
            "video_playback_decision mediaId={} selected={} reason={} rotation={} cacheFile={}",
            memoryId,
            forceNormalization ? "normalize-forced" : "normalize",
            inspection.decisionReason(),
            inspection.rotationDegrees(),
            compatibilityCacheName);
        generateCompatibilityPlayback(originalPath, compatibilityPath, capabilities, inspection);

        return new CompatibilityPlaybackResponse(
            CompatibilityPlaybackStatus.GENERATED,
            "/api/memories/%s/playback/compatible/media".formatted(memoryId),
            "Compatibility playback is ready.");
      } catch (IOException exception) {
        LOGGER.warn(
            "video_playback_normalization_failed mediaId={} reason={} cacheFile={}",
            memoryId,
            exception.getMessage(),
            compatibilityCacheName);
        return failed();
      } finally {
        playbackLocks.remove(compatibilityPath.getFileName().toString());
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
      Path originalPath,
      Path compatibilityPath,
      FfmpegEncoderCapabilities capabilities,
      MediaInspectionResult inspection)
      throws IOException {
    FfmpegResolution resolution = ffmpegPathResolver.resolve();

    if (!resolution.available()) {
      throw new IOException("FFmpeg unavailable.");
    }

    Path temporaryPath =
        playbackDirectory.resolve(compatibilityPath.getFileName().toString() + ".tmp");

    List<String> command =
        normalizationCommand(resolution, originalPath, temporaryPath, capabilities, inspection);

    Process process = null;

    try {
      process = new ProcessBuilder(command).redirectErrorStream(true).start();
      boolean completed = process.waitFor(TRANSCODE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);

      if (!completed) {
        process.destroyForcibly();
        throw new IOException("Compatibility playback generation timed out.");
      }

      int exitCode = process.exitValue();
      LOGGER.info(
          "video_playback_ffmpeg_result exitCode={} cacheFile={}",
          exitCode,
          compatibilityPath.getFileName());

      if (exitCode != 0 || !Files.isRegularFile(temporaryPath) || Files.size(temporaryPath) == 0) {
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

  private List<String> normalizationCommand(
      FfmpegResolution resolution,
      Path originalPath,
      Path temporaryPath,
      FfmpegEncoderCapabilities capabilities,
      MediaInspectionResult inspection) {
    List<String> command = new ArrayList<>();
    command.add(resolution.executablePath().toString());
    command.add("-hide_banner");
    command.add("-loglevel");
    command.add("error");
    command.add("-i");
    command.add(originalPath.toString());
    command.add("-map");
    command.add("0:v:0");
    command.add("-map");
    command.add("0:a:0?");

    String rotationFilter = rotationFilter(inspection.rotationDegrees());
    if (rotationFilter != null) {
      command.add("-vf");
      command.add(rotationFilter);
    }

    command.add("-map_metadata");
    command.add("-1");
    command.add("-metadata:s:v:0");
    command.add("rotate=0");
    command.add("-c:v");
    command.add(capabilities.h264EncoderName());
    command.addAll(videoEncoderOptions(capabilities.h264EncoderName()));
    command.add("-pix_fmt");
    command.add("yuv420p");
    command.add("-c:a");
    command.add("aac");
    command.add("-b:a");
    command.add("128k");
    command.add("-movflags");
    command.add("+faststart");
    command.add("-y");
    command.add(temporaryPath.toString());

    return List.copyOf(command);
  }

  private List<String> videoEncoderOptions(String encoderName) {
    if ("libx264".equals(encoderName)) {
      return List.of("-preset", "veryfast", "-crf", "23", "-profile:v", "main", "-level", "4.0");
    }

    return List.of("-b:v", "4000k", "-profile:v", "main");
  }

  private String rotationFilter(Integer rotationDegrees) {
    if (rotationDegrees == null || rotationDegrees == 0) {
      return null;
    }

    return switch (rotationDegrees) {
      case 90 -> "transpose=clock";
      case 180 -> "hflip,vflip";
      case 270 -> "transpose=cclock";
      default -> null;
    };
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
              "normalizationVersion=" + NORMALIZATION_VERSION,
              memory.getSourceId(),
              originalPath.toAbsolutePath().normalize().toString(),
              String.valueOf(attributes.size()),
              String.valueOf(attributes.lastModifiedTime().toMillis()));

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

  private void logInspection(String memoryId, MediaInspectionResult inspection) {
    LOGGER.info(
        "video_playback_inspection mediaId={} container={} videoCodec={} audioCodec={} pixelFormat={} width={} height={} rotation={} durationSeconds={} disposition={} decision={}",
        memoryId,
        inspection.containerFormat(),
        inspection.videoCodec(),
        inspection.audioCodec(),
        inspection.pixelFormat(),
        inspection.width(),
        inspection.height(),
        inspection.rotationDegrees(),
        inspection.durationSeconds(),
        inspection.videoDisposition(),
        inspection.decisionReason());
  }
}
