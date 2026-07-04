package be.cnoupoue.snapmemoria.platform.macos;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.snapmemoria.platform.PlatformRuntimePaths;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MacosRuntimePathsTest {

  @TempDir private Path temporaryDirectory;

  @Test
  void resolvesJpackageBundlePathsFromCodeSourceInsideContentsApp() throws Exception {
    Path appBundle = Files.createDirectories(temporaryDirectory.resolve("Memoria Vault.app"));
    Path contents = Files.createDirectories(appBundle.resolve("Contents"));
    Path appDirectory = Files.createDirectories(contents.resolve("app"));
    Path jar = Files.writeString(appDirectory.resolve("snapmemoria.jar"), "jar");

    PlatformRuntimePaths paths = new MacosRuntimePaths().detect(Optional.of(jar));

    assertThat(paths.applicationBundlePath()).contains(appBundle);
    assertThat(paths.applicationLauncherPath()).contains(contents.resolve("MacOS/Memoria Vault"));
    assertThat(paths.bundledFfmpegPath()).contains(appDirectory.resolve("ffmpeg/ffmpeg"));
  }

  @Test
  void returnsEmptyPathsOutsideJpackageLayout() throws Exception {
    Path classesDirectory = Files.createDirectories(temporaryDirectory.resolve("classes"));

    PlatformRuntimePaths paths = new MacosRuntimePaths().detect(Optional.of(classesDirectory));

    assertThat(paths.applicationBundlePath()).isEmpty();
    assertThat(paths.applicationLauncherPath()).isEmpty();
    assertThat(paths.bundledFfmpegPath()).isEmpty();
  }
}
