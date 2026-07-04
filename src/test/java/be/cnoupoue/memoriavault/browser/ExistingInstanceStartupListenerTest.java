package be.cnoupoue.memoriavault.browser;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ExistingInstanceStartupListenerTest {

  private static final URI APP_URL = URI.create("http://127.0.0.1:8080");

  @Test
  void opensExistingHealthyMemoriaVaultInstanceAndExitsCleanly() {
    FakeInstanceProbe instanceProbe =
        new FakeInstanceProbe(InstanceProbeResult.HEALTHY_MEMORIA_VAULT);
    BrowserAutoOpenServiceTest.FakeBrowserLauncher browserLauncher =
        new BrowserAutoOpenServiceTest.FakeBrowserLauncher(true);
    RecordingProcessExit processExit = new RecordingProcessExit();
    ExistingInstanceStartupListener listener =
        new ExistingInstanceStartupListener(instanceProbe, browserLauncher, processExit);

    listener.handleEnvironment(productionEnvironment());

    assertThat(instanceProbe.probedUrls()).containsExactly(APP_URL);
    assertThat(browserLauncher.openCount()).isOne();
    assertThat(browserLauncher.lastOpenedUri()).isEqualTo(APP_URL);
    assertThat(processExit.statuses()).containsExactly(0);
  }

  @Test
  void doesNotTreatUnrelatedServiceAsMemoriaVault() {
    FakeInstanceProbe instanceProbe = new FakeInstanceProbe(InstanceProbeResult.UNRELATED_SERVICE);
    BrowserAutoOpenServiceTest.FakeBrowserLauncher browserLauncher =
        new BrowserAutoOpenServiceTest.FakeBrowserLauncher(true);
    RecordingProcessExit processExit = new RecordingProcessExit();
    ExistingInstanceStartupListener listener =
        new ExistingInstanceStartupListener(instanceProbe, browserLauncher, processExit);

    listener.handleEnvironment(productionEnvironment());

    assertThat(instanceProbe.probedUrls()).containsExactly(APP_URL);
    assertThat(browserLauncher.openCount()).isZero();
    assertThat(processExit.statuses()).containsExactly(1);
  }

  @Test
  void doesNothingOutsideProductionProfile() {
    FakeInstanceProbe instanceProbe =
        new FakeInstanceProbe(InstanceProbeResult.HEALTHY_MEMORIA_VAULT);
    BrowserAutoOpenServiceTest.FakeBrowserLauncher browserLauncher =
        new BrowserAutoOpenServiceTest.FakeBrowserLauncher(true);
    RecordingProcessExit processExit = new RecordingProcessExit();
    ExistingInstanceStartupListener listener =
        new ExistingInstanceStartupListener(instanceProbe, browserLauncher, processExit);

    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("memoriavault.browser.auto-open", "true")
            .withProperty("memoriavault.browser.url", APP_URL.toString());
    environment.setActiveProfiles("test");

    listener.handleEnvironment(environment);

    assertThat(instanceProbe.probedUrls()).isEmpty();
    assertThat(browserLauncher.openCount()).isZero();
    assertThat(processExit.statuses()).isEmpty();
  }

  private MockEnvironment productionEnvironment() {
    MockEnvironment environment =
        new MockEnvironment()
            .withProperty("memoriavault.browser.auto-open", "true")
            .withProperty("memoriavault.browser.url", APP_URL.toString());
    environment.setActiveProfiles("production");
    return environment;
  }

  private static class FakeInstanceProbe extends LocalMemoriaVaultInstanceProbe {

    private final InstanceProbeResult result;
    private final List<URI> probedUrls = new ArrayList<>();

    FakeInstanceProbe(InstanceProbeResult result) {
      this.result = result;
    }

    @Override
    public InstanceProbeResult probe(URI appUrl) {
      probedUrls.add(appUrl);
      return result;
    }

    List<URI> probedUrls() {
      return probedUrls;
    }
  }

  private static class RecordingProcessExit implements ExistingInstanceStartupListener.ProcessExit {

    private final List<Integer> statuses = new ArrayList<>();

    @Override
    public void exit(int status) {
      statuses.add(status);
    }

    List<Integer> statuses() {
      return statuses;
    }
  }
}
