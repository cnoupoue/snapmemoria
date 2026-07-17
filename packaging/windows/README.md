# Windows packaging (skeleton)

This folder contains a minimal skeleton to help with Windows packaging.

Included files:
- `scripts/package-windows.ps1` — example PowerShell script to prepare jpackage input, build the JAR and print a sample jpackage command.
- `scripts/sign-sqlite-native-libs.ps1` — helper script to extract and repack the embedded `sqlite-jdbc` JAR. This script does not perform code-signing by default.
- `ffmpeg/` — recommended location to place verified `ffmpeg.exe` binaries per architecture.
- `icon/` — recommended location for the `.ico` used by jpackage.

Recommended steps to produce a Windows installer:
1. Add an `.ico` file at `packaging/windows/icon/MemoriaVault.ico`.
2. Place a verified FFmpeg binary at `packaging/windows/ffmpeg/win-x64/ffmpeg.exe` (or `win-arm64`).
3. Run `pwsh packaging/windows/scripts/package-windows.ps1` from the repository root to build the JAR and prepare the jpackage input directory.
4. Adapt and run the `jpackage` command printed by the script to produce an `exe` or `msi`. Install the WiX Toolset if you want to produce an MSI via jpackage.
5. Optionally run `pwsh packaging/windows/scripts/sign-sqlite-native-libs.ps1 -AppPath <path_to_app_image_or_jar>` to extract and repack native libs. By default this helper does not perform Windows code signing for this project.

Notes on signing and CI:
- For this repository, Windows code-signing is optional and not required to run the packaged application. macOS signing is handled separately in the mac packaging flow.
- If you choose to sign installers or binaries in CI, configure a `windows-latest` runner (GitHub Actions) with JDK 21 and the Windows SDK (for `signtool`). Store certificates as secrets and restore them only during the signing step.

General notes:
- The provided scripts are skeletons to get you started — adapt them to your packaging conventions and paths.
- Update `THIRD_PARTY_NOTICES.md` if you distribute FFmpeg or other third-party binary dependencies.
