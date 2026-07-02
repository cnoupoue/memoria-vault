# Third-party notices

## FFmpeg

Memoria Vault can bundle FFmpeg in packaged macOS Apple Silicon releases for video thumbnail generation only. Original video playback does not depend on FFmpeg.

Before distributing a build that includes FFmpeg, maintainers must complete the packaging manifest in `packaging/macos/ffmpeg/README.md` with the exact version, source, checksum, architecture, and license configuration.

FFmpeg is licensed under LGPL. Memoria Vault release artifacts must include notices and license text that match the bundled FFmpeg build.

## Independence notice

This application is an independent, open-source local tool and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Snap Inc. or Snapchat.

- Version: 6.1.6
- Architecture: macOS Apple Silicon (arm64)
- Binary SHA-256: [ffmpeg.sha256](packaging/macos/ffmpeg/arm64/ffmpeg.sha256)
- Source archive: https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz
- License: intended LGPL-compatible build
- Build metadata: `packaging/macos/ffmpeg/arm64/BUILD_INFO.md`
