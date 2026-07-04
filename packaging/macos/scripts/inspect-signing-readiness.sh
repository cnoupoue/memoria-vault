#!/usr/bin/env bash
set -u

MODE="${1:-inspect}"
APP_PATH="${2:-dist/app/Memoria Vault.app}"

if [ "$MODE" != "inspect" ] && [ "$MODE" != "verify" ]; then
  echo "Usage: $0 inspect|verify [path/to/Memoria Vault.app]" >&2
  exit 2
fi

if [ "$(uname -s)" != "Darwin" ]; then
  echo "macOS signing readiness inspection requires macOS." >&2
  exit 1
fi

if [ ! -d "$APP_PATH" ]; then
  echo "Missing app bundle: $APP_PATH. Run 'make package-macos-app' first." >&2
  exit 1
fi

APP_NAME="$(basename "$APP_PATH" .app)"
FFMPEG_PATH="$APP_PATH/Contents/app/ffmpeg/ffmpeg"
STRICT=0
if [ "$MODE" = "verify" ]; then
  STRICT=1
fi

WARNINGS=0
ERRORS=0
TOTAL=0
SIGNED=0
UNSIGNED=0
INVALID=0
ADHOC=0
UNSAFE_DEPS=0
FFMPEG_STATUS="not checked"
FOUND_PATHS_FILE="$(mktemp "${TMPDIR:-/tmp}/memoriavault-mach-o.XXXXXX")"
FIRST_SIGNING_TEAM=""
FIRST_SIGNING_AUTHORITY=""
trap 'rm -f "$FOUND_PATHS_FILE"' EXIT

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required tool: $1" >&2
    exit 1
  fi
}

for tool in find file codesign otool lipo; do
  require_tool "$tool"
done

record_warning() {
  WARNINGS=$((WARNINGS + 1))
  echo "WARNING: $*"
}

record_error() {
  ERRORS=$((ERRORS + 1))
  echo "ERROR: $*"
}

is_macho() {
  file "$1" 2>/dev/null | grep -Eq 'Mach-O|universal binary'
}

classify_binary() {
  path="$1"
  desc="$(file "$path" 2>/dev/null)"
  case "$path" in
    "$APP_PATH/Contents/MacOS/$APP_NAME")
      printf '%s' "app launcher"
      ;;
    "$FFMPEG_PATH")
      printf '%s' "bundled FFmpeg"
      ;;
    *"/Contents/runtime/"*)
      if printf '%s\n' "$path" | grep -Eq '\.dylib$|\.jnilib$'; then
        printf '%s' "Java runtime native library"
      else
        printf '%s' "Java runtime binary"
      fi
      ;;
    *".framework/"*)
      printf '%s' "framework binary"
      ;;
    *.dylib|*.jnilib)
      printf '%s' "native .dylib"
      ;;
    *)
      if printf '%s\n' "$desc" | grep -Eqi 'executable'; then
        printf '%s' "other Mach-O executable"
      else
        printf '%s' "other Mach-O native binary"
      fi
      ;;
  esac
}

signature_summary() {
  path="$1"
  count_signature="${2:-1}"
  verify_output="$(codesign --verify --strict --verbose=2 "$path" 2>&1)"
  verify_status=$?
  if [ $verify_status -eq 0 ]; then
    detail="$(codesign -dv --verbose=4 "$path" 2>&1 || true)"
    if printf '%s\n' "$detail" | grep -Eq '^Signature=adhoc$|flags=.*adhoc'; then
      if [ "$count_signature" -eq 1 ]; then
        SIGNED=$((SIGNED + 1))
        ADHOC=$((ADHOC + 1))
      fi
      SIG_RESULT="signed-valid (adhoc)"
    else
      if [ "$count_signature" -eq 1 ]; then
        SIGNED=$((SIGNED + 1))
      fi
      SIG_RESULT="signed-valid"
    fi
    return 0
  fi

  if printf '%s\n' "$verify_output" | grep -Eqi 'code object is not signed|is not signed at all'; then
    if [ "$count_signature" -eq 1 ]; then
      UNSIGNED=$((UNSIGNED + 1))
    fi
    SIG_RESULT="unsigned"
    return 1
  fi

  if [ "$count_signature" -eq 1 ]; then
    INVALID=$((INVALID + 1))
  fi
  SIG_RESULT="invalid: $(printf '%s\n' "$verify_output" | tail -n 1)"
  return 2
}

