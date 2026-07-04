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

Future macOS Intel support should add a verified `x64` FFmpeg binary, update packaging checks,
and add a separate release workflow or matrix entry.
