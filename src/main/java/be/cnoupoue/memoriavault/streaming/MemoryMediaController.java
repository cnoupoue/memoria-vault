package be.cnoupoue.memoriavault.streaming;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
public class MemoryMediaController {

  private static final Logger LOGGER = LoggerFactory.getLogger(MemoryMediaController.class);

  private final MemoryMediaService memoryMediaService;
  private final MediaMimeTypeResolver mediaMimeTypeResolver;

  public MemoryMediaController(
      MemoryMediaService memoryMediaService, MediaMimeTypeResolver mediaMimeTypeResolver) {
    this.memoryMediaService = memoryMediaService;
    this.mediaMimeTypeResolver = mediaMimeTypeResolver;
  }

  @RequestMapping(
      value = "/{id}/media",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<?> getMainMedia(@PathVariable String id, HttpServletRequest request)
      throws IOException {
    try {
      FileSystemResource resource = memoryMediaService.getMainMedia(id);

      return buildMediaResponse(id, resource, request);
    } catch (MediaStreamingException exception) {
      logStreamingFailure(id, request, exception);
      throw exception;
    } catch (IOException exception) {
      MediaStreamingException streamingException =
          new MediaStreamingException(MediaStreamingFailureCategory.MEDIA_READ_FAILED, exception);
      logStreamingFailure(id, request, streamingException);
      throw streamingException;
    }
  }

  @RequestMapping(
      value = "/{id}/overlay",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<?> getOverlay(@PathVariable String id, HttpServletRequest request)
      throws IOException {
    try {
      FileSystemResource resource = memoryMediaService.getOverlay(id);

      return buildMediaResponse(id, resource, request);
    } catch (MediaStreamingException exception) {
      logStreamingFailure(id, request, exception);
      throw exception;
    } catch (IOException exception) {
      MediaStreamingException streamingException =
          new MediaStreamingException(MediaStreamingFailureCategory.MEDIA_READ_FAILED, exception);
      logStreamingFailure(id, request, streamingException);
      throw streamingException;
    }
  }

  @RequestMapping(
      value = "/{id}/playback/compatible/media",
      method = {RequestMethod.GET, RequestMethod.HEAD})
  public ResponseEntity<?> getCompatiblePlaybackMedia(
      @PathVariable String id, HttpServletRequest request) throws IOException {
    try {
      FileSystemResource resource = memoryMediaService.getCompatiblePlaybackMedia(id);

      return buildMediaResponse(id, resource, request);
    } catch (MediaStreamingException exception) {
      logStreamingFailure(id, request, exception);
      throw exception;
    } catch (IOException exception) {
      MediaStreamingException streamingException =
          new MediaStreamingException(MediaStreamingFailureCategory.MEDIA_READ_FAILED, exception);
      logStreamingFailure(id, request, streamingException);
      throw streamingException;
    }
  }

  private ResponseEntity<Resource> buildMediaResponse(
      String mediaId, Resource resource, HttpServletRequest request) throws IOException {
    long contentLength = resource.contentLength();

    MediaType mediaType = mediaMimeTypeResolver.resolve(resource);

    String rangeHeader = request.getHeader(HttpHeaders.RANGE);

    if (rangeHeader == null) {
      logStreamingResponse(mediaId, request, HttpStatus.OK, mediaType, contentLength);

      return ResponseEntity.ok()
          .contentType(mediaType)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .contentLength(contentLength)
          .body(isHeadRequest(request) ? null : resource);
    }

    List<HttpRange> ranges;

    try {
      ranges = HttpRange.parseRanges(rangeHeader);
    } catch (IllegalArgumentException exception) {
      throw MediaStreamingException.invalidRange(contentLength, exception);
    }

    if (ranges.size() != 1) {
      throw MediaStreamingException.invalidRange(contentLength);
    }

    HttpRange range = ranges.getFirst();

    long start = range.getRangeStart(contentLength);
    long end = range.getRangeEnd(contentLength);
    if (start < 0 || start >= contentLength || end < start) {
      throw MediaStreamingException.invalidRange(contentLength);
    }

    long rangeLength = end - start + 1;

    logStreamingResponse(mediaId, request, HttpStatus.PARTIAL_CONTENT, mediaType, rangeLength);

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(mediaType)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, contentLength))
        .contentLength(rangeLength)
        .body(isHeadRequest(request) ? null : boundedResource(resource, start, rangeLength));
  }

  private Resource boundedResource(Resource resource, long start, long length) throws IOException {
    SeekableByteChannel channel = Files.newByteChannel(resource.getFile().toPath());
    channel.position(start);

    return new InputStreamResource(new BoundedChannelInputStream(channel, length)) {
      @Override
      public String getFilename() {
        return resource.getFilename();
      }

      @Override
      public long contentLength() {
        return length;
      }
    };
  }

  private static class BoundedChannelInputStream extends InputStream {

    private final SeekableByteChannel channel;
    private long remaining;

    private BoundedChannelInputStream(SeekableByteChannel channel, long length) {
      this.channel = channel;
      this.remaining = length;
    }

    @Override
    public int read() throws IOException {
      byte[] singleByte = new byte[1];
      int read = read(singleByte, 0, 1);

      return read == -1 ? -1 : singleByte[0] & 0xff;
    }

    @Override
    public int read(byte[] bytes, int offset, int length) throws IOException {
      if (remaining <= 0) {
        return -1;
      }

      ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, (int) Math.min(length, remaining));
      int read = channel.read(buffer);

      if (read == -1) {
        return -1;
      }

      remaining -= read;
      return read;
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }
  }

  private boolean isHeadRequest(HttpServletRequest request) {
    return "HEAD".equalsIgnoreCase(request.getMethod());
  }

  private void logStreamingResponse(
      String mediaId,
      HttpServletRequest request,
      HttpStatus status,
      MediaType mediaType,
      long bodyLength) {
    LOGGER.info(
        "media_stream_result mediaId={} method={} rangeRequest={} status={} contentType={} bodyLength={}",
        mediaId,
        request.getMethod(),
        request.getHeader(HttpHeaders.RANGE) != null,
        status.value(),
        mediaType,
        bodyLength);
  }

  private void logStreamingFailure(
      String mediaId, HttpServletRequest request, MediaStreamingException exception) {
    Throwable cause = exception.getCause();
    String exceptionType =
        cause == null ? exception.getClass().getSimpleName() : cause.getClass().getSimpleName();

    LOGGER.warn(
        "media_stream_failure mediaId={} method={} rangeRequest={} status={} category={} exceptionType={}",
        mediaId,
        request.getMethod(),
        request.getHeader(HttpHeaders.RANGE) != null,
        exception.getStatus().value(),
        exception.getCode(),
        exceptionType);
  }
}
