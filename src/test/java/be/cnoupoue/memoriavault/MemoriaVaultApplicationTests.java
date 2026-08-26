package be.cnoupoue.memoriavault;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.playback.LocalFileOpener;
import be.cnoupoue.memoriavault.playback.PlaybackController;
import be.cnoupoue.memoriavault.settings.ApplicationSettingsController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class MemoriaVaultApplicationTests {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertThat(applicationContext).isNotNull();
  }

  @Test
  void playbackControllerAndDesktopFileActionsAreRegistered() {
    assertThat(applicationContext.getBeansOfType(PlaybackController.class)).hasSize(1);
    assertThat(applicationContext.getBeansOfType(LocalFileOpener.class)).hasSize(1);
  }

  @Test
  void settingsResetControllerIsRegistered() {
    assertThat(applicationContext.getBeansOfType(ApplicationSettingsController.class)).hasSize(1);
  }
}
