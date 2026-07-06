#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/memoriavault-release-tests.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

STUB_DIR="$TMP_DIR/bin"
mkdir -p "$STUB_DIR"

write_stub() {
  path="$1"
  shift
  printf '%s\n' "$@" >"$STUB_DIR/$path"
  chmod +x "$STUB_DIR/$path"
}

write_stub uname '#!/usr/bin/env bash' \
  'if [ "${1:-}" = "-m" ]; then echo arm64; else echo Darwin; fi'
write_stub file '#!/usr/bin/env bash' \
  'path="${*: -1}"' \
  'case "$path" in' \
  '  *"/Contents/MacOS/Memoria Vault"|*"/Contents/app/ffmpeg/ffmpeg"|*".dylib") echo "$path: Mach-O 64-bit executable arm64" ;;' \
  '  *) echo "$path: ASCII text" ;;' \
  'esac'
write_stub otool '#!/usr/bin/env bash' \
  'target="${*: -1}"' \
  'echo "$target:"' \
  'echo "	/usr/lib/libSystem.B.dylib (compatibility version 1.0.0, current version 1.0.0)"' \
  'if [ "${STUB_UNSAFE_DEPS:-0}" = "1" ]; then echo "	/opt/homebrew/Cellar/example/1.0/lib/libexample.dylib (compatibility version 1.0.0, current version 1.0.0)"; fi'
write_stub codesign '#!/usr/bin/env bash' \
  'target="${*: -1}"' \
  'if printf "%s\n" "$*" | grep -q -- "--sign"; then echo "$target" >>"${STUB_CODESIGN_SIGNED_LOG:?}"; exit 0; fi' \
  'if [ "${1:-}" = "-dv" ]; then echo "TeamIdentifier=ZK7G72LVAX" >&2; echo "Authority=Developer ID Application: Test (ZK7G72LVAX)" >&2; exit 0; fi' \
  'if [ "${STUB_ALL_SIGNED:-0}" = "1" ] || printf "%s\n" "${STUB_SIGNED_PATHS:-}" | grep -Fxq "$target"; then exit 0; fi' \
  'if [ -n "${STUB_CODESIGN_SIGNED_LOG:-}" ] && [ -f "${STUB_CODESIGN_SIGNED_LOG}" ] && grep -Fxq "$target" "${STUB_CODESIGN_SIGNED_LOG}"; then exit 0; fi' \
  'echo "$target: code object is not signed at all" >&2' \
  'exit 1'
write_stub xcrun '#!/usr/bin/env bash' \
  'tool="${1:-}"; shift || true' \
  'case "$tool" in' \
  '  notarytool)' \
  '    action="${1:-}"; shift || true' \
  '    if [ "$action" = "submit" ]; then printf "{\"id\":\"test-submission-id\",\"status\":\"Invalid\"}\n"; exit 1; fi' \
  '    if [ "$action" = "log" ]; then out="${*: -1}"; printf "{\"id\":\"test-submission-id\",\"status\":\"Invalid\",\"issues\":[]}\n" >"$out"; exit 0; fi' \
  '    ;;' \
  '  stapler) exit 0 ;;' \
  'esac' \
  'exit 0'
write_stub plutil '#!/usr/bin/env bash' \
  'key=""' \
  'while [ "$#" -gt 0 ]; do case "$1" in -extract) key="$2"; shift 2 ;; -o) shift 2 ;; *) file="$1"; shift ;; esac; done' \
  'if [ "$key" = "id" ]; then sed -n "s/.*\"id\":\"\\([^\"]*\\)\".*/\\1/p" "$file"; fi' \
  'if [ "$key" = "status" ]; then sed -n "s/.*\"status\":\"\\([^\"]*\\)\".*/\\1/p" "$file"; fi'
write_stub spctl '#!/usr/bin/env bash' 'exit 0'
write_stub jpackage '#!/usr/bin/env bash' \
  'if printf "%s\n" "$*" | grep -q -- "--type dmg"; then' \
  '  dest=""; name=""; version=""' \
  '  while [ "$#" -gt 0 ]; do case "$1" in --dest) dest="$2"; shift 2 ;; --name) name="$2"; shift 2 ;; --app-version) version="$2"; shift 2 ;; *) shift ;; esac; done' \
  '  mkdir -p "$dest"; : >"$dest/$name-$version.dmg"; exit 0' \
  'fi' \
  'exit 0'

