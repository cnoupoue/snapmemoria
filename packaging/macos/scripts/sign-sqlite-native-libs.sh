#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-}"
IDENTITY="${APPLE_DEVELOPER_ID_APPLICATION:-}"
KEYCHAIN_PATH="${KEYCHAIN_PATH:-${APPLE_CODESIGN_KEYCHAIN:-}}"
EXPECTED_TEAM="${APPLE_TEAM_ID:-}"
SUMMARY_DIR="${MACOS_SIGNING_SUMMARY_DIR:-}"

if [ -z "$APP_PATH" ]; then
  echo "Usage: APPLE_DEVELOPER_ID_APPLICATION=<identity> $0 path/to/Memoria Vault.app" >&2
  exit 2
fi

if [ -z "$IDENTITY" ]; then
  echo "APPLE_DEVELOPER_ID_APPLICATION is required." >&2
  exit 2
fi

if [ -z "$EXPECTED_TEAM" ]; then
  EXPECTED_TEAM="$(printf '%s\n' "$IDENTITY" | sed -n 's/.*(\([^()]*\)).*/\1/p')"
fi

if [ -z "$EXPECTED_TEAM" ]; then
  echo "APPLE_TEAM_ID is required, or APPLE_DEVELOPER_ID_APPLICATION must include a Team ID." >&2
  exit 2
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "SQLite native library signing requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Missing app bundle: $APP_PATH" >&2
  exit 1
fi

resolve_absolute_path() {
  target="$1"

  if [ -d "$target" ]; then
    (
      cd "$target"
      pwd -P
    )
  else
    (
      cd "$(dirname "$target")"
      printf '%s/%s\n' "$(pwd -P)" "$(basename "$target")"
    )
  fi
}

APP_PATH="$(resolve_absolute_path "$APP_PATH")"

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

for tool in find file jar codesign mktemp zip unzip; do
  require_tool "$tool"
done

is_macho() {
  file "$1" 2>/dev/null | grep -Eq 'Mach-O|universal binary'
}

codesign_file() {
  target="$1"
  label="$2"
  set +e
  if [ -n "$KEYCHAIN_PATH" ]; then
    output="$(codesign --force --options runtime --timestamp --sign "$IDENTITY" --keychain "$KEYCHAIN_PATH" "$target" 2>&1)"
  else
    output="$(codesign --force --options runtime --timestamp --sign "$IDENTITY" "$target" 2>&1)"
  fi
  status=$?
  set -e
  if [ "$status" -ne 0 ]; then
    if printf '%s\n' "$output" | grep -Eqi 'specified item could not be found|no identity found|identity.*not found|unable to build chain|errSecInternalComponent'; then
      echo "Unable to access the configured Developer ID signing identity." >&2
      echo "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly." >&2
    fi
    echo "Failed to sign SQLite native library: $label" >&2
    exit 1
  fi
}

verify_file() {
  target="$1"
  label="$2"
  meta="$WORK_DIR/sqlite-meta.txt"
  codesign --verify --strict --verbose=2 "$target" >/dev/null
  codesign -dv --verbose=4 "$target" >"$meta" 2>&1
  authorities="$(sed -n 's/^Authority=//p' "$meta")"
  team="$(sed -n 's/^TeamIdentifier=//p' "$meta" | head -n 1)"
  timestamp="$(sed -n 's/^Timestamp=//p' "$meta" | head -n 1)"
  flags="$(grep -E '^CodeDirectory .* flags=' "$meta" | head -n 1 || true)"

  if ! printf '%s\n' "$authorities" | grep -Fq "$IDENTITY"; then
    echo "SQLite native library Developer ID authority mismatch: $label" >&2
    exit 1
  fi
  if [ "$team" != "$EXPECTED_TEAM" ]; then
    echo "SQLite native library Team Identifier mismatch: $label" >&2
    exit 1
  fi
  if [ -z "$timestamp" ]; then
    echo "SQLite native library is missing a secure timestamp: $label" >&2
    exit 1
  fi
  if ! printf '%s\n' "$flags" | grep -Eq 'runtime|0x[0-9a-fA-F]*10000'; then
    echo "SQLite native library is missing Hardened Runtime: $label" >&2
    exit 1
  fi
}

WORK_DIR="$(resolve_absolute_path "$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-sqlite-sign.XXXXXX")")"
SUMMARY_FILE="$WORK_DIR/sqlite-native-signing-summary.txt"
APP_JAR_BACKUP=""

cleanup() {
  status=$?
  if [ "$status" -ne 0 ] && [ -n "$APP_JAR_BACKUP" ] && [ -f "$APP_JAR_BACKUP" ] && [ -n "${APP_JAR:-}" ]; then
    cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
  fi
  rm -rf "$WORK_DIR"
  exit "$status"
}
trap cleanup EXIT
: >"$SUMMARY_FILE"

APP_JAR="$(find "$APP_PATH/Contents/app" -maxdepth 1 -type f -name '*.jar' -print | head -n 1)"
if [ -z "$APP_JAR" ]; then
  echo "Missing packaged application JAR in app bundle." >&2
  exit 1
fi
APP_JAR="$(resolve_absolute_path "$APP_JAR")"
APP_JAR_BACKUP="$WORK_DIR/$(basename "$APP_JAR").backup"
cp "$APP_JAR" "$APP_JAR_BACKUP"

APP_JAR_WORK="$WORK_DIR/app-jar"
mkdir -p "$APP_JAR_WORK"
cp "$APP_JAR" "$WORK_DIR/app.jar"
(
  cd "$APP_JAR_WORK"
  jar xf "$WORK_DIR/app.jar"
)

