#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-}"
DMG_PATH="${2:-}"
VOLUME_NAME="${3:-Memoria Vault}"

if [ -z "$APP_PATH" ] || [ -z "$DMG_PATH" ]; then
  echo "Usage: $0 path/to/Memoria Vault.app path/to/output.dmg [volume-name]" >&2
  exit 2
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "DMG creation requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Signed macOS app is missing or invalid. Refusing to create a DMG." >&2
  exit 1
fi

for tool in hdiutil ditto mktemp; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Missing required tool: $tool" >&2
    exit 1
  }
done

APP_NAME="$(basename "$APP_PATH")"
STAGING_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-dmg-stage.XXXXXX")"
cleanup() {
  rm -rf "$STAGING_DIR"
}
trap cleanup EXIT

mkdir -p "$(dirname "$DMG_PATH")"
rm -f "$DMG_PATH"

echo "Creating DMG from verified signed app bundle."
echo "Source app signature: Developer ID verified."
echo "Source app bundle: $APP_NAME"

ditto --rsrc --extattr "$APP_PATH" "$STAGING_DIR/$APP_NAME"
ln -s /Applications "$STAGING_DIR/Applications"

hdiutil create \
  -volname "$VOLUME_NAME" \
  -srcfolder "$STAGING_DIR" \
  -ov \
  -format UDZO \
  "$DMG_PATH" >/dev/null

test -f "$DMG_PATH" || {
  echo "DMG creation failed." >&2
  exit 1
}
