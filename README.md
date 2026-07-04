# Memoria Vault

> A private local archive viewer for compatible exported memories.

Memoria Vault helps you browse compatible exported memories locally without manually navigating thousands of files on an external drive.

It indexes your exported photos, videos, and overlays locally, then provides a faster way to explore them by year, month, and flashback date.

## Features

* Browse Memories by year and month
* View photos and videos in a full-screen viewer
* Display supported overlays without modifying original files
* Generate cached thumbnails for images and videos
* Rediscover Memories through “On this day” flashbacks
* Manage multiple export sources from the Settings page
* Scan large folders in the background with live progress
* Keep original files on your USB drive or local folder
* Store only metadata, indexes, and thumbnail cache locally

## Privacy first

Memoria Vault is designed to be local-first.

* Your original exported files stay where they are.
* Your media is not uploaded to a cloud service.
* Choosing a folder only gives Memoria Vault the local folder path; it does not upload, copy, move, or duplicate your media.
* The application runs on your computer by default.
* Local paths, SQLite databases, thumbnails, and personal exports should never be committed to Git.

## Independence disclaimer

This application is an independent, open-source local tool and is not affiliated, associated, authorized, endorsed by, or in any way officially connected with Snap Inc. or Snapchat.

Memoria Vault can read supported local export structures, including compatible Snapchat export formats. All compatibility references are descriptive only.

Maintainer note: this rebrand reduces perceived affiliation risk, but it does not replace trademark clearance or legal advice. Public launch should include an independent trademark search for the final app name and legal review. Compatibility references must remain descriptive and non-prominent, and no Snap Inc. or Snapchat logos, branding, or visual identity should be used.

## Beta issue reports

When reporting an issue, use **Settings → Copy diagnostic information** and include the copied report. Do not share personal archive paths, filenames, photos, or videos.

## Getting started

### Requirements

* Java 21 or later
* Node.js 22 or later
* npm
* FFmpeg for video thumbnails during development
* Git
* Make

On macOS, you can install FFmpeg with:

```bash
brew install ffmpeg
```

Verify the installation:

```bash
ffmpeg -version
```

Video playback does not depend on FFmpeg. If FFmpeg is unavailable, Memoria Vault continues to browse and open original videos, but video preview thumbnails are shown with a fallback state. Development can use FFmpeg from the system `PATH` or an explicit absolute `memoriavault.ffmpeg.path` value. A plain command name such as `ffmpeg` is treated only as a system `PATH` fallback, so packaged macOS builds still prefer the bundled FFmpeg binary.

### Clone and install

```bash
git clone https://github.com/cnoupoue/memoriavault.git
cd memoriavault
make install
```

### Start the application

```bash
make dev
```

This starts:

```text
Backend:  http://127.0.0.1:8080
Frontend: http://localhost:5173
```

Open the frontend in your browser:

```text
http://localhost:5173
```

### Run services separately

Start only the backend:

```bash
make run-backend
```

Start only the frontend:

```bash
make run-frontend
```

Check that the backend is running:

```bash
make health
```

### Add an exported archive

1. Open **Settings** in the application.
2. Click **Choose exported archive folder**.
3. Select the parent folder containing your exported archive data, such as:

```text
exported-archive/
├── memories/
├── memories 2/
├── memories 3/
└── ...
```

4. Start a scan.
5. Browse your archive through the timeline.

Do not select an individual `memories` folder when your export contains multiple folders. Select the parent exported archive folder instead. Compatible Snapchat export folder structures are supported descriptively.

The folder picker is local to the machine running Memoria Vault. It indexes files in place and never uploads or copies your personal media. If native folder selection is unavailable, for example in a headless environment, enter the folder path manually in Settings.

## Local commands

Memoria Vault provides a `Makefile` for common development tasks.

```bash
# Show every available command
make help

# Install root tooling and frontend dependencies
make install

# Start backend and frontend together
make dev

# Start only the Spring Boot backend
make run-backend

# Start only the React frontend
make run-frontend

# Format Java and frontend code automatically
make format

# Check formatting without changing files
make format-check

# Run frontend linting
make lint

# Automatically fix lint issues where possible
make lint-fix

# Run backend and frontend tests
make test

# Build separate backend and frontend development artifacts
make build

# Build the standalone production JAR
make build-production

# Create a verified local release tag
make tag VERSION=0.1.0

# Push an existing release tag and trigger the release workflow
make push-tag VERSION=0.1.0

# Run the standalone production JAR
make run-production

# Run all formatting checks, linting, tests, and builds
make verify

# Remove generated build artifacts
make clean
```

## Development

Run all local quality checks before opening a pull request:

```bash
make verify
```

