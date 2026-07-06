#!/usr/bin/env bash
set -euo pipefail

DMG_PATH="${1:-dist/installers/Memoria-Vault.dmg}"
SUMMARY_DIR="${MACOS_SIGNING_SUMMARY_DIR:-}"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "Mounted DMG signature verification requires macOS." >&2
  exit 1
fi

if [ ! -f "$DMG_PATH" ]; then
  echo "Missing DMG: $DMG_PATH" >&2
  exit 1
fi

for tool in hdiutil find mktemp; do
  command -v "$tool" >/dev/null 2>&1 || {
    echo "Missing required tool: $tool" >&2
    exit 1
  }
done

MOUNT_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-dmg.XXXXXX")"
cleanup() {
  hdiutil detach "$MOUNT_DIR" >/dev/null 2>&1 || true
  rmdir "$MOUNT_DIR" >/dev/null 2>&1 || true
}
trap cleanup EXIT

hdiutil attach -readonly -nobrowse -mountpoint "$MOUNT_DIR" "$DMG_PATH" >/dev/null

APP_PATH="$(find "$MOUNT_DIR" -maxdepth 1 -type d -name '*.app' -print | head -n 1)"
if [ -z "$APP_PATH" ]; then
  echo "Mounted DMG does not contain an app bundle." >&2
  exit 1
fi

if [ -n "$SUMMARY_DIR" ]; then
  mkdir -p "$SUMMARY_DIR"
  mounted_summary_dir="$SUMMARY_DIR/mounted-dmg"
  mkdir -p "$mounted_summary_dir"
  MACOS_SIGNING_SUMMARY_DIR="$mounted_summary_dir" "$(dirname "$0")/verify-signatures.sh" "$APP_PATH" >"$SUMMARY_DIR/mounted-dmg-signing-summary.txt"
else
  "$(dirname "$0")/verify-signatures.sh" "$APP_PATH"
fi
