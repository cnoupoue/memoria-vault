package be.cnoupoue.snapmemoria.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BrowserAutoOpenServiceTest {

  private static final URI APP_URL = URI.create("http://127.0.0.1:8080");

  @Test
  void browserLaunchIsDisabledWhenAutoOpenIsFalse() {
    FakeBrowserLauncher browserLauncher = new FakeBrowserLauncher(true);
    BrowserAutoOpenService service = new BrowserAutoOpenService(false, APP_URL, browserLauncher);

    boolean opened = service.openOnce();

    assertThat(opened).isFalse();
    assertThat(browserLauncher.openCount()).isZero();
  }

  @Test
  void browserLaunchIsSkippedWhenBrowseIsUnsupported() {
    FakeBrowserLauncher browserLauncher = new FakeBrowserLauncher(false);
    BrowserAutoOpenService service = new BrowserAutoOpenService(true, APP_URL, browserLauncher);

    boolean opened = service.openOnce();

    assertThat(opened).isFalse();
    assertThat(browserLauncher.openCount()).isZero();
  }

  @Test
  void browserLaunchIsAttemptedOnlyOnceWhenEnabledAndSupported() {
    FakeBrowserLauncher browserLauncher = new FakeBrowserLauncher(true);
    BrowserAutoOpenService service = new BrowserAutoOpenService(true, APP_URL, browserLauncher);

    boolean firstOpen = service.openOnce();
    boolean secondOpen = service.openOnce();

    assertThat(firstOpen).isTrue();
    assertThat(secondOpen).isFalse();
    assertThat(browserLauncher.openCount()).isOne();
    assertThat(browserLauncher.lastOpenedUri()).isEqualTo(APP_URL);
  }

  static class FakeBrowserLauncher implements BrowserLauncher {

    private final boolean browseSupported;
    private int openCount;
    private URI lastOpenedUri;

    FakeBrowserLauncher(boolean browseSupported) {
      this.browseSupported = browseSupported;
    }

    @Override
    public boolean isBrowseSupported() {
      return browseSupported;
    }

    @Override
    public void open(URI uri) {
      openCount++;
      lastOpenedUri = uri;
    }

    int openCount() {
      return openCount;
    }

    URI lastOpenedUri() {
      return lastOpenedUri;
    }
  }
}