identity_summary() {
  path="$1"
  detail="$(codesign -dv --verbose=4 "$path" 2>&1)"
  if [ $? -ne 0 ]; then
    printf '%s' "-"
    return 0
  fi

  team="$(printf '%s\n' "$detail" | sed -n 's/^TeamIdentifier=//p' | head -n 1)"
  authority="$(printf '%s\n' "$detail" | sed -n 's/^Authority=//p' | head -n 1)"
  if [ -n "$team" ] && [ -n "$authority" ]; then
    printf 'team=%s, authority=%s' "$team" "$authority"
  elif [ -n "$team" ]; then
    printf 'team=%s' "$team"
  elif [ -n "$authority" ]; then
    printf 'authority=%s' "$authority"
  else
    printf '%s' "signed, identity unavailable"
  fi
}

check_expected_identity() {
  path="$1"
  expected="${APPLE_DEVELOPER_ID_APPLICATION:-}"
  [ -n "$expected" ] || return 0

  detail="$(codesign -dv --verbose=4 "$path" 2>&1)" || return 0
  team="$(printf '%s\n' "$detail" | sed -n 's/^TeamIdentifier=//p' | head -n 1)"
  authority_lines="$(printf '%s\n' "$detail" | sed -n 's/^Authority=//p')"
  expected_team="$(printf '%s\n' "$expected" | sed -n 's/.*(\([^()]*\)).*/\1/p')"

  if [ -n "$expected_team" ] && [ -n "$team" ] && [ "$team" != "$expected_team" ]; then
    if [ "$STRICT" -eq 1 ]; then
      record_error "$path is signed by Team Identifier $team, expected $expected_team."
    else
      record_warning "$path is signed by Team Identifier $team, expected $expected_team."
    fi
  elif [ -z "$expected_team" ] && ! printf '%s\n' "$authority_lines" | grep -Fq "$expected"; then
    if [ "$STRICT" -eq 1 ]; then
      record_error "$path signing authority does not match APPLE_DEVELOPER_ID_APPLICATION."
    else
      record_warning "$path signing authority does not match APPLE_DEVELOPER_ID_APPLICATION."
    fi
  fi
}

check_identity_consistency() {
  path="$1"
  detail="$(codesign -dv --verbose=4 "$path" 2>&1)" || return 0
  if printf '%s\n' "$detail" | grep -Eq '^Signature=adhoc$|flags=.*adhoc'; then
    return 0
  fi

  team="$(printf '%s\n' "$detail" | sed -n 's/^TeamIdentifier=//p' | head -n 1)"
  authority="$(printf '%s\n' "$detail" | sed -n 's/^Authority=//p' | head -n 1)"

  if [ -n "$team" ] && [ "$team" != "not set" ]; then
    if [ -z "$FIRST_SIGNING_TEAM" ]; then
      FIRST_SIGNING_TEAM="$team"
    elif [ "$team" != "$FIRST_SIGNING_TEAM" ]; then
      if [ "$STRICT" -eq 1 ]; then
        record_error "$path is signed by Team Identifier $team, expected consistency with $FIRST_SIGNING_TEAM."
      else
        record_warning "$path is signed by Team Identifier $team, expected consistency with $FIRST_SIGNING_TEAM."
      fi
    fi
  elif [ -n "$authority" ]; then
    if [ -z "$FIRST_SIGNING_AUTHORITY" ]; then
      FIRST_SIGNING_AUTHORITY="$authority"
    elif [ "$authority" != "$FIRST_SIGNING_AUTHORITY" ]; then
      if [ "$STRICT" -eq 1 ]; then
        record_error "$path signing authority differs from other nested binaries."
      else
        record_warning "$path signing authority differs from other nested binaries."
      fi
    fi
  fi
}

