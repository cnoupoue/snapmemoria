package be.cnoupoue.memoriavault.platform.windows;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import be.cnoupoue.memoriavault.platform.PlatformType;
import be.cnoupoue.memoriavault.platform.common.AbstractPlatformService;

public class WindowsPlatformService extends AbstractPlatformService {

  private final WindowsRuntimePaths runtimePaths;

  public WindowsPlatformService() {
    this(new WindowsRuntimePaths());
  }

  WindowsPlatformService(WindowsRuntimePaths runtimePaths) {
    this.runtimePaths = runtimePaths;
  }

  @Override
  public PlatformType getPlatformType() {
    return PlatformType.WINDOWS;
  }

  @Override
  public PlatformCapabilities getCapabilities() {
    return new PlatformCapabilities(true, true, true, false);
  }

  @Override
  protected PlatformRuntimePaths detectRuntimePaths() {
    return runtimePaths.detect();
  }

  @Override
  protected String publicOsName() {
    return "Windows";
  }
}
