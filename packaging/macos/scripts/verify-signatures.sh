#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_PATH="${1:-dist/app/Memoria Vault.app}"
EXPECTED_IDENTITY="${APPLE_DEVELOPER_ID_APPLICATION:-}"
EXPECTED_TEAM="${APPLE_TEAM_ID:-}"
DIAGNOSTIC="${MACOS_SIGNING_DIAGNOSTIC:-0}"
SUMMARY_DIR="${MACOS_SIGNING_SUMMARY_DIR:-}"
DEFAULT_ENTITLEMENTS_PATH="$SCRIPT_DIR/../entitlements/memoria-vault.entitlements.plist"
ENTITLEMENTS_PATH="${MACOS_ENTITLEMENTS_PATH:-$DEFAULT_ENTITLEMENTS_PATH}"

# shellcheck source=packaging/macos/scripts/app-jar.sh
. "$SCRIPT_DIR/app-jar.sh"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "macOS signature verification requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Missing app bundle. Run make package-macos-app first." >&2
  exit 1
fi
APP_PATH="$(resolve_absolute_path "$APP_PATH")"

if [ ! -f "$ENTITLEMENTS_PATH" ]; then
  echo "Missing macOS entitlements file." >&2
  exit 1
fi

if [ -z "$EXPECTED_TEAM" ] && [ -n "$EXPECTED_IDENTITY" ]; then
  EXPECTED_TEAM="$(printf '%s\n' "$EXPECTED_IDENTITY" | sed -n 's/.*(\([^()]*\)).*/\1/p')"
fi

if [ -z "$EXPECTED_IDENTITY" ] || [ -z "$EXPECTED_TEAM" ]; then
  echo "APPLE_DEVELOPER_ID_APPLICATION and APPLE_TEAM_ID, or an identity containing a Team ID, are required for release signature verification." >&2
  exit 2
fi

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

for tool in find file codesign otool sort jar mktemp awk; do
  require_tool "$tool"
done

if [ -n "$SUMMARY_DIR" ]; then
  mkdir -p "$SUMMARY_DIR"
fi

WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-verify-signatures.XXXXXX")"
FOUND_LIST="$WORK_DIR/found.txt"
SORTED_LIST="$WORK_DIR/sorted.txt"
SIGNING_SUMMARY="$WORK_DIR/signing-metadata-summary.txt"
RUNTIME_SUMMARY="$WORK_DIR/runtime-signing-summary.txt"
SQLITE_SUMMARY="$WORK_DIR/sqlite-native-signing-summary.txt"
FFMPEG_SUMMARY="$WORK_DIR/ffmpeg-signing-summary.txt"
trap 'rm -rf "$WORK_DIR"' EXIT

is_macho() {
  file "$1" 2>/dev/null | grep -Eq 'Mach-O|universal binary'
}

safe_path() {
  printf '%s' "${1#"$APP_PATH/"}"
}

detail_path() {
  if [ "$DIAGNOSTIC" = "1" ]; then
    printf '%s' "$1"
  else
    safe_path "$1"
  fi
}

dependency_lines() {
  otool -L "$1" 2>/dev/null | sed '1d; s/^[[:space:]]*//; s/ (.*$//'
}

metadata_value() {
  key="$1"
  file="$2"
  sed -n "s/^${key}=//p" "$file" | head -n 1
}

metadata_authorities() {
  file="$1"
  sed -n 's/^Authority=//p' "$file"
}

entitlement_enabled() {
  key="$1"
  file="$2"
  awk -v key="$key" '
    index($0, "<key>" key "</key>") && /<true\/>/ { found = 1; exit }
    index($0, "<key>" key "</key>") { looking = 1; next }
    looking && /<true\/>/ { found = 1; exit }
    looking && (/<false\/>/ || /<key>/) { exit }
    END { exit(found ? 0 : 1) }
  ' "$file"
}

record_line() {
  line="$1"
  printf '%s\n' "$line" >>"$SIGNING_SUMMARY"
}

record_category() {
  category="$1"
  line="$2"
  case "$category" in
    runtime) printf '%s\n' "$line" >>"$RUNTIME_SUMMARY" ;;
    sqlite) printf '%s\n' "$line" >>"$SQLITE_SUMMARY" ;;
    ffmpeg) printf '%s\n' "$line" >>"$FFMPEG_SUMMARY" ;;
  esac
}

record_error() {
  ERRORS=$((ERRORS + 1))
  echo "ERROR: $*" >&2
}

