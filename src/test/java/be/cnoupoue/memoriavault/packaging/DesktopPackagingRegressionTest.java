package be.cnoupoue.memoriavault.packaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class DesktopPackagingRegressionTest {

  @Test
  void macosPackagingUsesBrowserRuntimeWithoutJavafxDesktopMode() throws Exception {
    String makefile = Files.readString(Path.of("Makefile"));
    String buildProductionTarget = makeTarget(makefile, "build-production");
    String macosPackageTarget = makeTarget(makefile, "package-macos-app");
    String productionProperties =
        Files.readString(Path.of("src/main/resources/application-production.properties"));

    assertThat(buildProductionTarget)
        .contains("./mvnw clean -P$(SPRING_PROFILE) -DskipTests package");
    assertThat(macosPackageTarget)
        .contains("--main-class \"org.springframework.boot.loader.launch.JarLauncher\"");
    assertThat(macosPackageTarget).contains("--arguments \"$(SPRING_ARGS)\"");
    assertThat(macosPackageTarget).contains("-Dmemoriavault.ffmpeg.path=$$APPDIR/");
    assertThat(macosPackageTarget).doesNotContain("memoriavault.desktop");
    assertThat(macosPackageTarget).doesNotContain("windows-desktop");

    assertThat(productionProperties).contains("memoriavault.browser.auto-open=true");
  }

  @Test
  void mavenDefaultBuildDoesNotCompileOrDependOnJavafx() throws Exception {
    String mainApplication =
        Files.readString(
            Path.of("src/main/java/be/cnoupoue/memoriavault/MemoriaVaultApplication.java"));

    assertThat(topLevelDependencies()).doesNotContain("org.openjfx");
    assertThat(mainApplication).doesNotContain("javafx.");
    assertThat(
            Path.of("src/main/java/be/cnoupoue/memoriavault/MemoriaVaultDesktopApplication.java"))
        .doesNotExist();
  }

  @Test
  void windowsDesktopProfileOwnsJavafxDependenciesAndSources() throws Exception {
    String pom = Files.readString(Path.of("pom.xml"));

    assertThat(profile(pom, "windows-desktop"))
        .contains("<family>Windows</family>")
        .contains("<groupId>org.openjfx</groupId>")
        .contains("<artifactId>javafx-controls</artifactId>")
        .contains("<artifactId>javafx-web</artifactId>")
        .contains("<artifactId>javafx-maven-plugin</artifactId>")
        .contains("<source>src/windows/java</source>");
    assertThat(
            Path.of(
                "src/windows/java/be/cnoupoue/memoriavault/MemoriaVaultDesktopApplication.java"))
        .exists();
  }

  @Test
  void windowsPackagingKeepsJavafxDesktopModeAndAvoidsExternalBrowser() throws Exception {
    String workflow = Files.readString(Path.of(".github/workflows/release-windows.yml"));
    String packagingScript =
        Files.readString(Path.of("packaging/windows/scripts/package-windows.ps1"));
    String windowsReadme = Files.readString(Path.of("packaging/windows/README.md"));

    assertThat(workflow).contains("npm --prefix frontend test");
    assertThat(workflow).doesNotContain("--watchAll");
    assertThat(workflow)
        .contains("function Find-WixBin")
        .contains("Get-Command candle.exe")
        .contains("light.exe")
        .contains("${env:ChocolateyInstall}\\lib\\wixtoolset\\tools");
    assertThat(workflow).doesNotContain("WiX Toolset v3.11\\bin");
    assertThat(windowsReadme).contains("WiX Toolset v3.x").doesNotContain("WiX Toolset v3.11");
    assertThat(packagingScript)
        .contains("\"-Pproduction,windows-desktop\"")
        .contains("$jpackageCommandTemplate = @'")
        .contains("'@")
        .contains("$jpackageCommand = $jpackageCommandTemplate -f")
        .doesNotContain("`$APPDIR");
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.desktop=true\"");
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.browser.auto-open=false\"");
    assertThat(workflow)
        .contains("--java-options '-Dmemoriavault.ffmpeg.path=$APPDIR\\ffmpeg\\ffmpeg.exe'");
    assertThat(packagingScript).contains("-Dmemoriavault.desktop=true");
    assertThat(packagingScript).contains("-Dmemoriavault.browser.auto-open=false");
    assertThat(packagingScript).contains("-Dmemoriavault.ffmpeg.path=$APPDIR\\ffmpeg\\ffmpeg.exe");
  }

  @Test
  void releaseWorkflowsSupportPlatformPrefixedAndSharedTags() throws Exception {
    String macosWorkflow = Files.readString(Path.of(".github/workflows/release-macos-arm64.yml"));
    String windowsWorkflow = Files.readString(Path.of(".github/workflows/release-windows.yml"));
    String qualityWorkflow = Files.readString(Path.of(".github/workflows/quality-checks.yml"));

    assertThat(macosWorkflow)
        .contains("- \"v*.*.*\"")
        .contains("- \"mac-v*.*.*\"")
        .contains("release_tag=\"${tag#mac-}\"")
        .contains("release_version=\"${release_tag#v}\"")
        .doesNotContain("- \"win-v*.*.*\"");

    assertThat(windowsWorkflow)
        .contains("- \"v*.*.*\"")
        .contains("- \"win-v*.*.*\"")
        .contains("release_tag=\"${tag#win-}\"")
        .contains("release_version=\"${release_tag#v}\"")
        .doesNotContain("- \"mac-v*.*.*\"");

    assertThat(qualityWorkflow)
        .contains("- \"v*.*.*\"")
        .contains("- \"mac-v*.*.*\"")
        .contains("- \"win-v*.*.*\"");
  }

  private String makeTarget(String makefile, String targetName) {
    Pattern pattern =
        Pattern.compile("(?ms)^" + Pattern.quote(targetName) + ":.*?(?=^[A-Za-z0-9_-]+:|\\z)");
    var matcher = pattern.matcher(makefile);
    assertThat(matcher.find()).as("Make target %s", targetName).isTrue();
    return matcher.group();
  }

  private String topLevelDependencies() throws Exception {
    var documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(false);
    var document = documentBuilderFactory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
    Element project = document.getDocumentElement();

    var dependencies = project.getElementsByTagName("dependencies");
    for (int index = 0; index < dependencies.getLength(); index++) {
      var dependenciesNode = dependencies.item(index);
      if (dependenciesNode.getParentNode().isSameNode(project)) {
        return nodeText(dependenciesNode);
      }
    }

    return "";
  }

  private String profile(String pom, String profileId) {
    Pattern pattern =
        Pattern.compile("(?ms)<profile>\\s*<id>" + Pattern.quote(profileId) + "</id>.*?</profile>");
    var matcher = pattern.matcher(pom);
    assertThat(matcher.find()).as("Maven profile %s", profileId).isTrue();
    return matcher.group();
  }

  private String nodeText(Node node) {
    try {
      var transformer = TransformerFactory.newInstance().newTransformer();
      var writer = new java.io.StringWriter();
      transformer.transform(new DOMSource(node), new StreamResult(writer));
      return writer.toString();
    } catch (TransformerException exception) {
      throw new IllegalStateException("Could not serialize XML node.", exception);
    }
  }
}
