package be.cnoupoue.memoriavault.browser;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class BrowserAutoOpenConfiguration {

  @Bean
  BrowserLauncher browserLauncher() {
    return new DesktopBrowserLauncher();
  }

  @Bean
  @ConditionalOnProperty(name = "memoriavault.browser.auto-open", havingValue = "true")
  BrowserAutoOpenService browserAutoOpenService(
      @Value("${memoriavault.browser.url:http://127.0.0.1:8080}") URI browserUrl,
      BrowserLauncher browserLauncher) {
    return new BrowserAutoOpenService(true, browserUrl, browserLauncher);
  }

  @Bean
  @ConditionalOnProperty(name = "memoriavault.browser.auto-open", havingValue = "true")
  BrowserAutoOpenReadyListener browserAutoOpenReadyListener(
      BrowserAutoOpenService browserAutoOpenService) {
    return new BrowserAutoOpenReadyListener(browserAutoOpenService);
  }
}
