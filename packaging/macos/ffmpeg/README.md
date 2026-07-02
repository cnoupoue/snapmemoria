# Bundled FFmpeg for macOS

SnapMemoria can include a verified FFmpeg executable in the macOS Apple Silicon app bundle for video thumbnail generation.

Expected binary location:

```text
packaging/macos/ffmpeg/arm64/ffmpeg
```

Maintainers must verify and document the binary before packaging a public release:

```text
FFmpeg version: TODO
Architecture: macOS arm64
Source URL: TODO
Build URL or reproducible build notes: TODO
SHA-256 checksum: TODO
License: LGPL/GPL configuration must be verified before distribution
```

Do not download or add an arbitrary binary. Public distribution requires a trusted source, architecture verification, checksum verification, and matching third-party notices.

After adding a verified binary:

```bash
chmod +x packaging/macos/ffmpeg/arm64/ffmpeg
make check-bundled-ffmpeg
```
