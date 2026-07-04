package be.cnoupoue.memoriavault.playback;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Component;

@Component
public class DesktopLocalFileOpener implements LocalFileOpener {

  @Override
  public void open(Path path) {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
      throw new OriginalFileOpenException(
          "Opening local files is unavailable in this environment.");
    }

    try {
      Desktop.getDesktop().open(path.toFile());
    } catch (IOException exception) {
      throw new OriginalFileOpenException("The original file could not be opened locally.");
    }
  }
}
