package be.cnoupoue.snapmemoria.platform;

import java.util.Locale;

public enum PlatformType {
  MACOS,
  WINDOWS,
  LINUX,
  UNKNOWN;

  public static PlatformType detect(String osName) {
    String normalized = osName == null ? "" : osName.toLowerCase(Locale.ROOT);

    if (normalized.contains("mac") || normalized.contains("darwin")) {
      return MACOS;
    }

    if (normalized.contains("win")) {
      return WINDOWS;
    }

    if (normalized.contains("linux")) {
      return LINUX;
    }

    return UNKNOWN;
  }

  public static PlatformType current() {
    return detect(System.getProperty("os.name"));
  }
}
