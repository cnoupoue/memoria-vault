package be.cnoupoue.memoriavault.streaming;

import org.springframework.http.HttpStatus;

public enum MediaStreamingFailureCategory {
  SOURCE_UNAVAILABLE(
      HttpStatus.NOT_FOUND,
      "This archive source is currently unavailable. Reconnect the drive containing it and try again."),
  MEDIA_FILE_MISSING(
      HttpStatus.NOT_FOUND,
      "This original file could not be found in the selected archive folder. Try rescanning the source if files were moved or changed."),
  MEDIA_PATH_REJECTED(
      HttpStatus.FORBIDDEN, "The requested file is outside the configured memory source."),
  MEDIA_READ_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "The video file is available, but it could not be streamed locally."),
  RANGE_REQUEST_INVALID(
      HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
      "The requested media byte range is not available."),
  VIDEO_STREAM_FAILED(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "The video file is available, but it could not be streamed locally."),
  VIDEO_CONTENT_TYPE_UNSUPPORTED(
      HttpStatus.UNSUPPORTED_MEDIA_TYPE,
      "The video file is available, but this browser may not be able to play its format.");

  private final HttpStatus status;
  private final String message;

  MediaStreamingFailureCategory(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getMessage() {
    return message;
  }
}