make_fixture() {
  fixture="$1"
  mkdir -p "$fixture/Memoria Vault.app/Contents/MacOS"
  mkdir -p "$fixture/Memoria Vault.app/Contents/app/ffmpeg"
  mkdir -p "$fixture/Memoria Vault.app/Contents/runtime/lib"
  printf 'launcher\n' >"$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault"
  printf 'ffmpeg\n' >"$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
  printf 'native\n' >"$fixture/Memoria Vault.app/Contents/runtime/lib/libjava.dylib"
  chmod +x "$fixture/Memoria Vault.app/Contents/MacOS/Memoria Vault" "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg"
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

mtime() {
  stat -f %m "$1" 2>/dev/null || stat -c %Y "$1"
}

fixture="$TMP_DIR/sign"
make_fixture "$fixture"
STUB_CODESIGN_SIGNED_LOG="$TMP_DIR/signed.log" PATH="$STUB_DIR:$PATH" APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Test (ZK7G72LVAX)" "$SCRIPT_DIR/sign-app.sh" "$fixture/Memoria Vault.app" >/dev/null
grep -Fq "$fixture/Memoria Vault.app/Contents/app/ffmpeg/ffmpeg" "$TMP_DIR/signed.log" || { echo "Expected signing script to sign FFmpeg." >&2; exit 1; }

fixture="$TMP_DIR/verify-unsigned"
make_fixture "$fixture"
set +e
output="$(PATH="$STUB_DIR:$PATH" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected unsigned nested binary verification to fail." >&2; exit 1; }
assert_contains "$output" "Unsigned or invalid nested code"

fixture="$TMP_DIR/verify-unsafe"
make_fixture "$fixture"
set +e
output="$(STUB_ALL_SIGNED=1 STUB_UNSAFE_DEPS=1 PATH="$STUB_DIR:$PATH" "$SCRIPT_DIR/verify-signatures.sh" "$fixture/Memoria Vault.app" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected unsafe dependency verification to fail." >&2; exit 1; }
assert_contains "$output" "Unsafe external dependency detected"

fixture="$TMP_DIR/notary"
mkdir -p "$fixture"
: >"$fixture/test.dmg"
set +e
output="$(PATH="$STUB_DIR:$PATH" APPLE_ID="test@example.invalid" APPLE_TEAM_ID="ZK7G72LVAX" APPLE_APP_SPECIFIC_PASSWORD="xxx" MACOS_NOTARIZATION_ARTIFACT_DIR="$fixture/artifacts" "$SCRIPT_DIR/notarize-dmg.sh" submit "$fixture/test.dmg" 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected notarization failure path to fail." >&2; exit 1; }
test -f "$fixture/artifacts/apple-notarization-log.json" || { echo "Expected notarization failure path to save Apple log." >&2; exit 1; }
assert_contains "$output" "Apple notarization submission ID: test-submission-id"

MAKE_TMP="$TMP_DIR/make"
mkdir -p "$MAKE_TMP/dist/app"
make_fixture "$MAKE_TMP/dist/app"
set +e
output="$(PATH="$STUB_DIR:$PATH" make -f "$REPO_ROOT/Makefile" package-macos-dmg-from-signed-app DIST_DIR="$MAKE_TMP/dist" APP_VERSION=1.2.3 JPACKAGE_VERSION=1.2.3 2>&1)"
status=$?
set -e
[ "$status" -ne 0 ] || { echo "Expected DMG packaging from unsigned app to fail." >&2; exit 1; }
assert_contains "$output" "Refusing to create DMG because the app is not signed with valid release signatures"

before="$(mtime "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/MacOS/Memoria Vault")"
STUB_ALL_SIGNED=1 PATH="$STUB_DIR:$PATH" make -f "$REPO_ROOT/Makefile" package-macos-dmg-from-signed-app DIST_DIR="$MAKE_TMP/dist" APP_VERSION=1.2.3 JPACKAGE_VERSION=1.2.3 >/dev/null
after="$(mtime "$MAKE_TMP/dist/app/Memoria Vault.app/Contents/MacOS/Memoria Vault")"
[ "$before" = "$after" ] || { echo "DMG packaging modified the signed app." >&2; exit 1; }
test -f "$MAKE_TMP/dist/installers/Memoria-Vault-1.2.3-macos-arm64.dmg" || { echo "Expected DMG to be created from signed app." >&2; exit 1; }

help_output="$(make -f "$REPO_ROOT/Makefile" help)"
assert_contains "$help_output" "Signed release path"
assert_contains "$help_output" "package-macos-dmg-from-signed-app"

workflow="$REPO_ROOT/.github/workflows/release-macos-arm64.yml"
notarize_line="$(grep -n 'Notarize macOS DMG' "$workflow" | head -n 1 | cut -d: -f1)"
publish_line="$(grep -n 'Publish GitHub Release assets' "$workflow" | head -n 1 | cut -d: -f1)"
[ "$notarize_line" -lt "$publish_line" ] || { echo "Release workflow publishes before notarization." >&2; exit 1; }
if grep -Eq 'echo \$\{\{ secrets\.(APPLE_APP_SPECIFIC_PASSWORD|APPLE_CERTIFICATE_PASSWORD|APPLE_CERTIFICATE_P12_BASE64)' "$workflow"; then
  echo "Workflow echoes a sensitive secret." >&2
  exit 1
fi

echo "macOS release pipeline script tests passed."
