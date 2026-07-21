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
    String macosIconScript = Files.readString(Path.of("packaging/macos/scripts/create-icns.mjs"));
    String buildProductionTarget = makeTarget(makefile, "build-production");
    String macosIconTarget = makeTarget(makefile, "generate-macos-icon");
    String macosPackageTarget = makeTarget(makefile, "package-macos-app");
    String mainApplication =
        Files.readString(
            Path.of("src/main/java/be/cnoupoue/memoriavault/MemoriaVaultApplication.java"));
    String productionProperties =
        Files.readString(Path.of("src/main/resources/application-production.properties"));

    assertThat(buildProductionTarget)
        .contains("./mvnw clean -P$(SPRING_PROFILE) -DskipTests package");
    assertThat(makefile)
        .contains("APP_ICON_SOURCE ?= src/main/resources/icon.png")
        .contains("MACOS_ICON ?= $(GENERATED_ICON_DIR)/MemoriaVault.icns")
        .doesNotContain("MACOS_ICON_SOURCE")
        .doesNotContain("frontend/public/favicon.png")
        .doesNotContain("packaging/macos/icon/MemoriaVault.icns");
    assertThat(macosIconTarget)
        .contains("$(APP_ICON_SOURCE)")
        .contains("node \"$(MACOS_PACKAGING_DIR)/scripts/create-icns.mjs\"");
    assertThat(macosIconScript)
        .contains("must be an 8-bit RGBA PNG")
        .contains("must have fully transparent corner pixels");
    assertThat(Path.of("packaging/macos/icon/MemoriaVault.icns")).doesNotExist();
    assertThat(macosPackageTarget)
        .contains("--main-class \"org.springframework.boot.loader.launch.JarLauncher\"");
    assertThat(macosPackageTarget).contains("--arguments \"$(SPRING_ARGS)\"");
    assertThat(macosPackageTarget).contains("--java-options '-Djava.awt.headless=false'");
    assertThat(macosPackageTarget).contains("-Dmemoriavault.ffmpeg.path=$$APPDIR/");
    assertThat(macosPackageTarget).doesNotContain("memoriavault.desktop");
    assertThat(macosPackageTarget).doesNotContain("windows-desktop");

    assertThat(mainApplication).contains("startBackend(args, false, true);");
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
        .contains("mvnw_with_retry()")
        .contains("mvnw_with_retry test")
        .contains(
            "maven_version=\"$(mvnw_with_retry -q -DforceStdout help:evaluate -Dexpression=project.version)\"");
    assertThat(workflow)
        .contains("function Find-WixBin")
        .contains("Get-Command candle.exe")
        .contains("light.exe")
        .contains("${env:ChocolateyInstall}\\lib\\wixtoolset\\tools");
    assertThat(workflow).doesNotContain("WiX Toolset v3.11\\bin");
    assertThat(windowsReadme).contains("WiX Toolset v3.x").doesNotContain("WiX Toolset v3.11");
    assertThat(packagingScript)
        .contains("$ErrorActionPreference = \"Stop\"")
        .contains("function Invoke-MavenWithRetry")
        .contains("function Find-InstalledFfmpeg")
        .contains("function Install-FfmpegWithChocolatey")
        .contains("function Stage-FfmpegFromInstalledLocation")
        .contains("function New-WindowsIconFromPng")
        .contains("$appIconSource = Join-Path $root 'src\\main\\resources\\icon.png'")
        .contains("$windowsIcon = Join-Path $generatedIconDir 'MemoriaVault.ico'")
        .contains("New-WindowsIconFromPng -SourcePng $appIconSource -DestinationIco $windowsIcon")
        .contains("$chocoOutput = & choco.exe install ffmpeg --no-progress -y 2>&1")
        .contains("$chocoExitCode = $LASTEXITCODE")
        .contains("[string]::IsNullOrWhiteSpace($installedFfmpeg)")
        .contains("Resolving Windows FFmpeg through Chocolatey.")
        .contains(
            "Invoke-MavenWithRetry -MavenArguments @(\"-q\", \"-DforceStdout\", \"help:evaluate\", \"-Dexpression=project.version\")")
        .contains(
            "Invoke-MavenWithRetry -MavenArguments @(\"clean\", \"package\", \"-Pproduction,windows-desktop\", \"-DskipTests\")")
        .contains("\"-Pproduction,windows-desktop\"")
        .contains("The release workflow now executes jpackage directly after this staging script.")
        .doesNotContain("`$APPDIR")
        .doesNotContain("$jpackageCommandTemplate")
        .doesNotContain("jpackage --type exe")
        .doesNotContain("gyan.dev")
        .doesNotContain("Invoke-WebRequest")
        .doesNotContain("Join-String");
    assertThat(workflow)
        .contains("Test-Path \"dist/generated-icons/MemoriaVault.ico\"")
        .contains("--icon \"dist/generated-icons/MemoriaVault.ico\"")
        .doesNotContain("packaging/windows/icon/MemoriaVault.ico");
    assertThat(windowsReadme)
        .contains(
            "Generates `dist/generated-icons/MemoriaVault.ico` from `src/main/resources/icon.png`")
        .doesNotContain("icon/MemoriaVault.ico");
    assertThat(Path.of("packaging/windows/icon/MemoriaVault.ico")).doesNotExist();
    assertThat(
            Pattern.compile(
                    "(?m)^\\s*choco(?:\\.exe)?\\s+install\\s+ffmpeg\\s+--no-progress\\s+-y\\s*$")
                .matcher(packagingScript)
                .find())
        .as("Chocolatey output must be captured before writing to host")
        .isFalse();
    assertThat(Pattern.compile("[^\\x00-\\x7F]").matcher(packagingScript).find())
        .as("Windows PowerShell 5.1 packaging script must stay ASCII-only")
        .isFalse();
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.desktop=true\"");
    assertThat(workflow).contains("--java-options \"-Djava.awt.headless=false\"");
    assertThat(workflow).contains("--java-options \"-Dmemoriavault.browser.auto-open=false\"");
    assertThat(workflow)
        .contains("--java-options '-Dmemoriavault.ffmpeg.path=$APPDIR\\ffmpeg\\ffmpeg.exe'");
    assertThat(packagingScript).contains("-Dmemoriavault.desktop=true");
    assertThat(packagingScript).contains("-Djava.awt.headless=false");
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
