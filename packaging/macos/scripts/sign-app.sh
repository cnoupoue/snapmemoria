#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-}"
IDENTITY="${APPLE_DEVELOPER_ID_APPLICATION:-}"
KEYCHAIN_PATH="${KEYCHAIN_PATH:-${APPLE_CODESIGN_KEYCHAIN:-}}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEFAULT_ENTITLEMENTS_PATH="$SCRIPT_DIR/../entitlements/memoria-vault.entitlements.plist"
ENTITLEMENTS_PATH="${MACOS_ENTITLEMENTS_PATH:-$DEFAULT_ENTITLEMENTS_PATH}"

if [ -z "$APP_PATH" ]; then
  echo "Usage: APPLE_DEVELOPER_ID_APPLICATION=<identity> $0 path/to/Memoria Vault.app" >&2
  exit 2
fi

if [ -z "$IDENTITY" ]; then
  echo "APPLE_DEVELOPER_ID_APPLICATION is required." >&2
  exit 2
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "Developer ID signing requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Missing app bundle: $APP_PATH" >&2
  exit 1
fi

if [ ! -f "$ENTITLEMENTS_PATH" ]; then
  echo "Missing macOS entitlements file." >&2
  exit 1
fi

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

for tool in find file codesign sort awk; do
  require_tool "$tool"
done

is_macho() {
  file "$1" 2>/dev/null | grep -Eq 'Mach-O|universal binary'
}

requires_jvm_entitlements() {
  rel="$1"
  case "$rel" in
    Contents/MacOS/*) return 0 ;;
    Contents/runtime/Contents/Home/bin/java) return 0 ;;
    Contents/runtime/Contents/Home/lib/server/libjvm.dylib) return 0 ;;
    *) return 1 ;;
  esac
}

sign_one() {
  target="$1"
  rel="${target#"$APP_PATH/"}"
  codesign_command=(codesign --force --options runtime --timestamp --sign "$IDENTITY")
  if requires_jvm_entitlements "$rel"; then
    codesign_command+=(--entitlements "$ENTITLEMENTS_PATH")
  fi
  if [ -n "$KEYCHAIN_PATH" ]; then
    codesign_command+=(--keychain "$KEYCHAIN_PATH")
  fi

  echo "Signing nested code: $rel"
  set +e
  sign_output="$("${codesign_command[@]}" "$target" 2>&1)"
  sign_status=$?
  set -e
  if [ "$sign_status" -ne 0 ]; then
    if printf '%s\n' "$sign_output" | grep -Eqi 'specified item could not be found|no identity found|identity.*not found|unable to build chain|errSecInternalComponent'; then
      echo "Unable to access the configured Developer ID signing identity." >&2
      echo "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly." >&2
    fi
    echo "Failed to sign nested Mach-O code: $rel" >&2
    exit 1
  fi
}

FOUND_PATHS_FILE="$(mktemp "${TMPDIR:-/tmp}/memoriavault-sign-paths.XXXXXX")"
SORTED_PATHS_FILE="$(mktemp "${TMPDIR:-/tmp}/memoriavault-sign-sorted.XXXXXX")"
trap 'rm -f "$FOUND_PATHS_FILE" "$SORTED_PATHS_FILE"' EXIT

find "$APP_PATH" -type f -print0 | while IFS= read -r -d '' candidate; do
  if is_macho "$candidate"; then
    printf '%s\0' "$candidate"
  fi
done >"$FOUND_PATHS_FILE"

if [ ! -s "$FOUND_PATHS_FILE" ]; then
  echo "No Mach-O code found inside app bundle." >&2
  exit 1
fi

tr '\0' '\n' <"$FOUND_PATHS_FILE" | awk '
  length($0) > 0 {
    path = $0
    depth = gsub("/", "/", path)
    printf "%06d\t%s\n", 999999 - depth, $0
  }
' | sort | sed 's/^[0-9][0-9][0-9][0-9][0-9][0-9]	//' >"$SORTED_PATHS_FILE"

TOTAL=0
while IFS= read -r binary; do
  case "$binary" in
    "$APP_PATH/Contents/MacOS/"*)
      ;;
    "$APP_PATH/Contents/app/ffmpeg/ffmpeg")
      ;;
    "$APP_PATH/Contents/runtime/"*)
      ;;
    *".framework/"*|*.dylib|*.jnilib)
      ;;
  esac
  sign_one "$binary"
  TOTAL=$((TOTAL + 1))
done <"$SORTED_PATHS_FILE"

FFMPEG_PATH="$APP_PATH/Contents/app/ffmpeg/ffmpeg"
if [ ! -f "$FFMPEG_PATH" ]; then
  echo "Missing bundled FFmpeg: Contents/app/ffmpeg/ffmpeg" >&2
  exit 1
fi
if ! is_macho "$FFMPEG_PATH"; then
  echo "Bundled FFmpeg is not Mach-O code." >&2
  exit 1
fi

echo "Signing bundled FFmpeg explicitly: Contents/app/ffmpeg/ffmpeg"
sign_one "$FFMPEG_PATH"

echo "Signing final app bundle: $(basename "$APP_PATH")"
app_sign_command=(codesign --force --options runtime --timestamp --sign "$IDENTITY" --entitlements "$ENTITLEMENTS_PATH")
if [ -n "$KEYCHAIN_PATH" ]; then
  app_sign_command+=(--keychain "$KEYCHAIN_PATH")
fi
set +e
app_sign_output="$("${app_sign_command[@]}" "$APP_PATH" 2>&1)"
app_sign_status=$?
set -e
if [ "$app_sign_status" -ne 0 ]; then
  if printf '%s\n' "$app_sign_output" | grep -Eqi 'specified item could not be found|no identity found|identity.*not found|unable to build chain|errSecInternalComponent'; then
    echo "Unable to access the configured Developer ID signing identity." >&2
    echo "Check that the certificate private key is available and that KEYCHAIN_PATH is configured correctly." >&2
  fi
  echo "Failed to sign final app bundle." >&2
  exit 1
fi

codesign --verify --deep --strict --verbose=2 "$APP_PATH"

echo "Signed $TOTAL nested Mach-O files and the final app bundle."
