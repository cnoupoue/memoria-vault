package be.cnoupoue.memoriavault.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;

class MemoriaVaultEnvironmentPostProcessorTest {

  @TempDir private Path userHome;

  @Test
  void appliesCanonicalDataDirectoryDefaults() {
    StandardEnvironment environment = environmentWith("user.home=" + userHome);

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("memoriavault.data.directory"))
        .isEqualTo(userHome.resolve(".memoria-vault").toString());
    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:sqlite:" + userHome.resolve(".memoria-vault/data/memoriavault.db"));
    assertThat(environment.getProperty("memoriavault.thumbnail.directory"))
        .isEqualTo(userHome.resolve(".memoria-vault/cache/thumbnails").toString());
    assertThat(userHome.resolve(".memoria-vault/data")).isDirectory();
    assertThat(userHome.resolve(".memoria-vault/cache/thumbnails")).isDirectory();
  }

  @Test
  void detectsLegacyDataDirectoryAndKeepsThumbnailCacheAvailable() throws Exception {
    Files.createDirectories(userHome.resolve(".snapmemoria/cache/thumbnails"));

    StandardEnvironment environment = environmentWith("user.home=" + userHome);

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:sqlite:" + userHome.resolve(".snapmemoria/data/snapmemoria.db"));
    assertThat(environment.getProperty("memoriavault.thumbnail.directory"))
        .isEqualTo(userHome.resolve(".snapmemoria/cache/thumbnails").toString());
    assertThat(userHome.resolve(".snapmemoria/data")).isDirectory();
  }

  @Test
  void configuredDataDirectoryIsPreparedForSqliteAndThumbnails() {
    Path configuredRoot = userHome.resolve("custom-data");
    StandardEnvironment environment =
        environmentWith("user.home=" + userHome, "memoriavault.data.directory=" + configuredRoot);

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:sqlite:" + configuredRoot.resolve("data/memoriavault.db"));
    assertThat(configuredRoot.resolve("data")).isDirectory();
    assertThat(configuredRoot.resolve("cache/thumbnails")).isDirectory();
  }

  @Test
  void deprecatedConfigurationAliasesAreSupportedWhenNewKeysAreAbsent() {
    StandardEnvironment environment =
        environmentWith(
            "user.home=" + userHome,
            "snapmemoria.ffmpeg.path=/tmp/legacy-ffmpeg",
            "snapmemoria.thumbnail.max-width=320");

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("memoriavault.ffmpeg.path")).isEqualTo("/tmp/legacy-ffmpeg");
    assertThat(environment.getProperty("memoriavault.thumbnail.max-width")).isEqualTo("320");
  }

  @Test
  void newConfigurationKeysTakePrecedenceOverDeprecatedAliases() {
    StandardEnvironment environment =
        environmentWith(
            "user.home=" + userHome,
            "memoriavault.ffmpeg.path=/tmp/new-ffmpeg",
            "snapmemoria.ffmpeg.path=/tmp/legacy-ffmpeg");

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("memoriavault.ffmpeg.path")).isEqualTo("/tmp/new-ffmpeg");
  }

  @Test
  void explicitDatasourceUrlIsNotOverwrittenByDataDirectoryDefaults() {
    StandardEnvironment environment =
        environmentWith(
            "user.home=" + userHome, "spring.datasource.url=jdbc:sqlite:/tmp/explicit.db");

    new MemoriaVaultEnvironmentPostProcessor()
        .postProcessEnvironment(environment, new SpringApplication());

    assertThat(environment.getProperty("spring.datasource.url"))
        .isEqualTo("jdbc:sqlite:/tmp/explicit.db");
  }

  private StandardEnvironment environmentWith(String... properties) {
    StandardEnvironment environment = new StandardEnvironment();
    TestPropertySourceUtils.addInlinedPropertiesToEnvironment(environment, properties);
    return environment;
  }
}
