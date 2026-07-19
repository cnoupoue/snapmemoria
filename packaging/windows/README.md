
# Windows Native Packaging — Memoria Vault

This directory contains the scripts, icons, and configurations required to compile, validate, and package **Memoria Vault** into a native Windows desktop application (`.exe`) embedding a native JavaFX graphical user interface (`WebView`).

---

## 📁 Folder Structure

*   `scripts/package-windows.ps1`: The main staging script. It safely extracts project metadata from Maven, downloads and validates the pinned FFmpeg binary via SHA-256, compiles the distribution code, and builds the staging input for `jpackage`.
*   `scripts/sign-sqlite-native-libs.ps1`: An optional helper utility to unpack, sign, and repack native SQLite binaries if needed.
*   `icon/MemoriaVault.ico`: The official application icon used for the final installer executable and Start Menu shortcuts.
*   `ffmpeg/win-x64/`: The isolated directory where the verified and structurally embedded `ffmpeg.exe` binary is cached.

---

## 🛠️ Local Prerequisites

Before executing the packaging lifecycle on your local Windows workstation, ensure the following tools are installed and configured:

1.  **Java 21**: A JDK configuration containing JavaFX modules (e.g., Eclipse Temurin or Azul Zulu).
2.  **Node.js 22** & **npm**: Required to build the web production assets for the user interface.
3.  **WiX Toolset v3.11**: Required by `jpackage` to generate executable Windows Installers (`.exe`/`.msi`).
    *   *Quick Installation via Chocolatey:* `choco install wixtoolset -y`

---

## 🚀 Step-by-Step Packaging Guide

### Step 1: Compile the Frontend Assets
Generate the production-grade static web interface files so Spring Boot can embed them cleanly inside the application classpath:
```powershell
cd frontend
npm install
npm run build
cd ..

```

### Step 2: Execute the Packaging Script

Run the staging script from the repository root. This script enforces the following security and validation standards:

* Validates that the requested target architecture matches `x64`.
* Queries Maven directly for project version strings and artifact names (removing error-prone text parsing).
* Downloads the pinned production release of **FFmpeg 7.1** and validates its cryptographic integrity with a strict **SHA-256 hash check**.
* Packages the standalone code using `mvn clean package`.

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File packaging/windows/scripts/package-windows.ps1 -Version "0.1.2"

```

### Step 3: Generate the Installer via `jpackage`

Once the staging input directory is ready, run the following native `jpackage` command sequence to generate your self-contained installer:

```powershell
jpackage --type exe `
         --dest "dist/installers" `
         --name "Memoria Vault" `
         --app-version "0.1.2" `
         --vendor "cnoupoue" `
         --input "dist/jpackage-input" `
         --main-jar "memoria-vault-0.1.2.jar" `
         --main-class "org.springframework.boot.loader.launch.JarLauncher" `
         --icon "packaging/windows/icon/MemoriaVault.ico" `
         --win-shortcut `
         --win-menu `
         --jlink-options "--strip-debug --no-man-pages --no-header-files --compress zip-6" `
         --verbose

```

Your fully functional installer will be available at `dist/installers/Memoria Vault-0.1.2.exe`.

---

## 🔄 Continuous Integration (CI) Pipeline

The `.github/workflows/release-windows.yml` workflow automates this entire lifecycle on every pushed tag matching the `v*.*.*` semantic pattern.

### CI Security Guards & Standards:

1. **Pre-packaging Validation:** Complete frontend (`npm test`) and backend (`./mvnw test`) test execution suites run and must pass before the build begins.
2. **Runtime Path Isolation:** No absolute paths from the build environment are baked into the code. The runtime application resolves its bundled `ffmpeg.exe` relatively from its own installation subdirectory.
3. **Windows SmartScreen Handling:** Because the installer is unsigned, automated release descriptions explicitly provide user guidance (*"More Info"* -> *"Run Anyway"*) to walk testers safely past the initial SmartScreen dialog.
4. **Standardized Integrity Checksums:** SHA-256 files are formatted strictly relative to the release asset names, allowing end-users to run automated verification checks easily:
```bash
sha256sum -c Memoria-Vault-0.1.2-windows-x64.exe.sha256
```




