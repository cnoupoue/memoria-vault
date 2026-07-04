package be.cnoupoue.snapmemoria.platform;

import java.nio.file.Path;
import java.util.Optional;

public interface PlatformService {

  PlatformType getPlatformType();

  PlatformCapabilities getCapabilities();

  Optional<Path> resolveBundledFfmpegPath();

  Optional<Path> resolveApplicationBundlePath();

  Optional<Path> resolveApplicationLauncherPath();

  PlatformDiagnosticInfo getDiagnosticInfo();
}
