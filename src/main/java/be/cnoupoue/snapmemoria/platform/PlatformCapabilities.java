package be.cnoupoue.snapmemoria.platform;

public record PlatformCapabilities(
    boolean applicationBundleDetection,
    boolean bundledFfmpeg,
    boolean nativeFolderPicker,
    boolean desktopBrowserOpen) {

  public static PlatformCapabilities unsupported() {
    return new PlatformCapabilities(false, false, false, false);
  }
}
