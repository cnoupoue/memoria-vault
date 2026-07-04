package be.cnoupoue.memoriavault.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class InternalRenameGuardTest {

  @Test
  void activeJavaSourcesDoNotUseOldBasePackage() throws Exception {
    try (Stream<Path> paths = Files.walk(Path.of("src/main/java"))) {
      assertThat(
              paths
                  .filter(path -> path.toString().endsWith(".java"))
                  .map(this::readUnchecked)
                  .filter(contents -> contents.contains("be.cnoupoue.snapmemoria"))
                  .toList())
          .isEmpty();
    }
  }

  @Test
  void mavenMetadataUsesPublicProductNameAndNewArtifactId() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(pom).contains("<artifactId>memoria-vault</artifactId>");
    assertThat(pom).contains("<name>Memoria Vault</name>");
    assertThat(pom).doesNotContain("<artifactId>snapmemoria</artifactId>");
  }

  private String readUnchecked(Path path) {
    try {
      return Files.readString(path);
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
