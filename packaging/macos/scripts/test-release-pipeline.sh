#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-release-tests.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT
ORIGINAL_PATH="$PATH"

STUB_DIR="$TMP_DIR/bin"
mkdir -p "$STUB_DIR"
FAIL_ZIP_DIR="$TMP_DIR/fail-zip-bin"
mkdir -p "$FAIL_ZIP_DIR"

write_stub() {
  path="$1"
  shift
  printf '%s\n' "$@" >"$STUB_DIR/$path"
  chmod +x "$STUB_DIR/$path"
}

write_stub uname '#!/usr/bin/env bash' \
  'if [ "${1:-}" = "-m" ]; then echo arm64; else echo Darwin; fi'
write_stub file '#!/usr/bin/env bash' \
  'path="${*: -1}"' \
  'case "$path" in' \
  '  *"/Contents/MacOS/Memoria Vault"|*"/Contents/app/ffmpeg/ffmpeg"|*"/Contents/runtime/Contents/Home/bin/java"|*".dylib") echo "$path: Mach-O 64-bit executable arm64" ;;' \
  '  *) echo "$path: ASCII text" ;;' \
  'esac'
write_stub otool '#!/usr/bin/env bash' \
  'target="${*: -1}"' \
  'echo "$target:"' \
  'echo "	/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1.0.0)"' \
  'if [ "${STUB_UNSAFE_DEPS:-0}" = "1" ]; then echo "	/opt/homebrew/Cellar/example/1.0/lib/libexample.dylib (compatibility version 1.0.0, current version 1.0.0)"; fi'
write_stub codesign '#!/usr/bin/env bash' \
  'target="${*: -1}"' \
  'has_unsigned_marker() {' \
  '  probe="$target"' \
  '  while [ "$probe" != "/" ] && [ -n "$probe" ]; do' \
  '    if [ -f "$probe/.unsigned-replacement" ]; then return 0; fi' \
  '    probe="$(dirname "$probe")"' \
  '  done' \
  '  return 1' \
  '}' \
  'if printf "%s\n" "$*" | grep -q -- "--sign"; then' \
  '  if [ -n "${STUB_CODESIGN_ARGS_LOG:-}" ]; then printf "%s\n" "$*" >>"${STUB_CODESIGN_ARGS_LOG}"; fi' \
  '  if [ "${STUB_CODESIGN_MISSING_IDENTITY:-0}" = "1" ]; then echo "error: The specified item could not be found in the keychain." >&2; exit 1; fi' \
  '  if [ -f "$target" ]; then printf "\nsigned\n" >>"$target"; fi' \
  '  echo "$target" >>"${STUB_CODESIGN_SIGNED_LOG:?}"; exit 0' \
  'fi' \
  'if printf "%s\n" "$*" | grep -q -- "--entitlements"; then' \
  '  if [ "${STUB_MISSING_ENTITLEMENTS:-0}" = "1" ]; then printf "%s\n" "<?xml version=\"1.0\"?><plist version=\"1.0\"><dict></dict></plist>"; exit 0; fi' \
  '  printf "%s\n" "<?xml version=\"1.0\"?>"' \
  '  printf "%s\n" "<plist version=\"1.0\"><dict>"' \
  '  printf "%s\n" "<key>com.apple.security.cs.allow-jit</key><true/>"' \
  '  printf "%s\n" "<key>com.apple.security.cs.allow-unsigned-executable-memory</key><true/>"' \
  '  printf "%s\n" "</dict></plist>"' \
  '  exit 0' \
  'fi' \
  'if [ "${1:-}" = "-dv" ]; then' \
  '  if has_unsigned_marker || [ "${STUB_ADHOC_SIGNATURE:-0}" = "1" ]; then echo "Signature=adhoc" >&2; fi' \
  '  if ! has_unsigned_marker && [ "${STUB_MISSING_AUTHORITY:-0}" != "1" ]; then echo "${STUB_AUTHORITY:-Authority=Developer ID Application: Test (ZK7G72LVAX)}" >&2; fi' \
  '  if has_unsigned_marker; then echo "TeamIdentifier=BADTEAM" >&2; else echo "TeamIdentifier=${STUB_TEAM_ID:-ZK7G72LVAX}" >&2; fi' \
  '  if ! has_unsigned_marker && [ "${STUB_MISSING_TIMESTAMP:-0}" != "1" ]; then echo "Timestamp=Jan 1, 2026 at 00:00:00" >&2; fi' \
  '  echo "Runtime Version=15.0.0" >&2' \
  '  if has_unsigned_marker; then echo "CodeDirectory v=20500 size=1 flags=0x2(adhoc) hashes=1+2 location=embedded" >&2; echo "CDHash=unsigned" >&2; else echo "CodeDirectory v=20500 size=1 flags=${STUB_FLAGS:-0x10000(runtime)} hashes=1+2 location=embedded" >&2; echo "CDHash=signed" >&2; fi' \
  '  exit 0' \
  'fi' \
  'if ! has_unsigned_marker && { [ "${STUB_ALL_SIGNED:-0}" = "1" ] || { [ "${STUB_SQLITE_SIGNED:-0}" = "1" ] && printf "%s\n" "$target" | grep -Fq "/org/sqlite/native/Mac/" && printf "%s\n" "$target" | grep -Fq "/libsqlitejdbc.dylib"; } || printf "%s\n" "${STUB_SIGNED_PATHS:-}" | grep -Fxq "$target"; }; then exit 0; fi' \
  'if [ -n "${STUB_CODESIGN_SIGNED_LOG:-}" ] && [ -f "${STUB_CODESIGN_SIGNED_LOG}" ] && grep -Fxq "$target" "${STUB_CODESIGN_SIGNED_LOG}"; then exit 0; fi' \
  'echo "$target: code object is not signed at all" >&2' \
  'exit 1'