dependency_lines() {
  path="$1"
  otool -L "$path" 2>/dev/null | sed '1d; s/^[[:space:]]*//; s/ (.*$//'
}

check_unsafe_dependencies() {
  path="$1"
  unsafe="$(dependency_lines "$path" | grep -E '^(/opt/homebrew/|/usr/local/Cellar/|/Users/|/private/var/|/Volumes/)' || true)"
  if [ -n "$unsafe" ]; then
    UNSAFE_DEPS=$((UNSAFE_DEPS + 1))
    if [ "$STRICT" -eq 1 ]; then
      record_error "$path has unsafe dynamic dependencies:"
    else
      record_warning "$path has unsafe dynamic dependencies:"
    fi
    printf '%s\n' "$unsafe" | sed 's/^/  /'
  fi
}

check_ffmpeg_dependencies() {
  unsafe="$(dependency_lines "$FFMPEG_PATH" | grep -Ev '^(/usr/lib/|/System/Library/)' || true)"
  if [ -n "$unsafe" ]; then
    if [ "$STRICT" -eq 1 ]; then
      record_error "Bundled FFmpeg depends on non-Apple dynamic libraries:"
    else
      record_warning "Bundled FFmpeg depends on non-Apple dynamic libraries:"
    fi
    printf '%s\n' "$unsafe" | sed 's/^/  /'
  fi
}

check_ffmpeg() {
  echo ""
  echo "Bundled FFmpeg:"
  if [ ! -f "$FFMPEG_PATH" ]; then
    record_error "Missing bundled FFmpeg: $FFMPEG_PATH"
    FFMPEG_STATUS="missing"
    return
  fi
  if [ ! -x "$FFMPEG_PATH" ]; then
    record_error "Bundled FFmpeg is not executable: $FFMPEG_PATH"
    FFMPEG_STATUS="not executable"
  fi
  if ! is_macho "$FFMPEG_PATH"; then
    record_error "Bundled FFmpeg is not a Mach-O binary: $FFMPEG_PATH"
    FFMPEG_STATUS="not Mach-O"
  fi
  if ! file "$FFMPEG_PATH" | grep -Eq 'arm64'; then
    record_error "Bundled FFmpeg must support macOS arm64: $FFMPEG_PATH"
    FFMPEG_STATUS="missing arm64"
  fi
  if ! lipo -info "$FFMPEG_PATH" 2>/dev/null | grep -Eq 'arm64'; then
    record_error "Bundled FFmpeg lipo metadata does not report arm64: $FFMPEG_PATH"
    FFMPEG_STATUS="missing arm64"
  fi
  if ! "$FFMPEG_PATH" -version >/dev/null 2>&1; then
    record_error "Bundled FFmpeg failed validation: $FFMPEG_PATH -version"
    FFMPEG_STATUS="version failed"
  fi

  signature_summary "$FFMPEG_PATH" 0
  sig="$SIG_RESULT"
  printf '  path: %s\n' "$FFMPEG_PATH"
  printf '  signature: %s\n' "$sig"
  if [ "$sig" = "unsigned" ]; then
    if [ "$STRICT" -eq 1 ]; then
      record_error "Bundled FFmpeg is unsigned."
    else
      record_warning "Bundled FFmpeg is present and executable but is not yet signed."
    fi
  elif [ "$STRICT" -eq 1 ] && printf '%s\n' "$sig" | grep -q 'adhoc'; then
    record_error "Bundled FFmpeg uses an ad-hoc signature and must be signed with Developer ID for release."
  elif printf '%s\n' "$sig" | grep -q '^invalid:'; then
    record_error "Bundled FFmpeg signature is invalid."
  fi

  check_unsafe_dependencies "$FFMPEG_PATH"
  check_ffmpeg_dependencies
  check_expected_identity "$FFMPEG_PATH"

  if [ "$FFMPEG_STATUS" = "not checked" ]; then
    FFMPEG_STATUS="ready for signing inspection"
  fi
}

