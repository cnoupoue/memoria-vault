package be.cnoupoue.memoriavault.platform.macos;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import be.cnoupoue.memoriavault.platform.PlatformType;
import be.cnoupoue.memoriavault.platform.common.AbstractPlatformService;

public class MacosPlatformService extends AbstractPlatformService {

  private final MacosRuntimePaths runtimePaths;

  public MacosPlatformService() {
    this(new MacosRuntimePaths());
  }

  MacosPlatformService(MacosRuntimePaths runtimePaths) {
    this.runtimePaths = runtimePaths;
  }

  @Override
  public PlatformType getPlatformType() {
    return PlatformType.MACOS;
  }

  @Override
  public PlatformCapabilities getCapabilities() {
    return new PlatformCapabilities(true, true, true, true);
  }

  @Override
  protected PlatformRuntimePaths detectRuntimePaths() {
    return runtimePaths.detect();
  }

  @Override
  protected String publicOsName() {
    return "macOS";
  }
}