write_stub xcrun '#!/usr/bin/env bash' \
  'tool="${1:-}"; shift || true' \
  'if [ -n "${STUB_XCRUN_LOG:-}" ]; then printf "%s %s\n" "$tool" "${1:-}" >>"${STUB_XCRUN_LOG}"; fi' \
  'case "$tool" in' \
  '  notarytool)' \
  '    action="${1:-}"; shift || true' \
  '    if [ "$action" = "submit" ]; then printf "{\"id\":\"test-submission-id\",\"status\":\"Invalid\"}\n"; exit 1; fi' \
  '    if [ "$action" = "log" ]; then out="${*: -1}"; printf "{\"id\":\"test-submission-id\",\"status\":\"Invalid\",\"issues\":[]}\n" >"$out"; exit 0; fi' \
  '    ;;' \
  '  stapler) exit 0 ;;' \
  'esac' \
  'exit 0'
write_stub plutil '#!/usr/bin/env bash' \
  'key=""' \
  'while [ "$#" -gt 0 ]; do case "$1" in -extract) key="$2"; shift 2 ;; -o) shift 2 ;; *) file="$1"; shift ;; esac; done' \
  'if [ "$key" = "id" ]; then sed -n "s/.*\"id\":\"\\([^\"]*\\)\".*/\\1/p" "$file"; fi' \
  'if [ "$key" = "status" ]; then sed -n "s/.*\"status\":\"\\([^\"]*\\)\".*/\\1/p" "$file"; fi'
write_stub spctl '#!/usr/bin/env bash' 'exit 0'
write_stub ditto '#!/usr/bin/env bash' \
  'if [ -n "${STUB_DITTO_ARGS_LOG:-}" ]; then printf "%s\n" "$*" >"${STUB_DITTO_ARGS_LOG}"; fi' \
  'while [ "$#" -gt 0 ] && printf "%s\n" "$1" | grep -q -- "^--"; do shift; done' \
  'src="$1"; dest="$2"' \
  'if [ -n "${STUB_DITTO_LOG:-}" ]; then printf "%s\n" "$src" >"${STUB_DITTO_LOG}"; fi' \
  'mkdir -p "$(dirname "$dest")"' \
  'cp -R "$src" "$dest"'
write_stub hdiutil '#!/usr/bin/env bash' \
  'action="${1:-}"; shift || true' \
  'case "$action" in' \
  '  create)' \
  '    src=""; out=""' \
  '    while [ "$#" -gt 0 ]; do case "$1" in -srcfolder) src="$2"; shift 2 ;; -volname|-format) shift 2 ;; -ov) shift ;; *) out="$1"; shift ;; esac; done' \
  '    mkdir -p "$(dirname "$out")"' \
  '    rm -f "$out"' \
  '    rm -rf "$out.contents"' \
  '    mkdir -p "$out.contents"' \
  '    : >"$out"' \
  '    if [ -n "$src" ]; then cp -R "$src"/. "$out.contents"/; fi' \
  '    if [ -n "${STUB_DMG_CREATE_LOG:-}" ]; then printf "%s\n" "$src" >"${STUB_DMG_CREATE_LOG}"; fi' \
  '    exit 0' \
  '    ;;' \
  '  attach)' \
  '    mount=""' \
  '    dmg=""' \
  '    while [ "$#" -gt 0 ]; do case "$1" in -mountpoint) mount="$2"; shift 2 ;; -readonly|-nobrowse) shift ;; *) dmg="$1"; shift ;; esac; done' \
  '    mkdir -p "$mount"' \
  '    cp -R "$dmg.contents"/. "$mount"/' \
  '    if [ "${STUB_DMG_REPLACE_UNSIGNED:-0}" = "1" ]; then find "$mount" -maxdepth 1 -type d -name "*.app" -exec sh -c '"'"'touch "$1/.unsigned-replacement"'"'"' _ {} \; ; fi' \
  '    exit 0' \
  '    ;;' \
  '  detach)' \
  '    exit 0' \
  '    ;;' \
  'esac' \
  'exit 1'
write_stub jpackage '#!/usr/bin/env bash' \
  'if printf "%s\n" "$*" | grep -q -- "--type dmg"; then' \
  '  dest=""; name=""; version=""' \
  '  while [ "$#" -gt 0 ]; do case "$1" in --dest) dest="$2"; shift 2 ;; --name) name="$2"; shift 2 ;; --app-version) version="$2"; shift 2 ;; *) shift ;; esac; done' \
  '  mkdir -p "$dest"; : >"$dest/$name-$version.dmg"; exit 0' \
  'fi' \
  'exit 0'

printf '%s\n' '#!/usr/bin/env bash' 'echo "zip synthetic failure" >&2' 'exit 15' >"$FAIL_ZIP_DIR/zip"
chmod +x "$FAIL_ZIP_DIR/zip"

make_fixture() {
  fixture="$1"
  jar_name="${2:-memoria-vault-test.jar}"
  mkdir -p "$fixture/Memoria Vault.app/Contents/MacOS"
  mkdir -p "$fixture/Memoria Vault.app/Contents/app/ffmpeg"
  mkdir -p "$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/bin"
  mkdir -p "$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/lib/server"
  printf 'launcher\n' >"$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault"
  printf 'ffmpeg\n' >"$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
  printf 'java\n' >"$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/bin/java"
  printf 'jvm\n' >"$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/lib/server/libjvm.dylib"
  chmod +x "$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault" "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg" "$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/bin/java"

  sqlite_work="$fixture/sqlite-work"
  app_work="$fixture/app-work"
  mkdir -p "$sqlite_work/org/sqlite/native/Mac/aarch64"
  mkdir -p "$sqlite_work/org/sqlite/native/Mac/x86_64"
  printf 'sqlite arm\n' >"$sqlite_work/org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib"
  printf 'sqlite intel\n' >"$sqlite_work/org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib"
  mkdir -p "$app_work/BOOT-INF/lib" "$app_work/BOOT-INF/classes/static"
  (cd "$sqlite_work" && jar cf "$app_work/BOOT-INF/lib/sqlite-jdbc-test.jar" .)
  printf 'html\n' >"$app_work/BOOT-INF/classes/static/index.html"
  (cd "$app_work" && jar cf "$fixture/Memoria Vault.app/Contents/app/$jar_name" .)
  rm -rf "$sqlite_work" "$app_work"
}

