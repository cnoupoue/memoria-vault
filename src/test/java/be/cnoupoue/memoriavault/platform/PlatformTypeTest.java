package be.cnoupoue.memoriavault.platform;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlatformTypeTest {

  @Test
  void detectsMacosNames() {
    assertThat(PlatformType.detect("Mac OS X")).isEqualTo(PlatformType.MACOS);
    assertThat(PlatformType.detect("Darwin")).isEqualTo(PlatformType.MACOS);
  }

  @Test
  void detectsWindowsNames() {
    assertThat(PlatformType.detect("Windows 11")).isEqualTo(PlatformType.WINDOWS);
  }

  @Test
  void detectsLinuxNames() {
    assertThat(PlatformType.detect("Linux")).isEqualTo(PlatformType.LINUX);
  }

  @Test
  void unknownNamesAreSafe() {
    assertThat(PlatformType.detect("Solaris")).isEqualTo(PlatformType.UNKNOWN);
    assertThat(PlatformType.detect(null)).isEqualTo(PlatformType.UNKNOWN);
  }
}