This validates:

* Java formatting with Spotless and Google Java Format
* Frontend formatting with Prettier
* ESLint checks
* Backend tests
* Frontend tests
* Backend build
* Frontend production build

## Production build

Build the standalone production JAR:

```bash
make build-production
```

Run it locally:

```bash
make run-production
```

Production mode embeds the compiled React frontend in the Spring Boot JAR, serves the app from `http://127.0.0.1:8080`, and does not require Vite or Node.js at runtime. `make run-production` starts Memoria Vault locally and opens the default browser automatically. If Memoria Vault is already running, the existing local app is opened instead.

This JAR is the foundation for future macOS packaging with `jpackage`.

## macOS packaging

Maintainers can build the first Apple Silicon package with:

```bash
make package-macos
```

This creates:

```text
dist/app/Memoria Vault.app
dist/installers/Memoria-Vault-<version>-macos-arm64.dmg
```

The package includes a bundled Java runtime, the production JAR with the React frontend embedded, and a verified macOS arm64 FFmpeg binary when present at `packaging/macos/ffmpeg/arm64/ffmpeg`. FFmpeg is used only for video thumbnail generation; original videos still open if preview generation is unavailable.

Before public distribution, maintainers must verify the FFmpeg source, architecture, checksum, license configuration, and third-party notices. The packaging flow intentionally does not download FFmpeg automatically:

```bash
make check-bundled-ffmpeg
```

The package is intended for macOS Apple Silicon, and it is unsigned and not notarized yet. macOS may show a security warning for unsigned local builds. Code signing and notarization are planned future release steps.

The macOS package identifier is `be.cnoupoue.memoriavault`. Maintainers should treat upgrades from pre-release builds as a compatibility check before distribution.

## Creating a macOS release

Maintainers can create a macOS Apple Silicon release after all checks and manual packaging tests pass.

```bash
make tag VERSION=0.1.0
git push origin v0.1.0
```

`make tag` validates the version format, requires a clean `main` worktree synchronized with `origin/main`, checks for an existing local or remote tag, verifies that the Maven project version matches the release version or the matching `-SNAPSHOT` development version, runs `make verify`, and creates an annotated local tag.

Pushing the tag triggers GitHub Actions, which builds the macOS Apple Silicon DMG and creates a GitHub Release automatically. The release page includes:

```text
Memoria-Vault-0.1.0-macos-arm64.dmg
Memoria-Vault-0.1.0-macos-arm64.dmg.sha256
```

The release workflow requires:

* A valid bundled FFmpeg binary and complete provenance metadata.
* macOS arm64 packaging compatibility on the GitHub Actions runner.
* Java 21 and Node.js 22 dependency installation through the lockfiles.

The package is Apple Silicon only. Signing and notarization are not included yet, so macOS may show a security warning.

To publish with the optional helper instead of typing the push command directly:

```bash
make push-tag VERSION=0.1.0
```

This only pushes an existing local tag; it does not create one.

### Testing release tag creation without publishing

Do not create or push a real release tag during implementation tests. For a local dry run after the working tree is clean, use a version that matches the Maven project version and delete the local tag afterward:

```bash
make format
make verify
make tag VERSION=0.1.0
git tag -d v0.1.0
```

Only `git push origin v0.1.0` publishes the tag and starts the GitHub Release workflow.

Format the complete project:

```bash
make format
```

Automatically fix supported lint and formatting issues:

```bash
npm run fix
```

The backend is built with Java 21 compatibility. If you have several JDKs installed, ensure that `JAVA_HOME` points to Java 21 or a compatible later version.

For technical architecture, development workflow, testing, and contribution guidance, see:

* [Technical contribution guide](docs/technical-contribution-guide.md)
* [Contributing guide](docs/CONTRIBUTING.md)

## Security

Please report security issues privately.

See [SECURITY.md](docs/SECURITY.md) for the security policy and reporting guidance.

## Support the project

Memoria Vault is built as an open-source project.

If it helps you rediscover meaningful memories, consider supporting its development:

[☕ Buy me a coffee](https://buymeacoffee.com/cnoupoue)

You can also support the project by:

* Starring the repository
* Sharing it with people who use compatible local archive exports
* Opening an issue for bugs or ideas
* Contributing code, tests, or documentation

## Roadmap

Planned improvements include:

* Favorites and collections
* Advanced filters for photos, videos, overlays, and dates
* Previous and next navigation in the viewer
* Better source availability detection
* Thumbnail cache invalidation
* Backup and restore for the local index
* Desktop packaging for macOS and Windows
* Optional local network deployment with authentication

## License

Memoria Vault is licensed under the [MIT License](LICENSE).
