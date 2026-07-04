package be.cnoupoue.memoriavault.browser;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;

public class BrowserAutoOpenReadyListener implements ApplicationListener<ApplicationReadyEvent> {

  private final BrowserAutoOpenService browserAutoOpenService;

  public BrowserAutoOpenReadyListener(BrowserAutoOpenService browserAutoOpenService) {
    this.browserAutoOpenService = browserAutoOpenService;
  }

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    browserAutoOpenService.openOnce();
  }
}
