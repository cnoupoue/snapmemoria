package be.cnoupoue.memoriavault.source;

import static org.assertj.core.api.Assertions.assertThat;

import be.cnoupoue.memoriavault.platform.PlatformCapabilities;
import be.cnoupoue.memoriavault.platform.PlatformDiagnosticInfo;
import be.cnoupoue.memoriavault.platform.PlatformService;
import be.cnoupoue.memoriavault.platform.PlatformType;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class NativeFolderPickerConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(NativeFolderPickerConfiguration.class);

  @Test
  void macosPlatformUsesMacosPicker() {
    contextRunner
        .withBean(PlatformService.class, () -> new FakePlatformService(PlatformType.MACOS, true))
        .run(
            context ->
                assertThat(context.getBean(NativeFolderPicker.class))
                    .isInstanceOf(MacosNativeFolderPicker.class));
  }

  @Test
  void unsupportedPlatformUsesSafeFallbackPicker() {
    contextRunner
        .withBean(PlatformService.class, () -> new FakePlatformService(PlatformType.LINUX, false))
        .run(
            context ->
                assertThat(context.getBean(NativeFolderPicker.class))
                    .isInstanceOf(UnsupportedNativeFolderPicker.class));
  }

  @Test
  void windowsPlatformUsesWindowsPicker() {
    contextRunner
        .withBean(PlatformService.class, () -> new FakePlatformService(PlatformType.WINDOWS, true))
        .run(
            context ->
                assertThat(context.getBean(NativeFolderPicker.class))
                    .isInstanceOf(WindowsNativeFolderPicker.class));
  }

  private record FakePlatformService(PlatformType platformType, boolean nativeFolderPicker)
      implements PlatformService {

    @Override
    public PlatformType getPlatformType() {
      return platformType;
    }

    @Override
    public PlatformCapabilities getCapabilities() {
      return new PlatformCapabilities(false, false, nativeFolderPicker, false);
    }

    @Override
    public Optional<Path> resolveBundledFfmpegPath() {
      return Optional.empty();
    }

    @Override
    public Optional<Path> resolveApplicationBundlePath() {
      return Optional.empty();
    }

    @Override
    public Optional<Path> resolveApplicationLauncherPath() {
      return Optional.empty();
    }

    @Override
    public PlatformDiagnosticInfo getDiagnosticInfo() {
      return new PlatformDiagnosticInfo(platformType.name(), "test", "test");
    }
  }
}
