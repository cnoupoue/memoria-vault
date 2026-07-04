package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SourceAvailabilityServiceTest {

  private final SourceAvailabilityService service = new SourceAvailabilityService();

  @TempDir private Path temporaryDirectory;

  @Test
  void reportsAvailableDirectory() {
    SourceAvailability availability = service.check(temporaryDirectory);

    assertThat(availability.status()).isEqualTo(SourceAvailabilityStatus.AVAILABLE);
    assertThat(availability.message()).isEqualTo("Source folder is available.");
    assertThat(availability.isAvailable()).isTrue();
  }

  @Test
  void reportsMissingDirectoryWithoutLeakingPath() {
    Path missingPath = temporaryDirectory.resolve("missing");

    SourceAvailability availability = service.check(missingPath);

    assertThat(availability.status()).isEqualTo(SourceAvailabilityStatus.UNAVAILABLE);
    assertThat(availability.message())
        .isEqualTo("Connect the drive containing this source, then refresh its status.");
    assertThat(availability.message()).doesNotContain(missingPath.toString());
    assertThat(availability.isAvailable()).isFalse();
  }

  @Test
  void reportsFileInsteadOfDirectory() throws Exception {
    Path file = Files.createFile(temporaryDirectory.resolve("export.txt"));

    SourceAvailability availability = service.check(file);

    assertThat(availability.status()).isEqualTo(SourceAvailabilityStatus.NOT_A_DIRECTORY);
    assertThat(availability.message()).isEqualTo("The configured source location is not a folder.");
  }
}
