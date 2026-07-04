package be.cnoupoue.memoriavault.platform;

import java.nio.file.Path;
import java.util.Optional;

public record PlatformRuntimePaths(
    Optional<Path> applicationBundlePath,
    Optional<Path> applicationLauncherPath,
    Optional<Path> bundledFfmpegPath) {

  public static PlatformRuntimePaths empty() {
    return new PlatformRuntimePaths(Optional.empty(), Optional.empty(), Optional.empty());
  }
}
