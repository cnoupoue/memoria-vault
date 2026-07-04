package be.cnoupoue.memoriavault.thumbnail;

public class ThumbnailUnavailableException extends RuntimeException {

  public ThumbnailUnavailableException(String message) {
    super(message);
  }

  public ThumbnailUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
