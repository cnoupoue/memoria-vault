package be.cnoupoue.snapmemoria.browser;

import java.net.URI;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;

public class ExistingInstanceStartupListener
    implements ApplicationListener<ApplicationEnvironmentPreparedEvent> {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(ExistingInstanceStartupListener.class);

  private final LocalSnapmemoriaInstanceProbe instanceProbe;
  private final BrowserLauncher browserLauncher;
  private final ProcessExit processExit;

  public ExistingInstanceStartupListener() {
    this(
        new LocalSnapmemoriaInstanceProbe(),
        new DesktopBrowserLauncher(),
        status -> System.exit(status));
  }

  ExistingInstanceStartupListener(
      LocalSnapmemoriaInstanceProbe instanceProbe,
      BrowserLauncher browserLauncher,
      ProcessExit processExit) {
    this.instanceProbe = instanceProbe;
    this.browserLauncher = browserLauncher;
    this.processExit = processExit;
  }

  @Override
  public void onApplicationEvent(ApplicationEnvironmentPreparedEvent event) {
    handleEnvironment(event.getEnvironment());
  }

  void handleEnvironment(ConfigurableEnvironment environment) {
    if (!isProduction(environment) || isTest(environment) || !isAutoOpenEnabled(environment)) {
      return;
    }

    URI browserUrl =
        URI.create(environment.getProperty("snapmemoria.browser.url", "http://127.0.0.1:8080"));

    InstanceProbeResult result = instanceProbe.probe(browserUrl);

    if (result == InstanceProbeResult.HEALTHY_SNAPMEMORIA) {
      LOGGER.info("SnapMemoria is already running. Opening the existing app.");
      openExistingApp(browserUrl);
      processExit.exit(0);
    }

    if (result == InstanceProbeResult.UNRELATED_SERVICE) {
      LOGGER.error(
          "Port 8080 is already in use by another service. Stop that service before starting SnapMemoria.");
      processExit.exit(1);
    }
  }

  private boolean isProduction(ConfigurableEnvironment environment) {
    return Arrays.asList(environment.getActiveProfiles()).contains("production");
  }

  private boolean isTest(ConfigurableEnvironment environment) {
    return Arrays.asList(environment.getActiveProfiles()).contains("test");
  }

  private boolean isAutoOpenEnabled(ConfigurableEnvironment environment) {
    return environment.getProperty("snapmemoria.browser.auto-open", Boolean.class, false);
  }

  private void openExistingApp(URI browserUrl) {
    if (!browserLauncher.isBrowseSupported()) {
      LOGGER.warn("Browser auto-open is unavailable in this environment.");
      return;
    }

    try {
      browserLauncher.open(browserUrl);
    } catch (BrowserLaunchException exception) {
      LOGGER.warn("Could not open SnapMemoria in the default browser.");
    }
  }

  @FunctionalInterface
  interface ProcessExit {

    void exit(int status);
  }
}
