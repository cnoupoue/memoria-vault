package be.cnoupoue.memoriavault.platform.macos;

import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MacosRuntimePaths {

  private static final Logger LOGGER = LoggerFactory.getLogger(MacosRuntimePaths.class);
  private static final String APP_NAME = "Memoria Vault";
  private static final String FFMPEG_BINARY = "ffmpeg";

  public PlatformRuntimePaths detect() {
    return detect(codeSourcePath());
  }

  PlatformRuntimePaths detect(Optional<Path> codeSourcePath) {
    Optional<Path> appDirectory = codeSourcePath.flatMap(this::findJpackageAppDirectory);
    Optional<Path> contentsDirectory = appDirectory.map(Path::getParent);
    Optional<Path> bundlePath = contentsDirectory.map(Path::getParent).filter(Files::isDirectory);

    return new PlatformRuntimePaths(
        bundlePath,
        contentsDirectory.map(directory -> directory.resolve("MacOS").resolve(APP_NAME)),
        appDirectory.map(directory -> directory.resolve("ffmpeg").resolve(FFMPEG_BINARY)));
  }

  private Optional<Path> codeSourcePath() {
    try {
      URI codeSourceUri =
          MacosRuntimePaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
      return Optional.of(Path.of(codeSourceUri).toAbsolutePath().normalize());
    } catch (IllegalArgumentException | SecurityException | URISyntaxException exception) {
      LOGGER.debug("Could not inspect application location for macOS runtime paths.", exception);
      return Optional.empty();
    }
  }

  private Optional<Path> findJpackageAppDirectory(Path codeSourcePath) {
    Path cursor = Files.isDirectory(codeSourcePath) ? codeSourcePath : codeSourcePath.getParent();

    while (cursor != null) {
      if (cursor.getFileName() != null
          && "app".equals(cursor.getFileName().toString())
          && cursor.getParent() != null
          && "Contents".equals(cursor.getParent().getFileName().toString())) {
        return Optional.of(cursor.toAbsolutePath().normalize());
      }

      cursor = cursor.getParent();
    }

    return Optional.empty();
  }
}
