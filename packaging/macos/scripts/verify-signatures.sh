#!/usr/bin/env bash
set -euo pipefail

APP_PATH="${1:-dist/app/Memoria Vault.app}"
EXPECTED_IDENTITY="${APPLE_DEVELOPER_ID_APPLICATION:-}"
DIAGNOSTIC="${MACOS_SIGNING_DIAGNOSTIC:-0}"

if [ "$(uname -s)" != "Darwin" ]; then
  echo "macOS signature verification requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Missing app bundle. Run make package-macos-app first." >&2
  exit 1
fi

require_tool() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing required tool: $1" >&2
    exit 1
  }
}

for tool in find file codesign otool sort awk; do
  require_tool "$tool"
done

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

team_identifier() {
  codesign -dv --verbose=4 "$1" 2>&1 | sed -n 's/^TeamIdentifier=//p' | head -n 1
}

is_adhoc() {
  codesign -dv --verbose=4 "$1" 2>&1 | grep -Eq '^Signature=adhoc$|flags=.*adhoc'
}

dependency_lines() {
  otool -L "$1" 2>/dev/null | sed '1d; s/^[[:space:]]*//; s/ (.*$//'
}

EXPECTED_TEAM=""
if [ -n "$EXPECTED_IDENTITY" ]; then
  EXPECTED_TEAM="$(printf '%s\n' "$EXPECTED_IDENTITY" | sed -n 's/.*(\([^()]*\)).*/\1/p')"
fi

FOUND_PATHS_FILE="$(mktemp "${TMPDIR:-/tmp}/memoriavault-verify-paths.XXXXXX")"
SORTED_PATHS_FILE="$(mktemp "${TMPDIR:-/tmp}/memoriavault-verify-sorted.XXXXXX")"
trap 'rm -f "$FOUND_PATHS_FILE" "$SORTED_PATHS_FILE"' EXIT

find "$APP_PATH" -type f -print0 | while IFS= read -r -d '' candidate; do
  if is_macho "$candidate"; then
    printf '%s\0' "$candidate"
  fi
done >"$FOUND_PATHS_FILE"

tr '\0' '\n' <"$FOUND_PATHS_FILE" | awk '
  length($0) > 0 {
    path = $0
    depth = gsub("/", "/", path)
    printf "%06d\t%s\n", 999999 - depth, $0
  }
' | sort | sed 's/^[0-9][0-9][0-9][0-9][0-9][0-9]	//' >"$SORTED_PATHS_FILE"

TOTAL=0
SIGNED=0
ERRORS=0
UNSAFE_DEPS=0
FFMPEG_STATUS="missing"
APP_STATUS="not checked"
TEAM_STATUS="not checked"
FIRST_TEAM=""

record_error() {
  ERRORS=$((ERRORS + 1))
  echo "ERROR: $*" >&2
}

verify_one() {
  path="$1"
  rel="$(detail_path "$path")"
  TOTAL=$((TOTAL + 1))

  if ! codesign --verify --strict --verbose=2 "$path" >/dev/null 2>&1; then
    record_error "Unsigned or invalid nested code: $rel"
    return
  fi

  if is_adhoc "$path"; then
    record_error "Ad-hoc signature is not acceptable for release: $rel"
    return
  fi

  SIGNED=$((SIGNED + 1))

  team="$(team_identifier "$path")"
  if [ -n "$team" ] && [ "$team" != "not set" ]; then
    if [ -z "$FIRST_TEAM" ]; then
      FIRST_TEAM="$team"
    elif [ "$team" != "$FIRST_TEAM" ]; then
      TEAM_STATUS="mismatch"
      record_error "Team Identifier mismatch on nested code: $rel"
    fi

    if [ -n "$EXPECTED_TEAM" ] && [ "$team" != "$EXPECTED_TEAM" ]; then
      TEAM_STATUS="mismatch"
      record_error "Unexpected Team Identifier on nested code: $rel"
    fi
  fi

  unsafe="$(dependency_lines "$path" | grep -E '^(/opt/homebrew/|/usr/local/Cellar/|/Users/|/private/var/|/Volumes/)' || true)"
  if [ -n "$unsafe" ]; then
    UNSAFE_DEPS=$((UNSAFE_DEPS + 1))
    record_error "Unsafe external dependency detected in $rel"
    if [ "$DIAGNOSTIC" = "1" ]; then
      printf '%s\n' "$unsafe" >&2
    fi
  fi
}

while IFS= read -r binary; do
  verify_one "$binary"
done <"$SORTED_PATHS_FILE"

FFMPEG_PATH="$APP_PATH/Contents/app/ffmpeg/ffmpeg"
if [ -f "$FFMPEG_PATH" ]; then
  if codesign --verify --strict --verbose=2 "$FFMPEG_PATH" >/dev/null 2>&1 && ! is_adhoc "$FFMPEG_PATH"; then
    FFMPEG_STATUS="signed-valid"
  else
    FFMPEG_STATUS="invalid"
    record_error "Bundled FFmpeg signature failed strict verification."
  fi
fi

if codesign --verify --deep --strict --verbose=4 "$APP_PATH" >/dev/null 2>&1; then
  APP_STATUS="signed-valid"
else
  APP_STATUS="invalid"
  record_error "Final app bundle signature failed strict verification."
fi

if [ "$TEAM_STATUS" != "mismatch" ]; then
  if [ -n "$EXPECTED_TEAM" ] && [ -n "$FIRST_TEAM" ]; then
    TEAM_STATUS="consistent with expected Team Identifier"
  elif [ -n "$FIRST_TEAM" ]; then
    TEAM_STATUS="consistent"
  else
    TEAM_STATUS="unavailable"
  fi
fi

echo "macOS signature verification summary"
echo "  Mach-O files detected: $TOTAL"
echo "  Signed successfully:   $SIGNED"
echo "  FFmpeg:                $FFMPEG_STATUS"
echo "  App bundle:            $APP_STATUS"
echo "  Team ID consistency:   $TEAM_STATUS"
echo "  Unsafe dependencies:   $UNSAFE_DEPS"

if [ "$ERRORS" -gt 0 ] || [ "$TOTAL" -eq 0 ] || [ "$UNSAFE_DEPS" -gt 0 ] || [ "$FFMPEG_STATUS" != "signed-valid" ] || [ "$APP_STATUS" != "signed-valid" ]; then
  exit 1
fi
