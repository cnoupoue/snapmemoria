package be.cnoupoue.memoriavault.platform.windows;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformType;
import org.junit.jupiter.api.Test;

class WindowsPlatformServiceTest {

  @Test
  void detectedAsWindowsPlatform() {
    WindowsPlatformService service = new WindowsPlatformService();
    assertThat(service.getPlatformType()).isEqualTo(PlatformType.WINDOWS);
  }

  @Test
  void enablesNativeFolderPickerCapability() {
    WindowsPlatformService service = new WindowsPlatformService();
    PlatformCapabilities capabilities = service.getCapabilities();

    assertThat(capabilities.nativeFolderPicker()).isTrue();
    assertThat(capabilities.applicationBundleDetection()).isFalse();
    assertThat(capabilities.bundledFfmpeg()).isFalse();
    assertThat(capabilities.desktopBrowserOpen()).isFalse();
  }
}
