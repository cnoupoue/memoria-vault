package be.cnoupoue.snapmemoria.platform.macos;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.snapmemoria.platform.PlatformRuntimePaths;
import be.cnoupoue.snapmemoria.platform.PlatformService;
import be.cnoupoue.snapmemoria.platform.PlatformType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacosPlatformServiceTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void resolvesExecutableBundledFfmpegFromRuntimePaths() throws Exception {
    Path appBundle = Files.createDirectories(temporaryDirectory.resolve("Memoria Vault.app"));
    Path launcher = executable(appBundle.resolve("Contents/MacOS/Memoria Vault"));
    Path ffmpeg = executable(appBundle.resolve("Contents/app/ffmpeg/ffmpeg"));

    PlatformService service =
        new MacosPlatformService(
            new StaticMacosRuntimePaths(
                new PlatformRuntimePaths(
                    Optional.of(appBundle), Optional.of(launcher), Optional.of(ffmpeg))));

    assertThat(service.getPlatformType()).isEqualTo(PlatformType.MACOS);
    assertThat(service.resolveApplicationBundlePath()).contains(appBundle);
    assertThat(service.resolveApplicationLauncherPath()).contains(launcher);
    assertThat(service.resolveBundledFfmpegPath()).contains(ffmpeg);
    assertThat(service.getDiagnosticInfo().os()).isEqualTo("macOS");
    assertThat(service.getDiagnosticInfo().packaging()).isEqualTo("jpackage");
  }

  private Path executable(Path path) throws Exception {
    Files.createDirectories(path.getParent());
    Files.writeString(path, "#!/bin/sh\nexit 0\n");
    path.toFile().setExecutable(true);
    return path.toAbsolutePath().normalize();
  }

  private static class StaticMacosRuntimePaths extends MacosRuntimePaths {

    private final PlatformRuntimePaths paths;

    StaticMacosRuntimePaths(PlatformRuntimePaths paths) {
      this.paths = paths;
    }

    @Override
    public PlatformRuntimePaths detect() {
      return paths;
    }
  }
}
