package be.cnoupoue.memoriavault.streaming;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/memories")
public class MemoryMediaController {

  private final MemoryMediaService memoryMediaService;

  public MemoryMediaController(MemoryMediaService memoryMediaService) {
    this.memoryMediaService = memoryMediaService;
  }

  @GetMapping("/{id}/media")
  public ResponseEntity<ResourceRegion> getMainMedia(
      @PathVariable String id, HttpServletRequest request) throws IOException {
    FileSystemResource resource = memoryMediaService.getMainMedia(id);

    return buildMediaResponse(resource, request);
  }

  @GetMapping("/{id}/overlay")
  public ResponseEntity<ResourceRegion> getOverlay(
      @PathVariable String id, HttpServletRequest request) throws IOException {
    FileSystemResource resource = memoryMediaService.getOverlay(id);

    return buildMediaResponse(resource, request);
  }

  private ResponseEntity<ResourceRegion> buildMediaResponse(
      Resource resource, HttpServletRequest request) throws IOException {
    long contentLength = resource.contentLength();

    MediaType mediaType =
        MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);

    String rangeHeader = request.getHeader(HttpHeaders.RANGE);

    if (rangeHeader == null) {
      ResourceRegion region = new ResourceRegion(resource, 0, contentLength);

      return ResponseEntity.ok()
          .contentType(mediaType)
          .header(HttpHeaders.ACCEPT_RANGES, "bytes")
          .contentLength(contentLength)
          .body(region);
    }

    List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);

    if (ranges.size() != 1) {
      return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).build();
    }

    HttpRange range = ranges.getFirst();

    long start = range.getRangeStart(contentLength);
    long end = range.getRangeEnd(contentLength);
    long rangeLength = end - start + 1;

    ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

    return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
        .contentType(mediaType)
        .header(HttpHeaders.ACCEPT_RANGES, "bytes")
        .header(HttpHeaders.CONTENT_RANGE, "bytes %d-%d/%d".formatted(start, end, contentLength))
        .contentLength(rangeLength)
        .body(region);
  }
}
