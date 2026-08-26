package be.cnoupoue.memoriavault.playback;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformServiceFactory;
import be.cnoupoue.memoriavault.platform.PlatformType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DesktopLocalFileOpenerStartupTest {

  @Test
  void applicationStartupCreatesDesktopFileActionBeanOnMacos() {
    new ApplicationContextRunner()
        .withSystemProperties("os.name=Mac OS X")
        .withUserConfiguration(PlatformServiceFactory.class)
        .withBean(DesktopLocalFileOpener.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PlatformService.class);
              assertThat(context).hasSingleBean(LocalFileOpener.class);
              assertThat(context.getBean(PlatformService.class).getPlatformType())
                  .isEqualTo(PlatformType.MACOS);
            });
  }

  @Test
  void applicationStartupCreatesDesktopFileActionBeanOnWindows() {
    new ApplicationContextRunner()
        .withSystemProperties("os.name=Windows 11")
        .withUserConfiguration(PlatformServiceFactory.class)
        .withBean(DesktopLocalFileOpener.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PlatformService.class);
              assertThat(context).hasSingleBean(LocalFileOpener.class);
              assertThat(context.getBean(PlatformService.class).getPlatformType())
                  .isEqualTo(PlatformType.WINDOWS);
            });
  }

  @Test
  void applicationStartupCreatesDesktopFileActionBeanWhenDesktopIntegrationIsUnavailable() {
    new ApplicationContextRunner()
        .withSystemProperties("java.awt.headless=true")
        .withBean(PlatformService.class, () -> new FixedPlatformService(PlatformType.MACOS))
        .withBean(DesktopLocalFileOpener.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(LocalFileOpener.class);
              assertThat(context.getBean(LocalFileOpener.class).supportsNativeShare()).isFalse();
            });
  }

  private static final class FixedPlatformService
      extends be.cnoupoue.memoriavault.platform.common.UnsupportedPlatformService {

    private FixedPlatformService(PlatformType platformType) {
      super(platformType);
    }
  }
}
