# macOS packaging

macOS packaging is currently implemented for Apple Silicon (`arm64`).

Current macOS-specific assets:

- `icon/MemoriaVault.icns`
- `scripts/create-icns.mjs`
- `ffmpeg/arm64/ffmpeg`

The generated jpackage app image is expected to contain:

- `Memoria Vault.app/Contents/MacOS/Memoria Vault`
- `Memoria Vault.app/Contents/app/<application jar>`
- `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg`
- `Memoria Vault.app/Contents/runtime`

## Unsigned development packaging

`make package-macos` is for local development only. It builds the production JAR, creates an
unsigned app image, and creates an unsigned DMG. That DMG is useful for smoke testing packaging, but
it is not a release artifact and is not uploaded by CI.

Release DMG generation must happen after app signing. Do not rebuild or overwrite
`dist/app/Memoria Vault.app` after `make sign-macos-app`.

## Signed and notarized release packaging

The release pipeline is intentionally split into deterministic stages:

```text
1. Build production JAR
2. Build unsigned macOS app image
3. Sign native SQLite libraries embedded inside `sqlite-jdbc-*.jar`
4. Discover embedded Mach-O binaries
5. Sign nested executable code from inside out
6. Sign bundled FFmpeg explicitly
7. Sign Java runtime native executables/libraries where needed
8. Sign the final .app bundle
9. Verify Developer ID authority, Team ID, timestamp, Hardened Runtime, and non-ad-hoc metadata
10. Create DMG from the already signed .app
11. Sign the DMG
12. Mount the DMG and verify the app inside with the same strict metadata checks
13. Submit DMG to Apple notarization
14. Wait for final notarization status
15. Retrieve Apple log automatically when notarization fails
16. Staple the notarization ticket to the DMG
17. Validate stapling and Gatekeeper assessment
18. Generate SHA-256 checksum
19. Publish the notarized DMG and checksum to GitHub Release
```

The Makefile targets are:

```text
package-macos-app
postprocess-macos-sqlite-native-libs
sign-macos-app
verify-macos-signatures
package-macos-dmg-from-signed-app
sign-macos-dmg
verify-macos-dmg-signatures
notarize-macos-dmg
staple-macos-dmg
verify-macos-notarization
package-macos-release
```

`package-macos-dmg-from-signed-app` verifies the existing app signature before creating the DMG and
does not invoke `package-macos-app`. Verification inspects `codesign -dv --verbose=4` metadata, not
only `codesign --verify`, and fails on missing Developer ID authority, Team ID mismatch, missing
secure timestamp, missing Hardened Runtime, or ad-hoc signatures.

Apple notarization scans native libraries embedded inside nested dependency JARs. The release path
therefore signs `org/sqlite/native/Mac/*/libsqlitejdbc.dylib` inside the packaged
`sqlite-jdbc-*.jar`, rebuilds the dependency JAR, replaces it inside the packaged application JAR,
and verifies the modified archive before signing the outer `.app`.

Required GitHub secrets:

```text
APPLE_DEVELOPER_ID_APPLICATION
APPLE_CERTIFICATE_P12_BASE64
APPLE_CERTIFICATE_PASSWORD
APPLE_ID
APPLE_TEAM_ID
APPLE_APP_SPECIFIC_PASSWORD
```

The signing identity is read from `APPLE_DEVELOPER_ID_APPLICATION`. It is not hardcoded in scripts or
workflow files.

The `APPLE_CERTIFICATE_P12_BASE64` secret must be a base64-encoded `.p12` export that contains both:

```text
Developer ID Application certificate
+
matching private key
```

Exporting only the `.cer` certificate is insufficient for GitHub Actions signing because `codesign`
must have access to the private key. Before exporting the `.p12`, verify the local Mac can see the
Developer ID signing identity:

```bash
security find-identity -v -p codesigning
```

The expected Developer ID identity should be listed. If it is not listed locally, fix the certificate
and private key in Keychain Access before creating the `.p12` secret.

## Local signing test

Run this before pushing a release tag when the Developer ID Application certificate is available
locally:

```bash
make clean-packaging
make package-macos-app
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
APPLE_TEAM_ID="TEAMID" \
  make postprocess-macos-sqlite-native-libs
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make sign-macos-app
make verify-macos-signatures
make package-macos-dmg-from-signed-app
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make sign-macos-dmg
```

