package be.cnoupoue.memoriavault.platform.common;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformDiagnosticInfo;
import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import be.cnoupoue.memoriavault.platform.PlatformService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public abstract class AbstractPlatformService implements PlatformService {

  private final String architecture;
  private volatile PlatformRuntimePaths cachedRuntimePaths;

  protected AbstractPlatformService() {
    this(System.getProperty("os.arch", "unknown"));
  }

  protected AbstractPlatformService(String architecture) {
    this.architecture = sanitizeLabel(architecture);
  }

  @Override
  public PlatformCapabilities getCapabilities() {
    return PlatformCapabilities.unsupported();
  }

  @Override
  public Optional<Path> resolveBundledFfmpegPath() {
    return runtimePaths().bundledFfmpegPath().flatMap(this::executableCandidate);
  }

  @Override
  public Optional<Path> resolveApplicationBundlePath() {
    return runtimePaths().applicationBundlePath().filter(Files::isDirectory);
  }

  @Override
  public Optional<Path> resolveApplicationLauncherPath() {
    return runtimePaths().applicationLauncherPath().flatMap(this::executableCandidate);
  }

  @Override
  public PlatformDiagnosticInfo getDiagnosticInfo() {
    return new PlatformDiagnosticInfo(publicOsName(), architecture, packagingLabel());
  }

  protected PlatformRuntimePaths runtimePaths() {
    PlatformRuntimePaths runtimePaths = cachedRuntimePaths;

    if (runtimePaths != null) {
      return runtimePaths;
    }

    runtimePaths = detectRuntimePaths();
    cachedRuntimePaths = runtimePaths;
    return runtimePaths;
  }

  protected PlatformRuntimePaths detectRuntimePaths() {
    return PlatformRuntimePaths.empty();
  }

  protected Optional<Path> executableCandidate(Path candidate) {
    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
      return Optional.of(candidate.toAbsolutePath().normalize());
    }

    return Optional.empty();
  }

  protected String architecture() {
    return architecture;
  }

  protected String packagingLabel() {
    return resolveApplicationBundlePath().isPresent() ? "jpackage" : "development";
  }

  protected abstract String publicOsName();

  private static String sanitizeLabel(String label) {
    if (label == null || label.isBlank()) {
      return "unknown";
    }

    return label.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