classify() {
  label="$1"
  case "$label" in
    Contents/app/ffmpeg/ffmpeg) printf '%s' "ffmpeg" ;;
    *"/org/sqlite/native/Mac/"*"/libsqlitejdbc.dylib") printf '%s' "sqlite" ;;
    Contents/runtime/*) printf '%s' "runtime" ;;
    *) printf '%s' "general" ;;
  esac
}

verify_macho_metadata() {
  path="$1"
  label="$2"
  category="$(classify "$label")"
  meta="$WORK_DIR/meta-$TOTAL.txt"

  TOTAL=$((TOTAL + 1))

  if ! codesign --verify --strict --verbose=2 "$path" >/dev/null 2>&1; then
    record_error "Unsigned or invalid code: $label"
    return
  fi

  if ! codesign -dv --verbose=4 "$path" >"$meta" 2>&1; then
    record_error "Unable to inspect code signature metadata: $label"
    return
  fi

  identifier="$(metadata_value Identifier "$meta")"
  team="$(metadata_value TeamIdentifier "$meta")"
  timestamp="$(metadata_value Timestamp "$meta")"
  runtime_version="$(metadata_value Runtime "$meta")"
  flags_line="$(grep -E '^CodeDirectory .* flags=' "$meta" | head -n 1 || true)"
  authorities="$(metadata_authorities "$meta")"

  short_flags="${flags_line##* flags=}"
  [ "$short_flags" != "$flags_line" ] || short_flags="-"
  [ -n "$identifier" ] || identifier="-"
  [ -n "$team" ] || team="-"
  [ -n "$timestamp" ] || timestamp="-"
  [ -n "$runtime_version" ] || runtime_version="-"

  record_line "$label"
  record_line "  Identifier: $identifier"
  printf '%s\n' "$authorities" | sed 's/^/  Authority: /' >>"$SIGNING_SUMMARY"
  record_line "  TeamIdentifier: $team"
  record_line "  Timestamp: $timestamp"
  record_line "  Runtime Version: $runtime_version"
  record_line "  CodeDirectory flags: $short_flags"

  if [ "$category" != "general" ]; then
    record_category "$category" "$label"
    record_category "$category" "  TeamIdentifier: $team"
    record_category "$category" "  Timestamp: $timestamp"
    record_category "$category" "  CodeDirectory flags: $short_flags"
  fi

  if printf '%s\n' "$authorities" | grep -Eq '^$|^Authority=$'; then
    record_error "Missing Developer ID authority: $label"
  elif ! printf '%s\n' "$authorities" | grep -Fq "$EXPECTED_IDENTITY"; then
    record_error "Developer ID authority mismatch: $label"
  else
    DEVELOPER_ID_VERIFIED=$((DEVELOPER_ID_VERIFIED + 1))
  fi

  if [ "$team" != "$EXPECTED_TEAM" ]; then
    record_error "Team Identifier mismatch: $label"
  else
    TEAM_VERIFIED=$((TEAM_VERIFIED + 1))
  fi

  if [ "$timestamp" = "-" ]; then
    record_error "Missing secure timestamp: $label"
  else
    TIMESTAMP_VERIFIED=$((TIMESTAMP_VERIFIED + 1))
  fi

  if grep -Eq '^Signature=adhoc$|flags=.*adhoc' "$meta"; then
    ADHOC=$((ADHOC + 1))
    record_error "Ad hoc signature detected: $label"
  fi

  if printf '%s\n' "$short_flags" | grep -Eq 'runtime|0x[0-9a-fA-F]*10000'; then
    RUNTIME_VERIFIED=$((RUNTIME_VERIFIED + 1))
  else
    record_error "Missing Hardened Runtime flag: $label"
  fi

  unsafe="$(dependency_lines "$path" | grep -E '^(/opt/homebrew/|/usr/local/Cellar/|/Users/|/private/var/|/Volumes/)' || true)"
  if [ -n "$unsafe" ]; then
    UNSAFE_DEPS=$((UNSAFE_DEPS + 1))
    record_error "Unsafe external dependency detected in $label"
    if [ "$DIAGNOSTIC" = "1" ]; then
      printf '%s\n' "$unsafe" >&2
    fi
  fi

  case "$category" in
    ffmpeg) FFMPEG_STATUS="Developer ID verified" ;;
    sqlite) SQLITE_VERIFIED=$((SQLITE_VERIFIED + 1)) ;;
    runtime) RUNTIME_FILES=$((RUNTIME_FILES + 1)) ;;
  esac
}

verify_required_entitlements() {
  target="$1"
  label="$2"
  entitlements_file="$WORK_DIR/entitlements-$ENTITLEMENTS_TOTAL.plist"

  ENTITLEMENTS_TOTAL=$((ENTITLEMENTS_TOTAL + 1))

  if [ ! -e "$target" ]; then
    record_error "Required JVM entitlement target is missing: $label"
    return
  fi

  if ! codesign -d --entitlements :- "$target" >"$entitlements_file" 2>/dev/null; then
    record_error "Unable to inspect required JVM entitlements: $label"
    return
  fi

  if ! entitlement_enabled "com.apple.security.cs.allow-jit" "$entitlements_file"; then
    record_error "Missing required JVM entitlement com.apple.security.cs.allow-jit: $label"
    return
  fi

  if ! entitlement_enabled "com.apple.security.cs.allow-unsigned-executable-memory" "$entitlements_file"; then
    record_error "Missing required JVM entitlement com.apple.security.cs.allow-unsigned-executable-memory: $label"
    return
  fi

  ENTITLEMENTS_VERIFIED=$((ENTITLEMENTS_VERIFIED + 1))
  record_line "$label"
  record_line "  Required JVM entitlements: verified"
}

extract_and_record_sqlite_dylibs() {
  app_jar="$1"
  app_label="$2"
  app_extract="$WORK_DIR/app-jar"
  mkdir -p "$app_extract"
  (
    cd "$app_extract"
    jar xf "$app_jar"
  )

  find "$app_extract/BOOT-INF/lib" -type f -name 'sqlite-jdbc-*.jar' 2>/dev/null | while IFS= read -r sqlite_jar; do
    sqlite_label="${sqlite_jar#"$app_extract/"}"
    sqlite_extract="$WORK_DIR/sqlite-$(basename "$sqlite_jar" .jar)"
    mkdir -p "$sqlite_extract"
    (
      cd "$sqlite_extract"
      jar xf "$sqlite_jar"
    )
    find "$sqlite_extract/org/sqlite/native/Mac" -type f -name '*.dylib' 2>/dev/null | while IFS= read -r dylib; do
      if is_macho "$dylib"; then
        rel="${dylib#"$sqlite_extract/"}"
        printf '%s\t%s\n' "$dylib" "$app_label/$sqlite_label/$rel" >>"$FOUND_LIST"
      fi
    done
  done
}

: >"$FOUND_LIST"
: >"$SIGNING_SUMMARY"
: >"$RUNTIME_SUMMARY"
: >"$SQLITE_SUMMARY"
: >"$FFMPEG_SUMMARY"

APP_JAR="$(find_packaged_app_jar "$APP_PATH")"

find "$APP_PATH" -type f -print0 | while IFS= read -r -d '' candidate; do
  if is_macho "$candidate"; then
    printf '%s\t%s\n' "$candidate" "$(safe_path "$candidate")" >>"$FOUND_LIST"
  fi
done
extract_and_record_sqlite_dylibs "$APP_JAR" "$(safe_path "$APP_JAR")"

awk -F '\t' '
  NF >= 2 {
    path = $1
    label = $2
    depth = gsub("/", "/", label)
    printf "%06d\t%s\t%s\n", 999999 - depth, path, label
  }
' "$FOUND_LIST" | sort | cut -f2- >"$SORTED_LIST"

TOTAL=0
DEVELOPER_ID_VERIFIED=0
TIMESTAMP_VERIFIED=0
RUNTIME_VERIFIED=0
TEAM_VERIFIED=0
ADHOC=0
ERRORS=0
UNSAFE_DEPS=0
SQLITE_VERIFIED=0
RUNTIME_FILES=0
FFMPEG_STATUS="missing"
APP_STATUS="not checked"
ENTITLEMENTS_TOTAL=0
ENTITLEMENTS_VERIFIED=0
JVM_ENTITLEMENTS_STATUS="not checked"

while IFS="$(printf '\t')" read -r path label; do
  [ -n "$path" ] || continue
  verify_macho_metadata "$path" "$label"
done <"$SORTED_LIST"

APP_META="$WORK_DIR/app-meta.txt"
if codesign --verify --deep --strict --verbose=4 "$APP_PATH" >/dev/null 2>&1 && codesign -dv --verbose=4 "$APP_PATH" >"$APP_META" 2>&1; then
  app_team="$(metadata_value TeamIdentifier "$APP_META")"
  app_timestamp="$(metadata_value Timestamp "$APP_META")"
  app_authorities="$(metadata_authorities "$APP_META")"
  app_flags="$(grep -E '^CodeDirectory .* flags=' "$APP_META" | head -n 1 || true)"
  record_line "$(basename "$APP_PATH")"
  printf '%s\n' "$app_authorities" | sed 's/^/  Authority: /' >>"$SIGNING_SUMMARY"
  record_line "  TeamIdentifier: ${app_team:-"-"}"
  record_line "  Timestamp: ${app_timestamp:-"-"}"
  record_line "  CodeDirectory flags: ${app_flags##* flags=}"
  if printf '%s\n' "$app_authorities" | grep -Fq "$EXPECTED_IDENTITY" && [ "$app_team" = "$EXPECTED_TEAM" ] && [ -n "$app_timestamp" ] && printf '%s\n' "$app_flags" | grep -Eq 'runtime|0x[0-9a-fA-F]*10000' && ! grep -Eq '^Signature=adhoc$|flags=.*adhoc' "$APP_META"; then
    APP_STATUS="Developer ID verified"
  else
    APP_STATUS="invalid"
    record_error "Final app bundle is missing required Developer ID metadata."
  fi
else
  APP_STATUS="invalid"
  record_error "Final app bundle signature verification failed."
fi

verify_required_entitlements "$APP_PATH" "$(basename "$APP_PATH")"
verify_required_entitlements "$APP_PATH/Contents/MacOS/Memoria Vault" "Contents/MacOS/Memoria Vault"
verify_required_entitlements "$APP_PATH/Contents/runtime/Contents/Home/bin/java" "Contents/runtime/Contents/Home/bin/java"
verify_required_entitlements "$APP_PATH/Contents/runtime/Contents/Home/lib/server/libjvm.dylib" "Contents/runtime/Contents/Home/lib/server/libjvm.dylib"

if [ "$ENTITLEMENTS_TOTAL" -gt 0 ] && [ "$ENTITLEMENTS_VERIFIED" -eq "$ENTITLEMENTS_TOTAL" ]; then
  JVM_ENTITLEMENTS_STATUS="verified"
else
  JVM_ENTITLEMENTS_STATUS="invalid"
fi

echo "macOS notarization-ready signature verification summary"
echo "  Mach-O files detected:          $TOTAL"
echo "  Developer ID verified:         $DEVELOPER_ID_VERIFIED"
echo "  Secure timestamp verified:     $TIMESTAMP_VERIFIED"
echo "  Hardened Runtime verified:     $RUNTIME_VERIFIED"
echo "  Expected Team ID verified:     $TEAM_VERIFIED"
echo "  Ad hoc signatures detected:    $ADHOC"
echo "  Unsafe dependencies detected:  $UNSAFE_DEPS"
echo "  FFmpeg:                        $FFMPEG_STATUS"
echo "  Java runtime files verified:   $RUNTIME_FILES"
echo "  SQLite native libraries:       $SQLITE_VERIFIED"
echo "  App bundle:                    $APP_STATUS"
echo "  Required JVM entitlements:     $JVM_ENTITLEMENTS_STATUS"

if [ -n "$SUMMARY_DIR" ]; then
  cp "$SIGNING_SUMMARY" "$SUMMARY_DIR/signing-metadata-summary.txt"
  cp "$RUNTIME_SUMMARY" "$SUMMARY_DIR/runtime-signing-summary.txt"
  cp "$SQLITE_SUMMARY" "$SUMMARY_DIR/sqlite-native-signing-summary.txt"
  cp "$FFMPEG_SUMMARY" "$SUMMARY_DIR/ffmpeg-signing-summary.txt"
fi

if [ "$ERRORS" -gt 0 ] || [ "$TOTAL" -eq 0 ] || [ "$DEVELOPER_ID_VERIFIED" -ne "$TOTAL" ] || [ "$TIMESTAMP_VERIFIED" -ne "$TOTAL" ] || [ "$RUNTIME_VERIFIED" -ne "$TOTAL" ] || [ "$TEAM_VERIFIED" -ne "$TOTAL" ] || [ "$ADHOC" -ne 0 ] || [ "$UNSAFE_DEPS" -ne 0 ] || [ "$FFMPEG_STATUS" != "Developer ID verified" ] || [ "$SQLITE_VERIFIED" -lt 2 ] || [ "$APP_STATUS" != "Developer ID verified" ] || [ "$JVM_ENTITLEMENTS_STATUS" != "verified" ]; then
  exit 1
fi