assert_contains() {
  haystack="$1"
  needle="$2"
  case "$haystack" in
    *"$needle"*) return 0 ;;
  esac
  {
    echo "Expected output to contain: $needle" >&2
    echo "$haystack" >&2
    exit 1
  }
}

assert_equals() {
  expected="$1"
  actual="$2"
  message="$3"
  if [ "$expected" != "$actual" ]; then
    echo "$message" >&2
    exit 1
  fi
}

mtime() {
  stat -f %m "$1" 2>/dev/null || stat -c %Y "$1"
}

test -f "$REPO_ROOT/packaging/macos/entitlements/memoria-vault.entitlements.plist" || { echo "Expected macOS JVM entitlements file to exist." >&2; exit 1; }

fixture="$TMP_DIR/app-jar-version-mismatch"
make_fixture "$fixture" "memoria-vault-0.1.1.jar"
found_app_jar="$(. "$SCRIPT_DIR/app-jar.sh"; find_packaged_app_jar "$fixture/Memoria Vault.app")"
case "$found_app_jar" in
  *"/Memoria Vault.app/Contents/app/memoria-vault-0.1.1.jar") ;;
  *) echo "Expected packaged app JAR discovery to use the actual app image contents." >&2; exit 1 ;;
esac

fixture="$TMP_DIR/app-jar-other-cwd"
make_fixture "$fixture" "memoria-vault-release-tag-differs.jar"
(
  cd "$fixture"
  found_app_jar="$(. "$SCRIPT_DIR/app-jar.sh"; find_packaged_app_jar "Memoria Vault.app")"
  case "$found_app_jar" in
    *"/Memoria Vault.app/Contents/app/memoria-vault-release-tag-differs.jar") ;;
    *) echo "Expected packaged app JAR discovery to work from an arbitrary current directory." >&2; exit 1 ;;
  esac
)

fixture="$TMP_DIR/app-jar-missing"
mkdir -p "$fixture/Memoria Vault.app/Contents/app"
set +e
output="$(. "$SCRIPT_DIR/app-jar.sh"; find_packaged_app_jar "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing packaged app JAR discovery to fail." >&2; exit 1; }
assert_contains "$output" "Unable to uniquely locate the packaged application JAR."

fixture="$TMP_DIR/app-jar-multiple"
make_fixture "$fixture" "memoria-vault-one.jar"
cp "$fixture/Memoria Vault.app/Contents/app/memoria-vault-one.jar" "$fixture/Memoria Vault.app/Contents/app/memoria-vault-two.jar"
set +e
output="$(. "$SCRIPT_DIR/app-jar.sh"; find_packaged_app_jar "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected multiple packaged app JAR discovery to fail." >&2; exit 1; }
assert_contains "$output" "Unable to uniquely locate the packaged application JAR."

fixture="$TMP_DIR/freshness-beta"
make_fixture "$fixture" "memoria-vault-0.1.2-beta.7.jar"
source_jar="$fixture/source/memoria-vault-0.1.2-beta.7.jar"
mkdir -p "$fixture/source"
cp "$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.2-beta.7.jar" "$source_jar"
output="$(. "$SCRIPT_DIR/app-jar.sh"; packaged_jar="$(find_packaged_app_jar "$fixture/Memoria Vault.app")"; assert_packaged_app_jar_matches_build "$source_jar" "$packaged_jar" "0.1.2-beta.7" "memoria-vault")"
assert_contains "$output" "Packaging freshness: verified"

fixture="$TMP_DIR/freshness-stable"
make_fixture "$fixture" "memoria-vault-1.0.0.jar"
source_jar="$fixture/source/memoria-vault-1.0.0.jar"
mkdir -p "$fixture/source"
cp "$fixture/Memoria Vault.app/Contents/app/memoria-vault-1.0.0.jar" "$source_jar"
output="$(. "$SCRIPT_DIR/app-jar.sh"; packaged_jar="$(find_packaged_app_jar "$fixture/Memoria Vault.app")"; assert_packaged_app_jar_matches_build "$source_jar" "$packaged_jar" "1.0.0" "memoria-vault")"
assert_contains "$output" "Packaging freshness: verified"

fixture="$TMP_DIR/freshness-stale-target"
make_fixture "$fixture" "memoria-vault-0.1.1.jar"
set +e
output="$(. "$SCRIPT_DIR/app-jar.sh"; packaged_jar="$(find_packaged_app_jar "$fixture/Memoria Vault.app")"; assert_packaged_app_jar_matches_build "$packaged_jar" "$packaged_jar" "0.1.2-beta.6" "memoria-vault" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected stale target JAR version validation to fail." >&2; exit 1; }
assert_contains "$output" "Source production JAR version does not match the expected Maven version."

fixture="$TMP_DIR/freshness-version-mismatch"
make_fixture "$fixture" "memoria-vault-0.1.2.jar"
source_jar="$fixture/source/memoria-vault-0.1.2-beta.7.jar"
mkdir -p "$fixture/source"
cp "$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.2.jar" "$source_jar"
set +e
output="$(. "$SCRIPT_DIR/app-jar.sh"; packaged_jar="$(find_packaged_app_jar "$fixture/Memoria Vault.app")"; assert_packaged_app_jar_matches_build "$source_jar" "$packaged_jar" "0.1.2-beta.7" "memoria-vault" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected packaged JAR version mismatch validation to fail." >&2; exit 1; }
assert_contains "$output" "Packaged application JAR version does not match the expected Maven version."

