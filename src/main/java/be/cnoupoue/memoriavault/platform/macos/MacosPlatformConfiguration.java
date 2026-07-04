package be.cnoupoue.memoriavault.platform.macos;

/**
 * Marker package for macOS runtime integration.
 *
 * <p>macOS-specific bundle paths and native integration belong beside {@link MacosPlatformService};
 * generic services should depend on the platform interfaces instead.
 */
public final class MacosPlatformConfiguration {

  private MacosPlatformConfiguration() {}
}
