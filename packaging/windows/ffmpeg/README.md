Windows FFmpeg

Place verified FFmpeg builds here before packaging. Runtime video compatibility inspection requires
FFprobe next to FFmpeg.

Recommended structure:
- packaging/windows/ffmpeg/win-x64/ffmpeg.exe
- packaging/windows/ffmpeg/win-x64/ffprobe.exe
- packaging/windows/ffmpeg/win-arm64/ffmpeg.exe
- packaging/windows/ffmpeg/win-arm64/ffprobe.exe

Verify checksums and license compatibility before committing any binaries. Update `THIRD_PARTY_NOTICES.md` if you distribute FFmpeg.