fixture="$TMP_DIR/freshness-checksum-mismatch"
make_fixture "$fixture" "memoria-vault-0.1.2-beta.7.jar"
source_jar="$fixture/source/memoria-vault-0.1.2-beta.7.jar"
mkdir -p "$fixture/source"
cp "$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.2-beta.7.jar" "$source_jar"
printf 'changed\n' >>"$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.2-beta.7.jar"
set +e
output="$(. "$SCRIPT_DIR/app-jar.sh"; packaged_jar="$(find_packaged_app_jar "$fixture/Memoria Vault.app")"; assert_packaged_app_jar_matches_build "$source_jar" "$packaged_jar" "0.1.2-beta.7" "memoria-vault" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected packaged JAR checksum mismatch validation to fail." >&2; exit 1; }
assert_contains "$output" "Packaged application JAR checksum does not match the source production JAR."

fixture="$TMP_DIR/sign"
make_fixture "$fixture"
STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" "$SCRIPT_DIR/sign-app.sh" "$fixture/Memoria Vault.app" >/dev/null
grep -Fq "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg" "$TMP_DIR/signed.log" || { echo "Expected signing script to sign FFmpeg." >&2; exit 1; }

fixture="$TMP_DIR/sign-keychain"
make_fixture "$fixture"
STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/signed-keychain.log" STUB_CODESIGN_ARGS_LOG="$TMP_DIR/signed-keychain-args.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" "$SCRIPT_DIR/sign-app.sh" "$fixture/Memoria Vault.app" >/dev/null
grep -F -- "--keychain $TMP_DIR/signing.keychain-db" "$TMP_DIR/signed-keychain-args.log" >/dev/null || { echo "Expected signing script to pass KEYCHAIN_PATH to codesign." >&2; exit 1; }
grep -F -- "--keychain $TMP_DIR/signing.keychain-db $fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg" "$TMP_DIR/signed-keychain-args.log" >/dev/null || { echo "Expected FFmpeg signing to use KEYCHAIN_PATH." >&2; exit 1; }
grep -F -- "--keychain $TMP_DIR/signing.keychain-db $fixture/Memoria Vault.app" "$TMP_DIR/signed-keychain-args.log" >/dev/null || { echo "Expected final app signing to use KEYCHAIN_PATH." >&2; exit 1; }
grep -F -- "--options runtime --timestamp --sign Developer ID Application: Test (ZK7G72LVAX)" "$TMP_DIR/signed-keychain-args.log" >/dev/null || { echo "Expected signing script to pass hardened runtime and timestamp." >&2; exit 1; }
grep -F -- "--entitlements" "$TMP_DIR/signed-keychain-args.log" | grep -F -- "$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault" >/dev/null || { echo "Expected launcher signing to use JVM entitlements." >&2; exit 1; }
grep -F -- "--entitlements" "$TMP_DIR/signed-keychain-args.log" | grep -F -- "$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/bin/java" >/dev/null || { echo "Expected java runtime executable signing to use JVM entitlements." >&2; exit 1; }
grep -F -- "--entitlements" "$TMP_DIR/signed-keychain-args.log" | grep -F -- "$fixture/Memoria Vault.app/Contents/runtime/Contents/Home/lib/server/libjvm.dylib" >/dev/null || { echo "Expected libjvm signing to use JVM entitlements." >&2; exit 1; }
grep -F -- "--entitlements" "$TMP_DIR/signed-keychain-args.log" | grep -F -- "$fixture/Memoria Vault.app" >/dev/null || { echo "Expected final app bundle signing to use JVM entitlements." >&2; exit 1; }
signed_without_keychain="$(grep -F -- "--sign Developer ID Application: Test (ZK7G72LVAX)" "$TMP_DIR/signed-keychain-args.log" | grep -Fv -- "--keychain" || true)"
[ -z "$signed_without_keychain" ] || { echo "Expected every app signing operation to use KEYCHAIN_PATH." >&2; exit 1; }

fixture="$TMP_DIR/missing-private-key"
make_fixture "$fixture"
set +e
output="$(STUB_CODESIGN_MISSING_IDENTITY=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/missing-private-key.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" "$SCRIPT_DIR/sign-app.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected signing to fail when the identity is unavailable." >&2; exit 1; }
assert_contains "$output" "Unable to access the configured Developer ID signing identity."
assert_contains "$output" "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly."

fixture="$TMP_DIR/sign-sqlite"
make_fixture "$fixture" "memoria-vault-0.1.1.jar"
STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/sqlite-signed.log" STUB_CODESIGN_ARGS_LOG="$TMP_DIR/sqlite-signed-args.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$fixture/Memoria Vault.app" >/dev/null
grep -Fq "libsqlitejdbc.dylib" "$TMP_DIR/sqlite-signed.log" || { echo "Expected SQLite native libraries to be signed." >&2; exit 1; }
unzip -tq "$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.1.jar" >/dev/null || { echo "Expected modified outer app JAR to pass unzip validation." >&2; exit 1; }
sqlite_check="$TMP_DIR/sqlite-check"
mkdir -p "$sqlite_check/app" "$sqlite_check/sqlite"
(cd "$sqlite_check/app" && jar xf "$fixture/Memoria Vault.app/Contents/app/memoria-vault-0.1.1.jar" BOOT-INF/lib/sqlite-jdbc-test.jar)
(cd "$sqlite_check/sqlite" && jar xf "$sqlite_check/app/BOOT-INF/lib/sqlite-jdbc-test.jar")
test -f "$sqlite_check/sqlite/org/sqlite/native/Mac/aarch64/libsqlitejdbc.dylib" || { echo "Expected arm64 SQLite dylib to remain in app JAR." >&2; exit 1; }
test -f "$sqlite_check/sqlite/org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib" || { echo "Expected x86_64 SQLite dylib to remain in app JAR." >&2; exit 1; }

