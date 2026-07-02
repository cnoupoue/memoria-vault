package be.cnoupoue.snapmemoria.browser;

import java.net.URI;

public interface BrowserLauncher {

  boolean isBrowseSupported();

  void open(URI uri);
}
