package be.cnoupoue.memoriavault.platform.windows;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformRuntimePaths;
import be.cnoupoue.memoriavault.platform.PlatformType;
import be.cnoupoue.memoriavault.platform.common.AbstractPlatformService;

public class WindowsPlatformService extends AbstractPlatformService {

  @Override
  public PlatformType getPlatformType() {
    return PlatformType.WINDOWS;
  }

  @Override
  public PlatformCapabilities getCapabilities() {
    // Windows supports native folder picker via JFileChooser
    return new PlatformCapabilities(false, false, true, false);
  }

  @Override
  protected PlatformRuntimePaths detectRuntimePaths() {
    return PlatformRuntimePaths.empty();
  }

  @Override
  protected String publicOsName() {
    return "Windows";
  }
}
