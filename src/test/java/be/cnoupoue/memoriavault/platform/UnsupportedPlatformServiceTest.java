package be.cnoupoue.memoriavault.platform;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.common.UnsupportedPlatformService;
import org.junit.jupiter.api.Test;

class UnsupportedPlatformServiceTest {

  @Test
  void unsupportedPlatformDoesNotExposeBundledRuntimePaths() {
    PlatformService service = new UnsupportedPlatformService(PlatformType.UNKNOWN);

    assertThat(service.resolveBundledFfmpegPath()).isEmpty();
    assertThat(service.resolveApplicationBundlePath()).isEmpty();
    assertThat(service.resolveApplicationLauncherPath()).isEmpty();
    assertThat(service.getCapabilities().bundledFfmpeg()).isFalse();
  }
}
