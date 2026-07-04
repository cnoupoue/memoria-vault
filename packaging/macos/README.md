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

## Signing readiness

Developer ID signing and notarization are not implemented yet. The current packaging checks prepare
for that work by identifying every embedded Mach-O binary that will need an individual signature.

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
- checks signed nested binaries against `APPLE_DEVELOPER_ID_APPLICATION` when that optional
  environment variable is set;
- verifies bundled FFmpeg with `codesign --verify --strict`;
- rejects dynamic dependencies that point to Homebrew, user-local, temporary, or mounted-volume
  paths;
- verifies the final app bundle with `codesign --verify --deep --strict --verbose=4`.

`verify-macos-signatures` is expected to fail until every nested binary and the final app bundle are
signed with a Developer ID Application certificate.

Optional identity check:

```bash
APPLE_DEVELOPER_ID_APPLICATION="Developer ID Application: Cameron Noupoue (TEAMID)" \
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

## Future signing checklist

1. Build the app.
2. Sign every nested executable and native library.
3. Sign FFmpeg.
4. Sign the final app bundle.
5. Run `verify-macos-signatures`.
6. Build/sign the DMG.
7. Submit the DMG to Apple notarization.
8. Staple and validate the notarization ticket.

Future macOS Intel support should add a verified `x64` FFmpeg binary, update packaging checks,
and add a separate release workflow or matrix entry.
