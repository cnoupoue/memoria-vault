package be.cnoupoue.memoriavault.browser;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BrowserAutoOpenService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BrowserAutoOpenService.class);

  private final boolean autoOpen;
  private final URI browserUrl;
  private final BrowserLauncher browserLauncher;
  private final AtomicBoolean browserOpened = new AtomicBoolean(false);

  public BrowserAutoOpenService(boolean autoOpen, URI browserUrl, BrowserLauncher browserLauncher) {
    this.autoOpen = autoOpen;
    this.browserUrl = browserUrl;
    this.browserLauncher = browserLauncher;
  }

  public boolean openOnce() {
    if (!autoOpen) {
      return false;
    }

    if (!browserOpened.compareAndSet(false, true)) {
      return false;
    }

    if (!browserLauncher.isBrowseSupported()) {
      LOGGER.warn("Browser auto-open is unavailable in this environment.");
      return false;
    }

    try {
      browserLauncher.open(browserUrl);
      return true;
    } catch (BrowserLaunchException exception) {
      LOGGER.warn("Could not open Memoria Vault in the default browser.");
      return false;
    }
  }
}
