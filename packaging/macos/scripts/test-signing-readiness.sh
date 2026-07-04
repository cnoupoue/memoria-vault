#!/usr/bin/env bash
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SCRIPT="$SCRIPT_DIR/inspect-signing-readiness.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-signing-tests.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

STUB_DIR="$TMP_DIR/bin"
mkdir -p "$STUB_DIR"

cat >"$STUB_DIR/uname" <<'STUB'
#!/usr/bin/env bash
if [ "${1:-}" = "-s" ]; then
  echo Darwin
else
  echo arm64
fi
STUB

cat >"$STUB_DIR/file" <<'STUB'
#!/usr/bin/env bash
path="${*: -1}"
case "$path" in
  *"/Contents/MacOS/Memoria Vault"|*"/Contents/app/ffmpeg/ffmpeg"|*".dylib")
    echo "$path: Mach-O 64-bit executable arm64"
    ;;
  *)
    echo "$path: ASCII text"
    ;;
esac
STUB

cat >"$STUB_DIR/codesign" <<'STUB'
#!/usr/bin/env bash
if [ "${1:-}" = "-dv" ]; then
  echo "code object is not signed at all" >&2
  exit 1
fi

target="${*: -1}"
if printf '%s\n' "${STUB_SIGNED_PATHS:-}" | grep -Fxq "$target"; then
  exit 0
fi

echo "$target: code object is not signed at all" >&2
exit 1
STUB

cat >"$STUB_DIR/otool" <<'STUB'
#!/usr/bin/env bash
target="${*: -1}"
echo "$target:"
echo "	/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1.0.0)"
if [ "${STUB_UNSAFE_DEPS:-0}" = "1" ]; then
  echo "	/opt/homebrew/Cellar/example/1.0/lib/libexample.dylib (compatibility version 1.0.0, current version 1.0.0)"
fi
STUB

cat >"$STUB_DIR/lipo" <<'STUB'
#!/usr/bin/env bash
echo "Non-fat file: ${*: -1} is architecture: arm64"
STUB

chmod +x "$STUB_DIR/uname" "$STUB_DIR/file" "$STUB_DIR/codesign" "$STUB_DIR/otool" "$STUB_DIR/lipo"

make_fixture() {
  fixture="$1"
  mkdir -p "$fixture/Memoria Vault.app/Contents/MacOS"
  mkdir -p "$fixture/Memoria Vault.app/Contents/app/ffmpeg"
  mkdir -p "$fixture/Memoria Vault.app/Contents/runtime/lib"

  printf '#!/usr/bin/env bash\necho launcher\n' >"$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault"
  printf '#!/usr/bin/env bash\nif [ "${1:-}" = "-version" ]; then echo "ffmpeg test"; exit 0; fi\nexit 0\n' >"$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
  printf 'native\n' >"$fixture/Memoria Vault.app/Contents/runtime/lib/libjava.dylib"
  chmod +x "$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault"
  chmod +x "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
}

run_script() {
  PATH="$STUB_DIR:$PATH" "$SCRIPT" "$@"
}

assert_contains() {
  haystack="$1"
  needle="$2"
  if ! printf '%s\n' "$haystack" | grep -Fq "$needle"; then
    echo "Expected output to contain: $needle" >&2
    echo "$haystack" >&2
    exit 1
  fi
}

fixture="$TMP_DIR/dev"
make_fixture "$fixture"
output="$(run_script inspect "$fixture/Memoria Vault.app")"
assert_contains "$output" "[bundled FFmpeg] Contents/app/ffmpeg/ffmpeg"
assert_contains "$output" "WARNING: Bundled FFmpeg is present and executable but is not yet signed."
assert_contains "$output" "macOS signing readiness inspection completed."

fixture="$TMP_DIR/strict"
make_fixture "$fixture"
set +e
output="$(run_script verify "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "Expected strict verification to reject unsigned binaries." >&2
  echo "$output" >&2
  exit 1
fi
assert_contains "$output" "Strict macOS signature verification failed."
assert_contains "$output" "is unsigned."

fixture="$TMP_DIR/unsafe"
make_fixture "$fixture"
output="$(STUB_UNSAFE_DEPS=1 run_script inspect "$fixture/Memoria Vault.app")"
assert_contains "$output" "/opt/homebrew/Cellar/example/1.0/lib/libexample.dylib"
assert_contains "$output" "WARNING:"

fixture="$TMP_DIR/missing-ffmpeg"
make_fixture "$fixture"
rm "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
set +e
output="$(run_script inspect "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
if [ "$status" -eq 0 ]; then
  echo "Expected inspection to fail clearly when bundled FFmpeg is missing." >&2
  echo "$output" >&2
  exit 1
fi
assert_contains "$output" "Missing bundled FFmpeg"

echo "macOS signing-readiness script tests passed."
