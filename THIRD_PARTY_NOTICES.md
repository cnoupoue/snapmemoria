# Third-party notices

## FFmpeg

SnapMemoria can bundle FFmpeg in packaged macOS Apple Silicon releases for video thumbnail generation only. Original video playback does not depend on FFmpeg.

Before distributing a build that includes FFmpeg, maintainers must complete the packaging manifest in `packaging/macos/ffmpeg/README.md` with the exact version, source, checksum, architecture, and license configuration.

FFmpeg is licensed under LGPL. SnapMemoria release artifacts must include notices and license text that match the bundled FFmpeg build.

- Version: 6.1.6
- Architecture: macOS Apple Silicon (arm64)
- Binary SHA-256: [ffmpeg.sha256](packaging/macos/ffmpeg/arm64/ffmpeg.sha256)
- Source archive: https://ffmpeg.org/releases/ffmpeg-6.1.6.tar.xz
- License: intended LGPL-compatible build
- Build metadata: `packaging/macos/ffmpeg/arm64/BUILD_INFO.md`
