package be.cnoupoue.memoriavault.source;

public class SourceUnavailableDuringScanException extends RuntimeException {

  public SourceUnavailableDuringScanException() {
    super("The configured source folder became unavailable during the scan.");
  }
}
