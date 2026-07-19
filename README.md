# Memoria Vault

A local-first app to browse and rediscover exported Snapchat Memories.

Memoria Vault indexes compatible exported photos, videos, and overlays on your computer so you can
explore them by year, month, flashback date, and favorites without uploading your media elsewhere.

## Why

Snapchat Memories are personal. If you export them, you should be able to browse them easily without
renaming files, moving media around, or sending a private archive to a cloud service.

## Features

- Browse exported memories by year and month
- Rediscover memories through flashbacks
- Mark memories as favorites and find them later
- Back up and restore favorites locally
- Move through memories with previous/next buttons and arrow keys in the viewer
- Play and pause video memories with the Space bar
- Preview images and videos locally
- Display supported overlays without modifying original files
- Keep original files untouched in their existing folder
- macOS Apple Silicon release available
- Windows version in progress, with community contributions welcome

## Getting Started

Before using Memoria Vault, export your Snapchat data and unzip the downloaded archive.

Then select the folder that contains your exported memories.

1. Download Memoria Vault from [GitHub Releases](https://github.com/cnoupoue/memoriavault/releases).
2. Open the app.
3. Go to **Settings**.
4. Choose the unzipped export folder.
5. Start a scan.
6. Browse your memories locally.

If your export contains multiple folders such as `memories`, `memories 2`, and `memories 3`, select
their parent export folder.

## Platform Support

- macOS Apple Silicon: available
- Windows: in progress

## Privacy

- Your original exported files stay where they are.
- Memoria Vault stores local metadata, indexes, and thumbnails only on your computer.
- Your media is not uploaded, synced, or shared by the app.
- Local paths, SQLite databases, thumbnails, and personal exports should never be committed to Git.

## Development

### Prerequisites

- Java 21
- Node.js 22
- npm
- Maven
- Make
- FFmpeg, for video thumbnails during development

### Commands

```bash
# Install dependencies
make install

# Start the local development app
make dev

# Run formatting checks, linting, tests, and builds
make verify

# Build the standalone production JAR
make build-production

# Run the production JAR locally
make run-production

# Build an unsigned local macOS package
make package-macos

# Remove generated packaging artifacts
make clean-packaging
```

After `make dev`, open `http://localhost:5173` in your browser.

For macOS release signing, notarization, DMG packaging, entitlements, and Apple-specific release
details, see [packaging/macos/README.md](packaging/macos/README.md).

## Documentation

- [Technical contribution guide](docs/technical-contribution-guide.md)
- [Contributing guide](docs/CONTRIBUTING.md)
- [Security policy](docs/SECURITY.md)
- [macOS release documentation](packaging/macos/README.md)
- [Windows packaging notes](packaging/windows/README.md)

## Support

Memoria Vault is built as an open-source project.

If it helps you rediscover meaningful memories, consider supporting its development:

[Buy me a coffee](https://buymeacoffee.com/cnoupoue)

You can also support the project by starring the repository, opening issues, or contributing code,
tests, and documentation.

## Disclaimer

Memoria Vault is an independent project and is not affiliated with Snap Inc. or Snapchat.

Compatibility references are descriptive only.

## License

Memoria Vault is licensed under the [MIT License](LICENSE).