fixture="$TMP_DIR/sign-sqlite-other-cwd"
make_fixture "$fixture"
(
  cd "$fixture"
  STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/sqlite-other-cwd-signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "Memoria Vault.app" >/dev/null
)
grep -Fq "libsqlitejdbc.dylib" "$TMP_DIR/sqlite-other-cwd-signed.log" || { echo "Expected SQLite signing to work from an arbitrary current directory." >&2; exit 1; }

fixture="$TMP_DIR/sign-sqlite-zip-fails"
make_fixture "$fixture"
outer_jar="$fixture/Memoria Vault.app/Contents/app/memoria-vault-test.jar"
sqlite_entry="BOOT-INF/lib/sqlite-jdbc-test.jar"
before_checksum="$(shasum -a 256 "$outer_jar" | awk '{print $1}')"
set +e
failure_output="$(STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/sqlite-fail-signed.log" PATH="$FAIL_ZIP_DIR:$STUB_DIR:$ORIGINAL_PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$fixture/Memoria Vault.app" 2>&1)"
failure_status=$?
set -e
[ "$failure_status" -ne 0 ] || { echo "Expected SQLite archive update failure to fail the script." >&2; exit 1; }
after_checksum="$(shasum -a 256 "$outer_jar" | awk '{print $1}')"
assert_equals "$before_checksum" "$after_checksum" "The original application JAR must be restored after archive update failure."
assert_contains "$failure_output" "Unable to update packaged SQLite JDBC archive."
assert_contains "$failure_output" "Outer application archive was not modified successfully."
case "$failure_output" in
  *"$TMP_DIR"*)
    echo "Expected SQLite archive update failure output to avoid unsafe temporary absolute paths." >&2
    echo "$failure_output" >&2
    exit 1
    ;;
esac
unzip -tq "$outer_jar" >/dev/null || { echo "Expected restored outer app JAR to pass unzip validation." >&2; exit 1; }
jar tf "$outer_jar" | grep -qx "$sqlite_entry" || { echo "Expected restored outer app JAR to retain the SQLite JDBC entry." >&2; exit 1; }
echo "SQLite archive rollback behavior verified."

fixture="$TMP_DIR/verify-unsigned"
make_fixture "$fixture"
set +e
output="$(PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected unsigned nested binary verification to fail." >&2; exit 1; }
assert_contains "$output" "Unsigned or invalid code"

fixture="$TMP_DIR/verify-adhoc"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_FLAGS='0x10002(runtime,adhoc)' PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected ad hoc signatures to fail metadata verification." >&2; exit 1; }
assert_contains "$output" "Ad hoc signature detected"

fixture="$TMP_DIR/verify-missing-authority"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_MISSING_AUTHORITY=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing Developer ID authority to fail." >&2; exit 1; }
assert_contains "$output" "Missing Developer ID authority"

fixture="$TMP_DIR/verify-missing-timestamp"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_MISSING_TIMESTAMP=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing timestamp to fail." >&2; exit 1; }
assert_contains "$output" "Missing secure timestamp"

fixture="$TMP_DIR/verify-missing-runtime"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_FLAGS=0x0 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing Hardened Runtime to fail." >&2; exit 1; }
assert_contains "$output" "Missing Hardened Runtime flag"

fixture="$TMP_DIR/verify-team-mismatch"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_TEAM_ID=BADTEAM PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected Team ID mismatch to fail." >&2; exit 1; }
assert_contains "$output" "Team Identifier mismatch"

fixture="$TMP_DIR/verify-strict-pass"
make_fixture "$fixture" "memoria-vault-0.1.1.jar"
output="$(STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app")"
assert_contains "$output" "Developer ID verified:"
assert_contains "$output" "Secure timestamp verified:"
assert_contains "$output" "Hardened Runtime verified:"
assert_contains "$output" "SQLite native libraries:"
assert_contains "$output" "Required JVM entitlements:     verified"

fixture="$TMP_DIR/verify-missing-entitlements"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_MISSING_ENTITLEMENTS=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing JVM entitlements to fail." >&2; exit 1; }
assert_contains "$output" "Missing required JVM entitlement com.apple.security.cs.allow-jit"

fixture="$TMP_DIR/verify-unsafe"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_UNSAFE_DEPS=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected unsafe dependency verification to fail." >&2; exit 1; }
assert_contains "$output" "Unsafe external dependency detected"

