package be.cnoupoue.memoriavault.platform;

import be.cnoupoue.memoriavault.platform.common.UnsupportedPlatformService;
import be.cnoupoue.memoriavault.platform.macos.MacosPlatformService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PlatformServiceFactory {

  @Bean
  @ConditionalOnMissingBean(PlatformService.class)
  PlatformService platformService() {
    PlatformType platformType = PlatformType.current();

    if (platformType == PlatformType.MACOS) {
      return new MacosPlatformService();
    }

    return new UnsupportedPlatformService(platformType);
  }
}
