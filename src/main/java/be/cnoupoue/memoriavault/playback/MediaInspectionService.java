package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.ffmpeg.FfmpegPathResolver;
import be.cnoupoue.memoriavault.ffmpeg.FfmpegResolution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MediaInspectionService {

  private static final Logger LOGGER = LoggerFactory.getLogger(MediaInspectionService.class);
  private static final Duration INSPECTION_TIMEOUT = Duration.ofSeconds(15);
  private static final Pattern DISPLAY_MATRIX_ROTATION_PATTERN =
      Pattern.compile("rotation of\\s+(-?\\d+(?:\\.\\d+)?)\\s+degrees", Pattern.CASE_INSENSITIVE);

  private final FfmpegPathResolver ffmpegPathResolver;
  private final ObjectMapper objectMapper;
  private final ExecutableValidator executableValidator;

  @Autowired
  public MediaInspectionService(FfmpegPathResolver ffmpegPathResolver) {
    this(ffmpegPathResolver, new ObjectMapper(), MediaInspectionService::runsVersionCommand);
  }

  MediaInspectionService(
      FfmpegPathResolver ffmpegPathResolver,
      ObjectMapper objectMapper,
      ExecutableValidator executableValidator) {
    this.ffmpegPathResolver = ffmpegPathResolver;
    this.objectMapper = objectMapper;
    this.executableValidator = executableValidator;
  }

  public MediaInspectionResult inspect(Path mediaPath) throws IOException {
    Path ffprobePath = resolveFfprobePath();
    JsonNode root = probe(ffprobePath, mediaPath);
    JsonNode format = root.path("format");
    JsonNode videoStream = firstStream(root, "video");
    JsonNode audioStream = firstStream(root, "audio");

    String containerFormat = text(format, "format_name");
    String videoCodec = text(videoStream, "codec_name");
    String audioCodec = text(audioStream, "codec_name");
    String pixelFormat = text(videoStream, "pix_fmt");
    Integer width = integer(videoStream, "width");
    Integer height = integer(videoStream, "height");
    Integer rotationDegrees = rotationDegrees(videoStream);
    Map<String, Integer> videoDisposition = disposition(videoStream);
    Double durationSeconds = duration(format, videoStream);
    String decisionReason =
        directPlaybackDecision(
            containerFormat, videoCodec, audioCodec, pixelFormat, rotationDegrees);
    boolean directPlayable = "direct".equals(decisionReason);

    return new MediaInspectionResult(
        containerFormat,
        videoCodec,
        audioCodec,
        pixelFormat,
        width,
        height,
        rotationDegrees,
        videoDisposition,
        durationSeconds,
        directPlayable,
        decisionReason);
  }

  private Path resolveFfprobePath() throws IOException {
    FfmpegResolution ffmpegResolution = ffmpegPathResolver.resolve();

    if (!ffmpegResolution.available()) {
      throw new IOException("FFmpeg is unavailable; FFprobe cannot be resolved.");
    }

    Path ffmpegPath = ffmpegResolution.executablePath();
    Path ffprobePath = ffmpegPath.resolveSibling(ffprobeBinaryName(ffmpegPath));

    if (!Files.isRegularFile(ffprobePath)
        || !Files.isExecutable(ffprobePath)
        || !executableValidator.isValid(ffprobePath)) {
      throw new IOException("FFprobe is unavailable next to the resolved FFmpeg binary.");
    }

    return ffprobePath.toAbsolutePath().normalize();
  }

  private String ffprobeBinaryName(Path ffmpegPath) {
    String ffmpegName = ffmpegPath.getFileName().toString();

    return ffmpegName.toLowerCase().endsWith(".exe") ? "ffprobe.exe" : "ffprobe";
  }

  private JsonNode probe(Path ffprobePath, Path mediaPath) throws IOException {
    Process process = null;

    try {
      process =
          new ProcessBuilder(
                  ffprobePath.toString(),
                  "-v",
                  "error",
                  "-print_format",
                  "json",
                  "-show_format",
                  "-show_streams",
                  mediaPath.toString())
              .redirectErrorStream(true)
              .start();

      boolean completed = process.waitFor(INSPECTION_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
      String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

      if (!completed) {
        process.destroyForcibly();
        throw new IOException("FFprobe timed out while inspecting media.");
      }

      if (process.exitValue() != 0) {
        throw new IOException("FFprobe could not inspect media.");
      }

      return objectMapper.readTree(output);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IOException("FFprobe inspection was interrupted.", exception);
    }
  }

  private JsonNode firstStream(JsonNode root, String streamType) {
    JsonNode streams = root.path("streams");

    if (!streams.isArray()) {
      return objectMapper.nullNode();
    }

    for (JsonNode stream : streams) {
      if (streamType.equals(text(stream, "codec_type"))) {
        return stream;
      }
    }

    return objectMapper.nullNode();
  }

  private String directPlaybackDecision(
      String containerFormat,
      String videoCodec,
      String audioCodec,
      String pixelFormat,
      Integer rotationDegrees) {
    if (!containerContains(containerFormat, "mp4")) {
      return "container_requires_normalization";
    }

    if (!"h264".equals(videoCodec)) {
      return "video_codec_requires_normalization";
    }

    if (!"yuv420p".equals(pixelFormat)) {
      return "pixel_format_requires_normalization";
    }

    if (audioCodec != null && !"aac".equals(audioCodec)) {
      return "audio_codec_requires_normalization";
    }

    if (rotationDegrees != null && rotationDegrees != 0) {
      return "rotation_requires_normalization";
    }

    return "direct";
  }

  private boolean containerContains(String containerFormat, String expectedToken) {
    if (containerFormat == null) {
      return false;
    }

    for (String token : containerFormat.split(",")) {
      if (expectedToken.equals(token.trim())) {
        return true;
      }
    }

    return false;
  }

  private Integer rotationDegrees(JsonNode videoStream) {
    Optional<Integer> tagRotation =
        Optional.ofNullable(text(videoStream.path("tags"), "rotate")).flatMap(this::parseRotation);

    if (tagRotation.isPresent()) {
      return tagRotation.get();
    }

    JsonNode sideDataList = videoStream.path("side_data_list");
    if (!sideDataList.isArray()) {
      return null;
    }

    for (JsonNode sideData : sideDataList) {
      Optional<Integer> sideDataRotation = rotationFromSideData(sideData);

      if (sideDataRotation.isPresent()) {
        return sideDataRotation.get();
      }
    }

    return null;
  }

  private Optional<Integer> rotationFromSideData(JsonNode sideData) {
    if (sideData.has("rotation")) {
      JsonNode rotation = sideData.path("rotation");

      if (rotation.isNumber()) {
        return Optional.of(normalizeRotation(rotation.asDouble()));
      }

      if (rotation.isTextual()) {
        return parseRotation(rotation.asText());
      }
    }

    String displayMatrix = text(sideData, "displaymatrix");
    if (displayMatrix == null) {
      return Optional.empty();
    }

    Matcher matcher = DISPLAY_MATRIX_ROTATION_PATTERN.matcher(displayMatrix);

    if (!matcher.find()) {
      return Optional.empty();
    }

    return parseRotation(matcher.group(1));
  }

  private Optional<Integer> parseRotation(String value) {
    try {
      return Optional.of(normalizeRotation(Double.parseDouble(value.trim())));
    } catch (NumberFormatException exception) {
      LOGGER.debug("Ignoring unparsable video rotation metadata.");
      return Optional.empty();
    }
  }

  private int normalizeRotation(double rotationDegrees) {
    int roundedDegrees = (int) Math.round(rotationDegrees);
    int normalized = roundedDegrees % 360;

    return normalized < 0 ? normalized + 360 : normalized;
  }

  private Map<String, Integer> disposition(JsonNode videoStream) {
    JsonNode disposition = videoStream.path("disposition");
    Map<String, Integer> values = new LinkedHashMap<>();

    if (!disposition.isObject()) {
      return values;
    }

    Iterator<Map.Entry<String, JsonNode>> fields = disposition.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> field = fields.next();

      if (field.getValue().canConvertToInt()) {
        values.put(field.getKey(), field.getValue().asInt());
      }
    }

    return Map.copyOf(values);
  }

  private Double duration(JsonNode format, JsonNode videoStream) {
    Double formatDuration = decimal(format, "duration");

    if (formatDuration != null) {
      return formatDuration;
    }

    return decimal(videoStream, "duration");
  }

  private String text(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);

    return value.isMissingNode() || value.isNull() || !value.isValueNode() ? null : value.asText();
  }

  private Integer integer(JsonNode node, String fieldName) {
    JsonNode value = node.path(fieldName);

    return value.canConvertToInt() ? value.asInt() : null;
  }

  private Double decimal(JsonNode node, String fieldName) {
    String value = text(node, fieldName);

    if (value == null) {
      return null;
    }

    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      return null;
    }
  }

  private static boolean runsVersionCommand(Path candidate) {
    Process process = null;
    try {
      process =
          new ProcessBuilder(candidate.toString(), "-version")
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();

      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        return false;
      }

      return process.exitValue() == 0;
    } catch (IOException exception) {
      return false;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @FunctionalInterface
  interface ExecutableValidator {
    boolean isValid(Path candidate);
  }
}
