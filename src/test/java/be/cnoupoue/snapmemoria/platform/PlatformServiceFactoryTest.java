package be.cnoupoue.snapmemoria.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class PlatformServiceFactoryTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(PlatformServiceFactory.class);

  @Test
  void createsOnePlatformServiceAutomatically() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(PlatformService.class);
          assertThat(context.getBean(PlatformService.class).getPlatformType()).isNotNull();
        });
  }

  @Test
  void userProvidedPlatformServiceCanOverrideFactorySelection() {
    contextRunner
        .withBean(PlatformService.class, FakePlatformService::new)
        .run(
            context -> {
              assertThat(context).hasSingleBean(PlatformService.class);
              assertThat(context.getBean(PlatformService.class).getPlatformType())
                  .isEqualTo(PlatformType.LINUX);
            });
  }

  private static class FakePlatformService
      extends be.cnoupoue.snapmemoria.platform.common.UnsupportedPlatformService {

    FakePlatformService() {
      super(PlatformType.LINUX);
    }
  }
}
