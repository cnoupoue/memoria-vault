package be.cnoupoue.memoriavault.playback;

import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformType;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DesktopLocalFileOpener implements LocalFileOpener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DesktopLocalFileOpener.class);

  private final PlatformService platformService;
  private final ProcessStarter processStarter;

  @Autowired
  public DesktopLocalFileOpener(PlatformService platformService) {
    this(platformService, new ProcessBuilderStarter());
  }

  DesktopLocalFileOpener(PlatformService platformService, ProcessStarter processStarter) {
    this.platformService = platformService;
    this.processStarter = processStarter;
  }

  @Override
  public void open(Path path) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      throw new OriginalFileOpenException(
          "Opening local files is unavailable in this environment.");
    }

    try {
      Desktop.getDesktop().open(path.toFile());
    } catch (IOException exception) {
      LOGGER.warn("Failed to open original file {}.", path, exception);
      throw new OriginalFileOpenException("The original file could not be opened locally.");
    }
  }

  @Override
  public void reveal(Path path) {
    PlatformType platformType = platformService.getPlatformType();

    if (platformType == PlatformType.MACOS) {
      start(List.of("/usr/bin/open", "-R", path.toString()), path, "reveal in Finder");
      return;
    }

    if (platformType == PlatformType.WINDOWS) {
      start(List.of("explorer.exe", "/select," + path.toString()), path, "reveal in Explorer");
      return;
    }

    throw new OriginalFileActionException(
        "Opening the file location is unavailable on this platform.");
  }

  @Override
  public void share(Path path) {
    if (supportsNativeShare()) {
      throw new OriginalFileActionException("Sharing is unavailable in this environment.");
    }

    reveal(path);
  }

  @Override
  public boolean supportsNativeShare() {
    return false;
  }

  private void start(List<String> command, Path path, String action) {
    try {
      Process process = processStarter.start(command);
      int exitCode = process.waitFor();

      if (exitCode != 0) {
        throw new OriginalFileActionException("The original file location could not be opened.");
      }
    } catch (IOException exception) {
      LOGGER.warn("Failed to {} for original file {} using {}.", action, path, command, exception);
      throw new OriginalFileActionException(
          "The original file location could not be opened.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      LOGGER.warn("Interrupted while attempting to {} for original file {}.", action, path);
      throw new OriginalFileActionException(
          "The original file location could not be opened.", exception);
    }
  }

  interface ProcessStarter {

    Process start(List<String> command) throws IOException;
  }

  private static final class ProcessBuilderStarter implements ProcessStarter {

    @Override
    public Process start(List<String> command) throws IOException {
      return new ProcessBuilder(command).start();
    }
  }
}
