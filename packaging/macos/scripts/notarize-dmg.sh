#!/usr/bin/env bash
set -euo pipefail

MODE="${1:-all}"
DMG_PATH="${2:-dist/installers/Memoria-Vault.dmg}"
ARTIFACT_DIR="${MACOS_NOTARIZATION_ARTIFACT_DIR:-dist/notarization}"
SUBMISSION_ID_FILE="$ARTIFACT_DIR/submission-id.txt"
STATUS_FILE="$ARTIFACT_DIR/status.txt"
SUBMIT_JSON="$ARTIFACT_DIR/notarytool-submit.json"
APPLE_LOG_JSON="$ARTIFACT_DIR/apple-notarization-log.json"
ACCEPTED_MARKER="$ARTIFACT_DIR/accepted"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "macOS notarization requires macOS." >&2
  exit 1
fi

if [ ! -f "$DMG_PATH" ]; then
  echo "Missing DMG: $DMG_PATH" >&2
  exit 1
fi

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

for tool in xcrun codesign spctl plutil; do
  require_tool "$tool"
done

mkdir -p "$ARTIFACT_DIR"

submit_args=()

require_notarization_credentials() {
  if [ -n "${APPLE_ID:-}" ] || [ -n "${APPLE_TEAM_ID:-}" ] || [ -n "${APPLE_APP_SPECIFIC_PASSWORD:-}" ]; then
    if [ -z "${APPLE_ID:-}" ] || [ -z "${APPLE_TEAM_ID:-}" ] || [ -z "${APPLE_APP_SPECIFIC_PASSWORD:-}" ]; then
      echo "APPLE_ID, APPLE_TEAM_ID, and APPLE_APP_SPECIFIC_PASSWORD must be set together." >&2
      exit 2
    fi
    submit_args=(--apple-id "$APPLE_ID" --team-id "$APPLE_TEAM_ID" --password "$APPLE_APP_SPECIFIC_PASSWORD")
  elif [ -n "${APPLE_NOTARYTOOL_PROFILE:-}" ]; then
    submit_args=(--keychain-profile "$APPLE_NOTARYTOOL_PROFILE")
  else
    echo "Notarization credentials are required through environment variables or APPLE_NOTARYTOOL_PROFILE." >&2
    exit 2
  fi
}

json_get() {
  key="$1"
  file="$2"
  plutil -extract "$key" raw -o - "$file" 2>/dev/null || true
}

fetch_log() {
  submission_id="$1"
  if [ -n "$submission_id" ]; then
    xcrun notarytool log "$submission_id" "${submit_args[@]}" "$APPLE_LOG_JSON" >/dev/null 2>&1 || true
  fi
}

submit_and_wait() {
  require_notarization_credentials
  rm -f "$ACCEPTED_MARKER"
  echo "Submitting signed DMG to Apple notarization."
  stderr_file="$(mktemp "${TMPDIR:-/tmp}/memoriavault-notarytool-stderr.XXXXXX")"
  set +e
  xcrun notarytool submit "$DMG_PATH" "${submit_args[@]}" --wait --output-format json >"$SUBMIT_JSON" 2>"$stderr_file"
  submit_status=$?
  set -e
  rm -f "$stderr_file"

  submission_id="$(json_get id "$SUBMIT_JSON")"
  status="$(json_get status "$SUBMIT_JSON")"
  printf '%s\n' "$submission_id" >"$SUBMISSION_ID_FILE"
  printf '%s\n' "$status" >"$STATUS_FILE"

  echo "Apple notarization submission ID: $submission_id"
  echo "Apple notarization status: $status"

  if [ "$submit_status" -ne 0 ] || [ "$status" != "Accepted" ]; then
    fetch_log "$submission_id"
    echo "Notarization was not accepted. Apple log saved as a CI artifact when available." >&2
    exit 1
  fi

  touch "$ACCEPTED_MARKER"
}

staple() {
  if [ ! -f "$ACCEPTED_MARKER" ]; then
    echo "Refusing to staple because notarization has not been accepted for this artifact." >&2
    exit 1
  fi
  xcrun stapler staple "$DMG_PATH"
  xcrun stapler validate "$DMG_PATH"
}

verify_artifact() {
  codesign --verify --strict --verbose=2 "$DMG_PATH"
  xcrun stapler validate "$DMG_PATH"
  spctl --assess --type open --context context:primary-signature --verbose=2 "$DMG_PATH"
}

case "$MODE" in
  submit)
    submit_and_wait
    ;;
  staple)
    staple
    ;;
  verify)
    verify_artifact
    ;;
  all)
    submit_and_wait
    staple
    verify_artifact
    ;;
  *)
    echo "Usage: $0 submit|staple|verify|all path/to.dmg" >&2
    exit 2
    ;;
esac
