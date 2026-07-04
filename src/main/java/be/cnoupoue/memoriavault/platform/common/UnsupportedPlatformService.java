package be.cnoupoue.memoriavault.platform.common;

import be.cnoupoue.memoriavault.platform.PlatformType;

public class UnsupportedPlatformService extends AbstractPlatformService {

  private final PlatformType platformType;

  public UnsupportedPlatformService(PlatformType platformType) {
    this.platformType = platformType;
  }

  UnsupportedPlatformService(PlatformType platformType, String architecture) {
    super(architecture);
    this.platformType = platformType;
  }

  @Override
  public PlatformType getPlatformType() {
    return platformType;
  }

  @Override
  protected String publicOsName() {
    return switch (platformType) {
      case WINDOWS -> "Windows";
      case LINUX -> "Linux";
      default -> "Unknown";
    };
  }
}
