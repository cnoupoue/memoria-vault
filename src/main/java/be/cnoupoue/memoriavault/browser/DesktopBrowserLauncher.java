package be.cnoupoue.memoriavault.browser;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.net.URI;

public class DesktopBrowserLauncher implements BrowserLauncher {

  @Override
  public boolean isBrowseSupported() {
    return !GraphicsEnvironment.isHeadless()
        && Desktop.isDesktopSupported()
        && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE);
  }

  @Override
  public void open(URI uri) {
    try {
      Desktop.getDesktop().browse(uri);
    } catch (IOException | UnsupportedOperationException | SecurityException exception) {
      throw new BrowserLaunchException("Could not open browser.", exception);
    }
  }
}