fixture="$TMP_DIR/notary"
mkdir -p "$fixture"
: >"$fixture/test.dmg"
set +e
output="$(PATH="$STUB_DIR:$PATH" MACOS_NOTARIZATION_ARTIFACT_DIR="$fixture/artifacts" "$SCRIPT_DIR/notarize-dmg.sh" submit "$fixture/test.dmg" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected notarization submit to require credentials." >&2; exit 1; }
assert_contains "$output" "Notarization credentials are required through environment variables or APPLE_NOTARYTOOL_PROFILE."
set +e
output="$(PATH="$STUB_DIR:$PATH" APPLE_ID="test@example.invalid" APPLE_TEAM_ID="ZK7G72LVAX" APPLE_APP_SPECIFIC_PASSWORD="xxx" MACOS_NOTARIZATION_ARTIFACT_DIR="$fixture/artifacts" "$SCRIPT_DIR/notarize-dmg.sh" submit "$fixture/test.dmg" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected notarization failure path to fail." >&2; exit 1; }
test -f "$fixture/artifacts/apple-notarization-log.json" || { echo "Expected notarization failure path to save Apple log." >&2; exit 1; }
assert_contains "$output" "Apple notarization submission ID: test-submission-id"
touch "$fixture/artifacts/accepted"
STUB_XCRUN_LOG="$TMP_DIR/staple-xcrun.log" PATH="$STUB_DIR:$PATH" MACOS_NOTARIZATION_ARTIFACT_DIR="$fixture/artifacts" "$SCRIPT_DIR/notarize-dmg.sh" staple "$fixture/test.dmg" >/dev/null
grep -Fxq "stapler staple" "$TMP_DIR/staple-xcrun.log" || { echo "Expected stapling to call xcrun stapler staple." >&2; exit 1; }
grep -Fxq "stapler validate" "$TMP_DIR/staple-xcrun.log" || { echo "Expected stapling to call xcrun stapler validate." >&2; exit 1; }
if grep -Fq "notarytool" "$TMP_DIR/staple-xcrun.log"; then
  echo "Stapling must not call xcrun notarytool." >&2
  exit 1
fi

MAKE_TMP="$TMP_DIR/make"
mkdir -p "$MAKE_TMP/dist/app"
make_fixture "$MAKE_TMP/dist/app" "memoria-vault-1.2.3.jar"
mkdir -p "$MAKE_TMP/target"
cp "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$MAKE_TMP/target/memoria-vault-1.2.3.jar"
mkdir -p "$MAKE_TMP/clean/dist/app/stale"
touch "$MAKE_TMP/clean/dist/app/stale/old-file"
make -f "$REPO_ROOT/Makefile" clean-macos-app-output DIST_DIR="$MAKE_TMP/clean/dist" >/dev/null
test ! -e "$MAKE_TMP/clean/dist/app/stale/old-file" || { echo "Expected clean-macos-app-output to remove stale app-image output." >&2; exit 1; }

stale_sign="$TMP_DIR/stale-sign"
mkdir -p "$stale_sign/dist/app" "$stale_sign/target"
make_fixture "$stale_sign/dist/app" "memoria-vault-0.1.1.jar"
cp "$MAKE_TMP/target/memoria-vault-1.2.3.jar" "$stale_sign/target/memoria-vault-1.2.3.jar"
set +e
output="$(STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/stale-sign-codesign.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" postprocess-macos-sqlite-native-libs DIST_DIR="$stale_sign/dist" JAR_PATH="$stale_sign/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected SQLite post-processing to fail before codesign when packaged JAR freshness validation fails." >&2; exit 1; }
assert_contains "$output" "Packaged application JAR version does not match the expected Maven version."
test ! -f "$TMP_DIR/stale-sign-codesign.log" || { echo "Expected stale packaged JAR to fail before SQLite codesign runs." >&2; exit 1; }

cp "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$MAKE_TMP/dist/app/.pristine-packaged-app.jar"
STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/make-sqlite-signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" validate-macos-postprocessed-packaged-app DIST_DIR="$MAKE_TMP/dist" JAR_PATH="$MAKE_TMP/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 >/dev/null 2>&1 && {
  echo "Expected unchanged SQLite JDBC archive to fail post-processed validation." >&2
  exit 1
}
STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/make-sqlite-signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$MAKE_TMP/dist/app/Memoria Vault.app" >/dev/null
postprocessed_output="$(STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" validate-macos-postprocessed-packaged-app DIST_DIR="$MAKE_TMP/dist" JAR_PATH="$MAKE_TMP/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3)"
assert_contains "$postprocessed_output" "SQLite post-processing integrity: verified"
assert_contains "$postprocessed_output" "Unexpected modified entries: 0"

non_sqlite="$TMP_DIR/postprocessed-non-sqlite"
mkdir -p "$non_sqlite/dist/app" "$non_sqlite/target"
make_fixture "$non_sqlite/dist/app" "memoria-vault-1.2.3.jar"
cp "$non_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$non_sqlite/target/memoria-vault-1.2.3.jar"
cp "$non_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$non_sqlite/dist/app/.pristine-packaged-app.jar"
STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/non-sqlite-signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$non_sqlite/dist/app/Memoria Vault.app" >/dev/null
non_sqlite_work="$TMP_DIR/non-sqlite-entry-work"
mkdir -p "$non_sqlite_work/BOOT-INF/classes/static"
printf 'changed\n' >"$non_sqlite_work/BOOT-INF/classes/static/index.html"
(
  cd "$non_sqlite_work"
  zip -q "$non_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" BOOT-INF/classes/static/index.html
)
set +e
output="$(STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" validate-macos-postprocessed-packaged-app DIST_DIR="$non_sqlite/dist" JAR_PATH="$non_sqlite/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected non-SQLite archive mutation to fail post-processed validation." >&2; exit 1; }
assert_contains "$output" "Post-processed application JAR changed entries outside SQLite JDBC."