Local notarization is optional. Supply credentials only through environment variables or a
Keychain-backed notarytool profile:

```bash
APPLE_ID="account@example.invalid" \
APPLE_TEAM_ID="TEAMID" \
APPLE_APP_SPECIFIC_PASSWORD="xxx" \
make notarize-macos-dmg
make staple-macos-dmg
make verify-macos-notarization
```

Equivalent local command shape:

```bash
xcrun notarytool submit "$DMG" \
  --apple-id "$APPLE_ID" \
  --team-id "$APPLE_TEAM_ID" \
  --password "xxx" \
  --wait
```

## Signing readiness

Run the non-blocking inspection after building the app image:

```bash
make package-macos-app
make inspect-macos-signing-readiness
```

Inspection mode:

- requires `dist/app/Memoria Vault.app`;
- discovers Mach-O executables, `.dylib` files, framework binaries, Java runtime binaries, and other
  native binaries inside the bundle;
- labels the jpackage launcher, bundled FFmpeg, Java runtime files, native libraries, frameworks, and
  other Mach-O executables;
- reports whether each file currently has a valid code signature;
- validates `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg` explicitly;
- warns when unsigned binaries or ad-hoc signatures are present, but exits successfully for normal
  development builds unless a packaging problem such as missing FFmpeg is found.

Run strict verification only for signed release candidates:

```bash
make verify-macos-signatures
```

Strict mode:

- fails if any detected Mach-O executable or native library is unsigned;
- fails if any signature is invalid;
- fails if any nested binary still uses an ad-hoc signature instead of a Developer ID signature;
- requires `Authority` to contain `APPLE_DEVELOPER_ID_APPLICATION`;
- requires `TeamIdentifier` to match `APPLE_TEAM_ID`, or the Team ID parsed from the signing identity;
- requires a secure timestamp;
- requires the Hardened Runtime `runtime` flag for Mach-O code;
- verifies bundled FFmpeg with `codesign --verify --strict`;
- verifies native SQLite libraries embedded inside the packaged `sqlite-jdbc-*.jar`;
- rejects dynamic dependencies that point to Homebrew, user-local, temporary, or mounted-volume
  paths;
- verifies the final app bundle with `codesign --verify --deep --strict --verbose=4`.

`verify-macos-signatures` is expected to fail until every nested binary and the final app bundle are
signed with a Developer ID Application certificate, secure timestamp, and Hardened Runtime metadata.
Its normal CI summary does not print absolute local paths.

Optional identity check:

```bash
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Example Name (TEAMID)" \
  make inspect-macos-signing-readiness
```

The inspection checks Team Identifier or signing authority metadata where practical. It does not
require the variable for unsigned local development and does not print certificate passwords, private
keys, or keychain contents.

## FFmpeg validation

Bundled FFmpeg must be checked separately because it is an executable nested inside the jpackage
bundle and notarization requires nested executables to be signed before the final `.app` bundle.

The signing-readiness script verifies that:

- `Memoria Vault.app/Contents/app/ffmpeg/ffmpeg` exists;
- the file is executable;
- `file` and `lipo -info` report macOS `arm64`;
- `ffmpeg -version` succeeds;
- strict mode passes `codesign --verify --strict`;
- `otool -L` does not report Homebrew, user-local, temporary, or mounted-volume dependencies;
- FFmpeg links only to expected Apple system libraries/frameworks.

If FFmpeg is unsigned in development mode, the inspection prints:

```text
WARNING: Bundled FFmpeg is present and executable but is not yet signed.
```

## Notarization failure diagnostics

When Apple notarization is not accepted, `packaging/macos/scripts/notarize-dmg.sh` saves diagnostic
files under `dist/notarization/`:

- `submission-id.txt`
- `status.txt`
- `notarytool-submit.json`
- `apple-notarization-log.json`, when Apple returns one
- `signature-verification-summary.txt`, in GitHub Actions after signature verification

The workflow uploads `dist/notarization/` only when the job fails. The release asset upload step is
after notarization, stapling, final validation, and checksum generation, so no DMG is published when
notarization fails.

Future macOS Intel support should add a verified `x64` FFmpeg binary, update packaging checks,
and add a separate release workflow or matrix entry.
