package be.cnoupoue.memoriavault.source;

import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NativeFolderPickerConfiguration {

  @Bean
  @ConditionalOnMissingBean(NativeFolderPicker.class)
  NativeFolderPicker nativeFolderPicker(PlatformService platformService) {
    if (platformService.getPlatformType() == PlatformType.MACOS
        && platformService.getCapabilities().nativeFolderPicker()) {
      return new MacosNativeFolderPicker();
    }

    if (platformService.getPlatformType() == PlatformType.WINDOWS
        && platformService.getCapabilities().nativeFolderPicker()) {
      return new WindowsNativeFolderPicker();
    }

    return new UnsupportedNativeFolderPicker();
  }
}