missing_sqlite="$TMP_DIR/postprocessed-missing-sqlite"
mkdir -p "$missing_sqlite/dist/app" "$missing_sqlite/target"
make_fixture "$missing_sqlite/dist/app" "memoria-vault-1.2.3.jar"
cp "$missing_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$missing_sqlite/target/memoria-vault-1.2.3.jar"
cp "$missing_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" "$missing_sqlite/dist/app/.pristine-packaged-app.jar"
STUB_ALL_SIGNED=1 STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/missing-sqlite-signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$missing_sqlite/dist/app/Memoria Vault.app" >/dev/null
missing_work="$TMP_DIR/missing-sqlite-work"
mkdir -p "$missing_work/outer" "$missing_work/sqlite"
(
  cd "$missing_work/outer"
  jar xf "$missing_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" BOOT-INF/lib/sqlite-jdbc-test.jar
)
(
  cd "$missing_work/sqlite"
  jar xf "$missing_work/outer/BOOT-INF/lib/sqlite-jdbc-test.jar"
  rm -f org/sqlite/native/Mac/x86_64/libsqlitejdbc.dylib
  jar cf "$missing_work/outer/BOOT-INF/lib/sqlite-jdbc-test.jar" .
)
(
  cd "$missing_work/outer"
  zip -q "$missing_sqlite/dist/app/Memoria Vault.app/Contents/app/memoria-vault-1.2.3.jar" BOOT-INF/lib/sqlite-jdbc-test.jar
)
set +e
output="$(STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" validate-macos-postprocessed-packaged-app DIST_DIR="$missing_sqlite/dist" JAR_PATH="$missing_sqlite/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected missing SQLite dylib to fail post-processed validation." >&2; exit 1; }
assert_contains "$output" "Post-processed SQLite native library is missing."

set +e
output="$(STUB_SQLITE_SIGNED=1 STUB_MISSING_AUTHORITY=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" validate-macos-postprocessed-packaged-app DIST_DIR="$MAKE_TMP/dist" JAR_PATH="$MAKE_TMP/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected SQLite metadata mismatch to fail post-processed validation." >&2; exit 1; }
assert_contains "$output" "SQLite native library Developer ID authority mismatch."

set +e
output="$(STUB_SQLITE_SIGNED=1 STUB_DITTO_LOG="$TMP_DIR/unsigned-dmg-ditto.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" package-macos-dmg-from-signed-app DIST_DIR="$MAKE_TMP/dist" JAR_PATH="$MAKE_TMP/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 JPACKAGE_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected DMG packaging from unsigned app to fail." >&2; exit 1; }
assert_contains "$output" "Signed macOS app is missing or invalid. Refusing to create a DMG."
test ! -f "$TMP_DIR/unsigned-dmg-ditto.log" || { echo "Expected invalid source app to abort before DMG app copy." >&2; exit 1; }

before="$(mtime "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/MacOS/Memoria Vault")"
output="$(STUB_ALL_SIGNED=1 STUB_DITTO_LOG="$TMP_DIR/signed-dmg-ditto.log" STUB_DITTO_ARGS_LOG="$TMP_DIR/signed-dmg-ditto-args.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" make -f "$REPO_ROOT/Makefile" package-macos-dmg-from-signed-app DIST_DIR="$MAKE_TMP/dist" JAR_PATH="$MAKE_TMP/target/memoria-vault-1.2.3.jar" APP_VERSION=1.2.3 JPACKAGE_VERSION=1.2.3)"
assert_contains "$output" "Creating DMG from verified signed app bundle."
assert_contains "$output" "Source app signature: Developer ID verified."
assert_contains "$output" "Source app bundle: Memoria Vault.app"
after="$(mtime "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/MacOS/Memoria Vault")"
[ "$before" = "$after" ] || { echo "DMG packaging modified the signed app." >&2; exit 1; }
test -f "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg" || { echo "Expected DMG to be created from signed app." >&2; exit 1; }
assert_equals "$MAKE_TMP/dist/app/Memoria Vault.app" "$(cat "$TMP_DIR/signed-dmg-ditto.log")" "Expected DMG creator to copy the already-signed app bundle."
assert_contains "$(cat "$TMP_DIR/signed-dmg-ditto-args.log")" "--rsrc --extattr"
test -d "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg.contents/Memoria Vault.app" || { echo "Expected staged DMG contents to contain Memoria Vault.app." >&2; exit 1; }
test -L "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg.contents/Applications" || { echo "Expected staged DMG contents to contain an Applications symlink." >&2; exit 1; }
assert_equals "/Applications" "$(readlink "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg.contents/Applications")" "Expected Applications shortcut to point to /Applications."

mounted_output="$(STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-dmg-signatures.sh" "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg" "$MAKE_TMP/dist/app/Memoria Vault.app")"
assert_contains "$mounted_output" "Signed app identity continuity: verified"
assert_contains "$mounted_output" "Launcher metadata: matches mounted DMG"
assert_contains "$mounted_output" "FFmpeg metadata: matches mounted DMG"
assert_contains "$mounted_output" "Java runtime metadata: matches mounted DMG"
assert_contains "$mounted_output" "JVM library metadata: matches mounted DMG"

set +e
output="$(STUB_ALL_SIGNED=1 STUB_DMG_REPLACE_UNSIGNED=1 PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" APPLE_TEAM_ID="ZK7G72LVAX" "$SCRIPT_DIR/verify-dmg-signatures.sh" "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg" "$MAKE_TMP/dist/app/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected mounted unsigned replacement app to fail DMG verification." >&2; exit 1; }
assert_contains "$output" "metadata: does not match mounted DMG"

: >"$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg"
STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/dmg-signed.log" STUB_CODESIGN_ARGS_LOG="$TMP_DIR/dmg-signed-args.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" KEYCHAIN_PATH="$TMP_DIR/signing.keychain-db" make -f "$REPO_ROOT/Makefile" sign-macos-dmg DIST_DIR="$MAKE_TMP/dist" APP_VERSION=1.2.3 JPACKAGE_VERSION=1.2.3 >/dev/null
grep -F -- "--keychain $TMP_DIR/signing.keychain-db $MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg" "$TMP_DIR/dmg-signed-args.log" >/dev/null || { echo "Expected DMG signing to use KEYCHAIN_PATH." >&2; exit 1; }

help_output="$(make -f "$REPO_ROOT/Makefile" help)"
assert_contains "$help_output" "Signed release path"
assert_contains "$help_output" "package-macos-dmg-from-signed-app"

