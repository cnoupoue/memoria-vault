package be.cnoupoue.memoriavault.source;

public record SourceAvailability(SourceAvailabilityStatus status, String message) {

  public boolean isAvailable() {
    return status == SourceAvailabilityStatus.AVAILABLE;
  }
}
