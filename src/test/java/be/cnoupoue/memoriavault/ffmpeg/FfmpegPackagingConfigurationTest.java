package be.cnoupoue.memoriavault.ffmpeg;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FfmpegPackagingConfigurationTest {

  @Test
  void macosJpackagePassesBundledFfmpegPathToRuntime() throws Exception {
    String makefile = Files.readString(Path.of("Makefile"));

    assertThat(makefile)
        .contains(
            "--java-options '-Dmemoriavault.ffmpeg.path=$$APPDIR/$(BUNDLED_FFMPEG_APP_DIR)/ffmpeg'");
    assertThat(makefile)
        .contains("--main-class \"org.springframework.boot.loader.launch.JarLauncher\"");
    assertThat(makefile).contains("./mvnw clean -P$(SPRING_PROFILE) -DskipTests package");
    assertThat(makefile).doesNotContain("memoriavault.desktop");
    assertThat(makefile).contains("@$(MAKE) inspect-bundled-ffmpeg");
    assertThat(makefile).contains("Bundled app FFmpeg did not generate a video preview JPEG.");
  }

  @Test
  void mavenBuildKeepsJavafxInWindowsDesktopProfileOnly() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));
    String mainApplication =
        Files.readString(
            Path.of("src/main/java/be/cnoupoue/memoriavault/MemoriaVaultApplication.java"));

    assertThat(pom).contains("<id>windows-desktop</id>");
    assertThat(pom).contains("org.openjfx");
    assertThat(pom).contains("javafx-maven-plugin");
    assertThat(pom).contains("src/windows/java");
    assertThat(mainApplication).doesNotContain("javafx.");
  }

  @Test
  void windowsJpackagePassesBundledFfmpegPathToRuntime() throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/release-windows.yml"));
    String packagingScript =
        Files.readString(Path.of("packaging/windows/scripts/package-windows.ps1"));

    assertThat(workflow)
        .contains("--java-options '-Dmemoriavault.ffmpeg.path=$APPDIR\\ffmpeg\\ffmpeg.exe'");
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.desktop=true\"");
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.browser.auto-open=false\"");
    assertThat(packagingScript)
        .contains("\"-Pproduction,windows-desktop\"")
        .contains("$jpackageCommandTemplate = @'")
        .contains("$jpackageCommand = $jpackageCommandTemplate -f")
        .doesNotContain("`$APPDIR");
    assertThat(packagingScript).contains("-Dmemoriavault.desktop=true");
    assertThat(packagingScript).contains("-Dmemoriavault.browser.auto-open=false");
    assertThat(packagingScript).contains("-Dmemoriavault.ffmpeg.path=$APPDIR\\ffmpeg\\ffmpeg.exe");
  }
}