SQLITE_JARS_FILE="$WORK_DIR/sqlite-jars.txt"
find "$APP_JAR_WORK/BOOT-INF/lib" -type f -name 'sqlite-jdbc-*.jar' -print >"$SQLITE_JARS_FILE"
if [ ! -s "$SQLITE_JARS_FILE" ]; then
  echo "No sqlite-jdbc dependency JAR found in packaged application JAR." >&2
  exit 1
fi

SIGNED_COUNT=0
UPDATED_SQLITE_ENTRIES="$WORK_DIR/updated-sqlite-entries.txt"
: >"$UPDATED_SQLITE_ENTRIES"
while IFS= read -r sqlite_jar; do
  sqlite_jar="$(resolve_absolute_path "$sqlite_jar")"
  sqlite_name="$(basename "$sqlite_jar")"
  sqlite_work="$WORK_DIR/sqlite-${sqlite_name%.jar}"
  mkdir -p "$sqlite_work"
  (
    cd "$sqlite_work"
    jar xf "$sqlite_jar"
  )

  found_for_jar=0
  while IFS= read -r dylib; do
    if is_macho "$dylib"; then
      rel="${dylib#"$sqlite_work/"}"
      label="BOOT-INF/lib/$sqlite_name/$rel"
      codesign_file "$dylib" "$label"
      verify_file "$dylib" "$label"
      printf '%s\n' "$label" >>"$SUMMARY_FILE"
      found_for_jar=$((found_for_jar + 1))
      SIGNED_COUNT=$((SIGNED_COUNT + 1))
    fi
  done < <(find "$sqlite_work/org/sqlite/native/Mac" -type f -name '*.dylib' -print 2>/dev/null || true)

  if [ "$found_for_jar" -eq 0 ]; then
    echo "No macOS SQLite native libraries found in $sqlite_name." >&2
    exit 1
  fi

  (
    cd "$sqlite_work"
    if [ -f META-INF/MANIFEST.MF ]; then
      jar cfm "$sqlite_jar" META-INF/MANIFEST.MF .
    else
      jar cf "$sqlite_jar" .
    fi
  )

  sqlite_rel="${sqlite_jar#"$APP_JAR_WORK/"}"
  printf '%s\n' "$sqlite_rel" >>"$UPDATED_SQLITE_ENTRIES"
  zip -q -d "$APP_JAR" "$sqlite_rel" >/dev/null || {
    cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
    echo "Unable to update packaged SQLite JDBC archive." >&2
    echo "Outer application archive was not modified successfully." >&2
    exit 1
  }
  (
    cd "$APP_JAR_WORK"
    zip -0 -q "$APP_JAR" "$sqlite_rel"
  ) || {
    cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
    echo "Unable to update packaged SQLite JDBC archive." >&2
    echo "Outer application archive was not modified successfully." >&2
    exit 1
  }
done <"$SQLITE_JARS_FILE"

if [ "$SIGNED_COUNT" -lt 2 ]; then
  echo "Expected at least arm64 and x86_64 SQLite native libraries to be signed." >&2
  exit 1
fi

test -f "$APP_JAR" || {
  cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
  echo "Unable to update packaged SQLite JDBC archive." >&2
  echo "Outer application archive was not modified successfully." >&2
  exit 1
}

unzip -tq "$APP_JAR" >/dev/null || {
  cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
  echo "Unable to update packaged SQLite JDBC archive." >&2
  echo "Outer application archive was not modified successfully." >&2
  exit 1
}

APP_JAR_ENTRIES="$WORK_DIR/app-jar-entries.txt"
jar tf "$APP_JAR" >"$APP_JAR_ENTRIES"
grep -Fx 'BOOT-INF/classes/static/index.html' "$APP_JAR_ENTRIES" >/dev/null || {
  cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
  echo "Modified application JAR failed packaged asset verification." >&2
  exit 1
}

while IFS= read -r sqlite_entry; do
  jar tf "$APP_JAR" >"$APP_JAR_ENTRIES"
  grep -Fx "$sqlite_entry" "$APP_JAR_ENTRIES" >/dev/null || {
    cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
    echo "Unable to update packaged SQLite JDBC archive." >&2
    echo "Outer application archive was not modified successfully." >&2
    exit 1
  }

  verify_outer="$WORK_DIR/reverify-outer-$(basename "$sqlite_entry" .jar)"
  verify_sqlite="$WORK_DIR/reverify-sqlite-$(basename "$sqlite_entry" .jar)"
  mkdir -p "$verify_outer" "$verify_sqlite"
  (
    cd "$verify_outer"
    jar xf "$APP_JAR" "$sqlite_entry"
  )
  (
    cd "$verify_sqlite"
    jar xf "$verify_outer/$sqlite_entry"
  )

  for arch in aarch64 x86_64; do
    dylib="$verify_sqlite/org/sqlite/native/Mac/$arch/libsqlitejdbc.dylib"
    test -f "$dylib" || {
      cp "$APP_JAR_BACKUP" "$APP_JAR" 2>/dev/null || true
      echo "Unable to update packaged SQLite JDBC archive." >&2
      echo "Outer application archive was not modified successfully." >&2
      exit 1
    }
    verify_file "$dylib" "$sqlite_entry/org/sqlite/native/Mac/$arch/libsqlitejdbc.dylib"
  done
done <"$UPDATED_SQLITE_ENTRIES"

if [ -n "$SUMMARY_DIR" ]; then
  mkdir -p "$SUMMARY_DIR"
  cp "$SUMMARY_FILE" "$SUMMARY_DIR/sqlite-native-signing-summary.txt"
fi

rm -f "$APP_JAR_BACKUP"
APP_JAR_BACKUP=""

echo "Signed $SIGNED_COUNT SQLite native libraries inside the packaged application JAR."
