package be.cnoupoue.memoriavault.streaming;

import be.cnoupoue.memoriavault.web.ApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public class MediaStreamingException extends ApiException {

  private final HttpHeaders headers;

  public MediaStreamingException(MediaStreamingFailureCategory category) {
    this(category, null, new HttpHeaders());
  }

  public MediaStreamingException(MediaStreamingFailureCategory category, Throwable cause) {
    this(category, cause, new HttpHeaders());
  }

  private MediaStreamingException(
      MediaStreamingFailureCategory category, Throwable cause, HttpHeaders headers) {
    super(category.getStatus(), category.name(), category.getMessage(), cause);
    this.headers = headers;
  }

  public static MediaStreamingException invalidRange(long contentLength) {
    return invalidRange(contentLength, null);
  }

  public static MediaStreamingException invalidRange(long contentLength, Throwable cause) {
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
    headers.set(HttpHeaders.CONTENT_RANGE, "bytes */%d".formatted(contentLength));

    return new MediaStreamingException(
        MediaStreamingFailureCategory.RANGE_REQUEST_INVALID, cause, headers);
  }

  public HttpStatus getStatus() {
    return super.getStatus();
  }

  public HttpHeaders getHeaders() {
    return headers;
  }
}
