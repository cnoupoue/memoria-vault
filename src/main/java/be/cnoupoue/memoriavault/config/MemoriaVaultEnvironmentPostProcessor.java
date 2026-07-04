package be.cnoupoue.memoriavault.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class MemoriaVaultEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(MemoriaVaultEnvironmentPostProcessor.class);

  private static final Map<String, String> DEPRECATED_ALIASES =
      Map.ofEntries(
          Map.entry("memoriavault.browser.auto-open", "snapmemoria.browser.auto-open"),
          Map.entry("memoriavault.browser.url", "snapmemoria.browser.url"),
          Map.entry("memoriavault.ffmpeg.path", "snapmemoria.ffmpeg.path"),
          Map.entry("memoriavault.thumbnail.directory", "snapmemoria.thumbnail.directory"),
          Map.entry("memoriavault.playback.directory", "snapmemoria.playback.directory"),
          Map.entry("memoriavault.thumbnail.max-width", "snapmemoria.thumbnail.max-width"),
          Map.entry("memoriavault.thumbnail.max-height", "snapmemoria.thumbnail.max-height"),
          Map.entry(
              "memoriavault.thumbnail.video-seek-seconds",
              "snapmemoria.thumbnail.video-seek-seconds"),
          Map.entry("memoriavault.data.directory", "snapmemoria.data.directory"));

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> overrides = new LinkedHashMap<>();

    applyDeprecatedAliases(environment, overrides);
    applyDataDirectoryDefaults(environment, overrides);

    if (!overrides.isEmpty()) {
      environment
          .getPropertySources()
          .addFirst(new MapPropertySource("memoriaVaultCompatibilityDefaults", overrides));
    }
  }

  @Override
  public int getOrder() {
    return Ordered.LOWEST_PRECEDENCE;
  }

  private void applyDeprecatedAliases(
      ConfigurableEnvironment environment, Map<String, Object> overrides) {
    for (Map.Entry<String, String> alias : DEPRECATED_ALIASES.entrySet()) {
      String newKey = alias.getKey();
      String oldKey = alias.getValue();

      if (StringUtils.hasText(environment.getProperty(newKey))) {
        continue;
      }

      String oldValue = environment.getProperty(oldKey);

      if (StringUtils.hasText(oldValue)) {
        overrides.put(newKey, oldValue);
        LOGGER.warn(
            "Configuration key '{}' is deprecated; use '{}' instead. The deprecated alias is retained for one release cycle.",
            oldKey,
            newKey);
      }
    }
  }

  private void applyDataDirectoryDefaults(
      ConfigurableEnvironment environment, Map<String, Object> overrides) {
    if (StringUtils.hasText(environment.getProperty("spring.datasource.url"))
        && StringUtils.hasText(environment.getProperty("memoriavault.thumbnail.directory"))) {
      return;
    }

    String configuredDataDirectory =
        firstText(
            valueFromOverridesOrEnvironment(overrides, environment, "memoriavault.data.directory"),
            environment.getProperty("snapmemoria.data.directory"));

    ApplicationDataDirectoryResolver.ApplicationDataDirectories directories =
        StringUtils.hasText(configuredDataDirectory)
            ? configuredDirectories(Path.of(configuredDataDirectory).toAbsolutePath().normalize())
            : new ApplicationDataDirectoryResolver(
                    Path.of(environment.getProperty("user.home", System.getProperty("user.home"))))
                .resolve();

    ensureRuntimeDirectoriesExist(directories);

    putDefaultIfMissing(
        overrides, environment, "memoriavault.data.directory", directories.root().toString());
    putDefaultIfMissing(
        overrides,
        environment,
        "spring.datasource.url",
        "jdbc:sqlite:" + directories.databasePath().toString());
    putDefaultIfMissing(
        overrides,
        environment,
        "memoriavault.thumbnail.directory",
        directories.thumbnailDirectory().toString());
    putDefaultIfMissing(
        overrides,
        environment,
        "memoriavault.playback.directory",
        directories.playbackDirectory().toString());

    if (directories.usingLegacyDirectory()) {
      LOGGER.warn(
          "Using legacy local data directory '{}'. New installations use '{}'. No files were moved or deleted.",
          directories.root(),
          directories
              .root()
              .getParent()
              .resolve(ApplicationDataDirectoryResolver.CANONICAL_DIRECTORY_NAME));
    }
  }

  private ApplicationDataDirectoryResolver.ApplicationDataDirectories configuredDirectories(
      Path root) {
    return new ApplicationDataDirectoryResolver.ApplicationDataDirectories(
        root,
        root.resolve("data").resolve(ApplicationDataDirectoryResolver.CANONICAL_DATABASE_FILE_NAME),
        root.resolve("cache").resolve("thumbnails"),
        root.resolve("cache").resolve("playback"),
        false);
  }

  private void ensureRuntimeDirectoriesExist(
      ApplicationDataDirectoryResolver.ApplicationDataDirectories directories) {
    try {
      Files.createDirectories(directories.databasePath().getParent());
      Files.createDirectories(directories.thumbnailDirectory());
      Files.createDirectories(directories.playbackDirectory());
    } catch (IOException exception) {
      throw new IllegalStateException(
          "Unable to prepare Memoria Vault local data directories.", exception);
    }
  }

  private String valueFromOverridesOrEnvironment(
      Map<String, Object> overrides, ConfigurableEnvironment environment, String key) {
    Object override = overrides.get(key);

    if (override instanceof String value) {
      return value;
    }

    return environment.getProperty(key);
  }

  private void putDefaultIfMissing(
      Map<String, Object> overrides,
      ConfigurableEnvironment environment,
      String key,
      String value) {
    if (!StringUtils.hasText(valueFromOverridesOrEnvironment(overrides, environment, key))) {
      overrides.put(key, value);
    }
  }

  private String firstText(String first, String second) {
    if (StringUtils.hasText(first)) {
      return first;
    }

    return second;
  }
}
