package be.cnoupoue.snapmemoria.source;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.stereotype.Service;

@Service
public class SourceAvailabilityService {

  public SourceAvailability check(MemorySource source) {
    return check(Path.of(source.getRootPath()));
  }

  public SourceAvailability check(Path rootPath) {
    if (!Files.exists(rootPath)) {
      return new SourceAvailability(
          SourceAvailabilityStatus.UNAVAILABLE,
          "Connect the drive containing this source, then refresh its status.");
    }

    if (!Files.isDirectory(rootPath)) {
      return new SourceAvailability(
          SourceAvailabilityStatus.NOT_A_DIRECTORY,
          "The configured source location is not a folder.");
    }

    if (!Files.isReadable(rootPath)) {
      return new SourceAvailability(
          SourceAvailabilityStatus.NOT_READABLE, "The configured source folder is not readable.");
    }

    return new SourceAvailability(
        SourceAvailabilityStatus.AVAILABLE, "Source folder is available.");
  }
}