release_dmg_target="$(awk '
  /^package-macos-dmg-from-signed-app:/ { in_target = 1 }
  in_target && /^[[:alnum:]_-]+:/ && $0 !~ /^package-macos-dmg-from-signed-app:/ { exit }
  in_target { print }
' "$REPO_ROOT/Makefile")"
case "$release_dmg_target" in
  *"package-macos-app"* | *"jpackage"* | *"clean-macos-app-output"* | *"clean-packaging"*)
    echo "Signed DMG target must not rebuild, clean, or invoke jpackage." >&2
    exit 1
    ;;
esac
assert_contains "$release_dmg_target" "create-dmg.sh"

workflow="$REPO_ROOT/.github/workflows/release-macos-arm64.yml"
import_line="$(grep -n 'Import Developer ID certificate' "$workflow" | head -n 1 | cut -d: -f1)"
identity_line="$(grep -n 'Verify signing identity' "$workflow" | head -n 1 | cut -d: -f1)"
sign_line="$(grep -n 'Sign embedded code' "$workflow" | head -n 1 | cut -d: -f1)"
notarize_line="$(grep -n 'Notarize macOS DMG' "$workflow" | head -n 1 | cut -d: -f1)"
mounted_line="$(grep -n 'Verify mounted DMG signatures' "$workflow" | head -n 1 | cut -d: -f1)"
staple_line="$(grep -n 'Staple notarization ticket' "$workflow" | head -n 1 | cut -d: -f1)"
verify_notarized_line="$(grep -n 'Verify notarized release artifact' "$workflow" | head -n 1 | cut -d: -f1)"
checksum_line="$(grep -n 'Generate checksum' "$workflow" | head -n 1 | cut -d: -f1)"
publish_line="$(grep -n 'Publish GitHub Release assets' "$workflow" | head -n 1 | cut -d: -f1)"
[ "$import_line" -lt "$identity_line" ] || { echo "Release workflow validates identity before import." >&2; exit 1; }
[ "$identity_line" -lt "$sign_line" ] || { echo "Release workflow signs before validating identity." >&2; exit 1; }
[ "$mounted_line" -lt "$notarize_line" ] || { echo "Release workflow notarizes before mounted DMG verification." >&2; exit 1; }
[ "$notarize_line" -lt "$staple_line" ] || { echo "Release workflow staples before notarization." >&2; exit 1; }
[ "$staple_line" -lt "$verify_notarized_line" ] || { echo "Release workflow verifies notarization before stapling." >&2; exit 1; }
[ "$verify_notarized_line" -lt "$checksum_line" ] || { echo "Release workflow generates checksum before final notarization verification." >&2; exit 1; }
[ "$staple_line" -lt "$checksum_line" ] || { echo "Release workflow generates checksum before stapling." >&2; exit 1; }
[ "$checksum_line" -lt "$publish_line" ] || { echo "Release workflow publishes before checksum generation." >&2; exit 1; }
[ "$notarize_line" -lt "$publish_line" ] || { echo "Release workflow publishes before notarization." >&2; exit 1; }
grep -Fq 'make verify-macos-signatures' "$workflow" || { echo "Expected workflow to verify app signatures before DMG creation." >&2; exit 1; }
grep -Fq 'make verify-macos-dmg-signatures' "$workflow" || { echo "Expected workflow to verify mounted DMG signatures before notarization." >&2; exit 1; }
grep -Fq 'MACOS_ENTITLEMENTS_PATH: packaging/macos/entitlements/memoria-vault.entitlements.plist' "$workflow" || { echo "Expected workflow to declare the macOS JVM entitlements path." >&2; exit 1; }
grep -Fq 'Packaged app JAR candidates:' "$workflow" || { echo "Expected workflow to list packaged app JAR candidate filenames before verification." >&2; exit 1; }
grep -Fq 'security find-identity -v -p codesigning "${KEYCHAIN_PATH}"' "$workflow" || { echo "Expected workflow to validate identity in the temporary keychain." >&2; exit 1; }
grep -Fq 'Check that APPLE_CERTIFICATE_P12_BASE64 contains a .p12 exported with its private key' "$workflow" || { echo "Expected workflow to explain missing private key failures safely." >&2; exit 1; }
grep -Fq 'rm -f "${CERTIFICATE_PATH}"' "$workflow" || { echo "Expected workflow cleanup to remove only the temporary certificate file." >&2; exit 1; }
grep -Fq 'security delete-keychain "${KEYCHAIN_PATH}"' "$workflow" || { echo "Expected workflow cleanup to delete the temporary keychain." >&2; exit 1; }
if grep -Eq 'delete-keychain .*login' "$workflow"; then
  echo "Workflow cleanup must not delete the login keychain." >&2
  exit 1
fi
if grep -Eq 'echo \$\{\{ secrets\.(APPLE_APP_SPECIFIC_PASSWORD|APPLE_CERTIFICATE_PASSWORD|APPLE_CERTIFICATE_P12_BASE64)' "$workflow"; then
  echo "Workflow echoes a sensitive secret." >&2
  exit 1
fi
grep -Fq 'find_packaged_app_jar "$APP_PATH"' "$SCRIPT_DIR/verify-dmg-signatures.sh" || { echo "Expected mounted DMG verification to discover the packaged app JAR inside the mounted app." >&2; exit 1; }
if grep -n 'Contents/app/.*basename.*JAR_PATH\|memoria-vault-.*APP_VERSION\|memoria-vault-.*VERSION' "$REPO_ROOT/Makefile" "$SCRIPT_DIR/sign-sqlite-native-libs.sh" "$SCRIPT_DIR/verify-signatures.sh" "$SCRIPT_DIR/verify-dmg-signatures.sh" >/dev/null; then
  echo "Packaging scripts must not construct the packaged app JAR path from the project version." >&2
  exit 1
fi

echo "macOS release pipeline tests passed."
