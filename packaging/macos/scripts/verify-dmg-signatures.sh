#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DMG_PATH="${1:-dist/installers/Memoria-Vault.dmg}"
SOURCE_APP_PATH="${2:-dist/app/Memoria Vault.app}"
SUMMARY_DIR="${MACOS_SIGNING_SUMMARY_DIR:-}"

# shellcheck source=packaging/macos/scripts/app-jar.sh
. "$SCRIPT_DIR/app-jar.sh"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "Mounted DMG signature verification requires macOS." >&2
  exit 1
fi

if [ ! -f "$DMG_PATH" ]; then
  echo "Missing DMG: $DMG_PATH" >&2
  exit 1
fi

if [ ! -d "$SOURCE_APP_PATH" ]; then
  echo "Missing source signed app bundle." >&2
  exit 1
fi

for tool in hdiutil find mktemp jar awk codesign grep; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Missing required tool: $tool" >&2
    exit 1
  }
done

SOURCE_APP_PATH="$(resolve_absolute_path "$SOURCE_APP_PATH")"

metadata_value() {
  key="$1"
  file="$2"
  sed -n "s/^${key}=//p" "$file" | head -n 1
}

metadata_authorities() {
  file="$1"
  sed -n 's/^Authority=//p' "$file" | tr '\n' ';' | sed 's/;$//'
}

metadata_runtime_flag() {
  file="$1"
  flags="$(grep -E '^CodeDirectory .* flags=' "$file" | head -n 1 || true)"
  if printf '%s\n' "$flags" | grep -Eq 'runtime|0x[0-9a-fA-F]*10000'; then
    printf '%s' "runtime"
  else
    printf '%s' "missing"
  fi
}

metadata_cdhash() {
  file="$1"
  cdhash="$(metadata_value CDHash "$file")"
  if [ -n "$cdhash" ]; then
    printf '%s' "$cdhash"
  else
    grep -E '^CodeDirectory ' "$file" | shasum -a 256 | awk '{print $1}'
  fi
}

capture_metadata() {
  app_path="$1"
  rel_path="$2"
  output_file="$3"
  target="$app_path/$rel_path"
  raw_file="$output_file.raw"

  if [ ! -e "$target" ]; then
    echo "Representative signed file is missing: $rel_path" >&2
    return 1
  fi

  codesign -dv --verbose=4 "$target" >"$raw_file" 2>&1 || {
    echo "Unable to inspect representative signed file: $rel_path" >&2
    return 1
  }

  {
    printf 'Authority=%s\n' "$(metadata_authorities "$raw_file")"
    printf 'TeamIdentifier=%s\n' "$(metadata_value TeamIdentifier "$raw_file")"
    if [ -n "$(metadata_value Timestamp "$raw_file")" ]; then
      printf 'Timestamp=present\n'
    else
      printf 'Timestamp=missing\n'
    fi
    printf 'Runtime=%s\n' "$(metadata_runtime_flag "$raw_file")"
    printf 'CodeDirectoryHash=%s\n' "$(metadata_cdhash "$raw_file")"
  } >"$output_file"
}

compare_representative_metadata() {
  source_app="$1"
  mounted_app="$2"
  work_dir="$3"
  failures=0

  while IFS='|' read -r label rel_path; do
    [ -n "$label" ] || continue
    source_meta="$work_dir/source-$label.txt"
    mounted_meta="$work_dir/mounted-$label.txt"
    capture_metadata "$source_app" "$rel_path" "$source_meta" || return 1
    capture_metadata "$mounted_app" "$rel_path" "$mounted_meta" || return 1
    if cmp -s "$source_meta" "$mounted_meta"; then
      echo "$label metadata: matches mounted DMG"
    else
      echo "$label metadata: does not match mounted DMG" >&2
      failures=$((failures + 1))
    fi
  done <<'EOF'
Launcher|Contents/MacOS/Memoria Vault
FFmpeg|Contents/app/ffmpeg/ffmpeg
Java runtime|Contents/runtime/Contents/Home/bin/java
JVM library|Contents/runtime/Contents/Home/lib/server/libjvm.dylib
EOF

  if [ "$failures" -ne 0 ]; then
    return 1
  fi

  echo "Signed app identity continuity: verified"
}

MOUNT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-dmg.XXXXXX")"
WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-dmg-verify.XXXXXX")"
cleanup() {
  hdiutil detach "$MOUNT_DIR" >/dev/null 2>&1 || true
  rm -rf "$MOUNT_DIR"
  rm -rf "$WORK_DIR"
}
trap cleanup EXIT

hdiutil attach -readonly -nobrowse -mountpoint "$MOUNT_DIR" "$DMG_PATH" >/dev/null

APP_PATH="$(find "$MOUNT_DIR" -maxdepth 1 -type d -name '*.app' -print | head -n 1)"
if [ -z "$APP_PATH" ]; then
  echo "Mounted DMG does not contain an app bundle." >&2
  exit 1
fi

find_packaged_app_jar "$APP_PATH" >/dev/null
compare_representative_metadata "$SOURCE_APP_PATH" "$APP_PATH" "$WORK_DIR"

if [ -n "$SUMMARY_DIR" ]; then
  mkdir -p "$SUMMARY_DIR"
  mounted_summary_dir="$SUMMARY_DIR/mounted-dmg"
  mkdir -p "$mounted_summary_dir"
  MACOS_SIGNING_SUMMARY_DIR="$mounted_summary_dir" "$SCRIPT_DIR/verify-signatures.sh" "$APP_PATH" >"$SUMMARY_DIR/mounted-dmg-signing-summary.txt"
else
  "$SCRIPT_DIR/verify-signatures.sh" "$APP_PATH"
fi