echo "Inspecting macOS signing readiness"
echo "  mode: $MODE"
echo "  app:  $APP_PATH"
if [ -n "${APPLE_DEVELOPER_ID_APPLICATION:-}" ]; then
  echo "  expected signing identity: configured"
fi

find "$APP_PATH" -type f -print0 | while IFS= read -r -d '' candidate; do
  if is_macho "$candidate"; then
    printf '%s\0' "$candidate"
  fi
done >"$FOUND_PATHS_FILE"

echo ""
echo "Discovered Mach-O binaries:"
if [ ! -s "$FOUND_PATHS_FILE" ]; then
  record_warning "No Mach-O binaries were found inside $APP_PATH."
fi

while IFS= read -r -d '' binary; do
  TOTAL=$((TOTAL + 1))
  kind="$(classify_binary "$binary")"
  signature_summary "$binary"
  sig="$SIG_RESULT"
  identity="$(identity_summary "$binary")"
  rel="${binary#"$APP_PATH/"}"

  printf '%03d. [%s] %s\n' "$TOTAL" "$kind" "$rel"
  printf '     signature: %s\n' "$sig"
  printf '     identity:  %s\n' "$identity"

  if [ "$STRICT" -eq 1 ] && [ "$sig" = "unsigned" ]; then
    record_error "$binary is unsigned."
  elif [ "$STRICT" -eq 1 ] && printf '%s\n' "$sig" | grep -q 'adhoc'; then
    record_error "$binary uses an ad-hoc signature and must be signed with Developer ID for release."
  elif printf '%s\n' "$sig" | grep -q '^invalid:'; then
    record_error "$binary signature is invalid."
  elif [ "$sig" = "unsigned" ]; then
    record_warning "$binary is unsigned and will need signing before notarization."
  fi

  check_unsafe_dependencies "$binary"
  check_expected_identity "$binary"
  check_identity_consistency "$binary"
done <"$FOUND_PATHS_FILE"

check_ffmpeg

if [ "$STRICT" -eq 1 ]; then
  echo ""
  echo "Final app bundle signature:"
  if codesign --verify --deep --strict --verbose=4 "$APP_PATH"; then
    echo "  app bundle signature: signed-valid"
  else
    record_error "Final app bundle signature verification failed."
  fi
fi

if [ "$STRICT" -eq 0 ] && [ "$ADHOC" -gt 0 ]; then
  record_warning "$ADHOC Mach-O binaries use ad-hoc signatures. They are acceptable for local development but must be signed with Developer ID before notarization."
fi

echo ""
echo "Summary:"
echo "  Mach-O binaries: $TOTAL"
echo "  Signed valid:    $SIGNED"
echo "  Ad-hoc signed:   $ADHOC"
echo "  Unsigned:        $UNSIGNED"
echo "  Invalid:         $INVALID"
echo "  Unsafe deps:     $UNSAFE_DEPS"
echo "  Warnings:        $WARNINGS"
echo "  Errors:          $ERRORS"
echo "  FFmpeg:          $FFMPEG_STATUS"

if [ "$STRICT" -eq 1 ] && { [ "$ERRORS" -gt 0 ] || [ "$UNSIGNED" -gt 0 ] || [ "$INVALID" -gt 0 ] || [ "$ADHOC" -gt 0 ] || [ "$UNSAFE_DEPS" -gt 0 ]; }; then
  echo "Strict macOS signature verification failed."
  exit 1
fi

if [ "$STRICT" -eq 0 ] && [ "$ERRORS" -gt 0 ]; then
  echo "macOS signing readiness inspection found blocking packaging issues."
  exit 1
fi

echo "macOS signing readiness inspection completed."
