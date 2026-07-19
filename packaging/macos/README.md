# macOS Release

## Purpose

This document explains how Memoria Vault is packaged, signed, notarized, stapled, verified, and
distributed on macOS.

macOS packaging is currently implemented for Apple Silicon (`arm64`).

## Required Apple Assets

Signed releases require:

- Developer ID Application certificate
- Matching private key exported in a `.p12`
- App-specific Apple password for notarization
- Apple Developer Team ID
- GitHub Actions secrets for certificate import, signing identity, and notarization credentials

Current macOS-specific assets:

- `icon/MemoriaVault.icns`
- `scripts/create-icns.mjs`
- `ffmpeg/arm64/ffmpeg`

The generated jpackage app image is expected to contain:

- `Memoria Vault.app/Contents/MacOS/Memoria Vault`
- `Memoria Vault.app/Contents/app/<application jar>`
- `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg`
- `Memoria Vault.app/Contents/runtime`

## Unsigned Development Packaging

`make package-macos` is for local development only. It builds the production JAR, creates an
unsigned app image, and creates an unsigned DMG. That DMG is useful for smoke testing packaging, but
it is not a release artifact and is not uploaded by CI.

Release DMG generation must happen after app signing. Do not rebuild or overwrite
`dist/app/Memoria Vault.app` after `make sign-macos-app`.

## Signed And Notarized Release Packaging

The release pipeline is intentionally split into deterministic stages:

```text
1. Build production JAR
2. Build unsigned macOS app image
3. Sign native SQLite libraries embedded inside `sqlite-jdbc-*.jar`
4. Discover embedded Mach-O binaries
5. Sign nested executable code from inside out
6. Sign bundled FFmpeg explicitly
7. Sign Java runtime native executables/libraries where needed
8. Sign the final .app bundle
9. Verify Developer ID authority, Team ID, timestamp, Hardened Runtime, and non-ad-hoc metadata
10. Create DMG from the already signed .app
11. Sign the DMG
12. Mount the DMG and verify the app inside with the same strict metadata checks
13. Submit DMG to Apple notarization
14. Wait for final notarization status
15. Retrieve Apple log automatically when notarization fails
16. Staple the notarization ticket to the DMG
17. Validate stapling and Gatekeeper assessment
18. Generate SHA-256 checksum
19. Publish the notarized DMG and checksum to GitHub Release
```

High-level flow:

```text
build -> package app -> sign nested code -> create DMG -> notarize -> staple -> publish
```

The Makefile targets are:

```text
package-macos-app
postprocess-macos-sqlite-native-libs
sign-macos-app
verify-macos-signatures
package-macos-dmg-from-signed-app
sign-macos-dmg
verify-macos-dmg-signatures
notarize-macos-dmg
staple-macos-dmg
verify-macos-notarization
package-macos-release
```

`package-macos-dmg-from-signed-app` verifies the existing app signature before creating the DMG and
does not invoke `package-macos-app`. Verification inspects `codesign -dv --verbose=4` metadata, not
only `codesign --verify`, and fails on missing Developer ID authority, Team ID mismatch, missing
secure timestamp, missing Hardened Runtime, or ad-hoc signatures.

Do not use `jpackage --type dmg` for the signed release DMG. That path can rebuild or repackage the
app after signing. The release flow uses `packaging/macos/scripts/create-dmg.sh` so the signed app is
copied into the DMG without being rebuilt.

Apple notarization scans native libraries embedded inside nested dependency JARs. The release path
therefore signs `org/sqlite/native/Mac/*/libsqlitejdbc.dylib` inside the packaged
`sqlite-jdbc-*.jar`, rebuilds the dependency JAR, replaces it inside the packaged application JAR,
and verifies the modified archive before signing the outer `.app`.

Required GitHub secrets:

```text
APPLE_DEVELOPER_ID_APPLICATION
APPLE_CERTIFICATE_P12_BASE64
APPLE_CERTIFICATE_PASSWORD
APPLE_ID
APPLE_TEAM_ID
APPLE_APP_SPECIFIC_PASSWORD
```

The signing identity is read from `APPLE_DEVELOPER_ID_APPLICATION`. It is not hardcoded in scripts or
workflow files.

The `APPLE_CERTIFICATE_P12_BASE64` secret must be a base64-encoded `.p12` export that contains both:

```text
Developer ID Application certificate
+
matching private key
```

Exporting only the `.cer` certificate is insufficient for GitHub Actions signing because `codesign`
must have access to the private key. Before exporting the `.p12`, verify the local Mac can see the
Developer ID signing identity:

```bash
security find-identity -v -p codesigning
```

The expected Developer ID identity should be listed. If it is not listed locally, fix the certificate
and private key in Keychain Access before creating the `.p12` secret.

## Entitlements

The signed app uses Hardened Runtime plus the minimum JVM startup entitlements in
`entitlements/memoria-vault.entitlements.plist`:

- `com.apple.security.cs.allow-jit`
- `com.apple.security.cs.allow-unsigned-executable-memory`

These entitlements are needed by the bundled JVM at startup. They are applied to the outer app
bundle, the jpackage launcher, the bundled `java` executable, and `libjvm.dylib`. The stricter
verification scripts fail the release if those JVM entitlements are missing.

## Manual Checks

Use these commands when validating a local signed release candidate:

```bash
codesign --verify --deep --strict --verbose=4 "dist/app/Memoria Vault.app"
spctl --assess --type execute --verbose=4 "dist/app/Memoria Vault.app"
xcrun stapler validate "dist/installers/Memoria-Vault-<version>-macos-arm64.dmg"
spctl --assess --type open --context context:primary-signature --verbose=4 \
  "dist/installers/Memoria-Vault-<version>-macos-arm64.dmg"
```

## Local Signing Test

Run this before pushing a release tag when the Developer ID Application certificate is available
locally:

```bash
make clean-packaging
make package-macos-app
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
APPLE_TEAM_ID="TEAMID" \
  make postprocess-macos-sqlite-native-libs
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make sign-macos-app
make verify-macos-signatures
make package-macos-dmg-from-signed-app
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make sign-macos-dmg
```

After local signing, run a launch smoke test before notarizing or publishing:

```bash
open "dist/app/Memoria Vault.app"
sleep 8
pgrep -fl "Memoria Vault|java" || true
```

If the app exits immediately, inspect the latest macOS crash report for `Memoria Vault` before
creating a release DMG.

Local notarization is optional. Supply credentials only through environment variables or a
Keychain-backed notarytool profile:

```bash
APPLE_ID="account@example.invalid" \
APPLE_TEAM_ID="TEAMID" \
APPLE_APP_SPECIFIC_PASSWORD="xxx" \
make notarize-macos-dmg
make staple-macos-dmg
make verify-macos-notarization
```

Equivalent local command shape:

```bash
xcrun notarytool submit "$DMG" \
  --apple-id "$APPLE_ID" \
  --team-id "$APPLE_TEAM_ID" \
  --password "xxx" \
  --wait
```

## Release Tags

The GitHub Actions release workflow runs when a semantic version tag is pushed:

```bash
git tag -a v0.1.0 -m "Memoria Vault v0.1.0"
git push origin v0.1.0
```

Run `make verify` and local packaging checks before pushing a release tag.

## Signing Readiness

Run the non-blocking inspection after building the app image:

```bash
make package-macos-app
make inspect-macos-signing-readiness
```

Inspection mode:

- requires `dist/app/Memoria Vault.app`;
- discovers Mach-O executables, `.dylib` files, framework binaries, Java runtime binaries, and other
  native binaries inside the bundle;
- labels the jpackage launcher, bundled FFmpeg, Java runtime files, native libraries, frameworks, and
  other Mach-O executables;
- reports whether each file currently has a valid code signature;
- validates `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg` explicitly;
- warns when unsigned binaries or ad-hoc signatures are present, but exits successfully for normal
  development builds unless a packaging problem such as missing FFmpeg is found.

Run strict verification only for signed release candidates:

```bash
make verify-macos-signatures
```

Strict mode:

- fails if any detected Mach-O executable or native library is unsigned;
- fails if any signature is invalid;
- fails if any nested binary still uses an ad-hoc signature instead of a Developer ID signature;
- requires `Authority` to contain `APPLE_DEVELOPER_ID_APPLICATION`;
- requires `TeamIdentifier` to match `APPLE_TEAM_ID`, or the Team ID parsed from the signing identity;
- requires a secure timestamp;
- requires the Hardened Runtime `runtime` flag for Mach-O code;
- requires JVM startup entitlements on the app bundle, launcher, bundled `java`, and `libjvm.dylib`;
- verifies bundled FFmpeg with `codesign --verify --strict`;
- verifies native SQLite libraries embedded inside the packaged `sqlite-jdbc-*.jar`;
- rejects dynamic dependencies that point to Homebrew, user-local, temporary, or mounted-volume
  paths;
- verifies the final app bundle with `codesign --verify --deep --strict --verbose=4`.

`verify-macos-signatures` is expected to fail until every nested binary and the final app bundle are
signed with a Developer ID Application certificate, secure timestamp, and Hardened Runtime metadata.
Its normal CI summary does not print absolute local paths.

Optional identity check:

```bash
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make inspect-macos-signing-readiness
```

The inspection checks Team Identifier or signing authority metadata where practical. It does not
require the variable for unsigned local development and does not print certificate passwords, private
keys, or keychain contents.

## FFmpeg validation

Bundled FFmpeg must be checked separately because it is an executable nested inside the jpackage
bundle and notarization requires nested executables to be signed before the final `.app` bundle.

The signing-readiness script verifies that:

- `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg` exists;
- the file is executable;
- `file` and `lipo -info` report macOS `arm64`;
- `ffmpeg -version` succeeds;
- strict mode passes `codesign --verify --strict`;
- `otool -L` does not report Homebrew, user-local, temporary, or mounted-volume dependencies;
- FFmpeg links only to expected Apple system libraries/frameworks.

If FFmpeg is unsigned in development mode, the inspection prints:

```text
WARNING: Bundled FFmpeg is present and executable but is not yet signed.
```

## Notarization failure diagnostics

When Apple notarization is not accepted, `packaging/macos/scripts/notarize-dmg.sh` saves diagnostic
files under `dist/notarization/`:

- `submission-id.txt`
- `status.txt`
- `notarytool-submit.json`
- `apple-notarization-log.json`, when Apple returns one
- `signature-verification-summary.txt`, in GitHub Actions after signature verification

The workflow uploads `dist/notarization/` only when the job fails. The release asset upload step is
after notarization, stapling, final validation, and checksum generation, so no DMG is published when
notarization fails.

Future macOS Intel support should add a verified `x64` FFmpeg binary, update packaging checks,
and add a separate release workflow or matrix entry.
