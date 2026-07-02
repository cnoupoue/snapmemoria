# Technical Contribution Guide

This document explains how SnapMemoria is structured, how to run it locally, and how to contribute safely.

## Project overview

SnapMemoria is a local-first application for browsing exported Snapchat Memories.

The project is split into two applications:

```text
snapmemoria/
├── src/                         # Spring Boot backend
├── frontend/                    # React + TypeScript frontend
├── docs/                        # Technical documentation
├── .github/workflows/           # GitHub Actions workflows
├── pom.xml                      # Maven configuration
├── package.json                 # Root tooling and Git hooks
└── README.md
```

The backend indexes exported Snapchat media files into SQLite. It stores metadata and local file paths only; original media remains in place on local storage or an external drive.

The frontend provides:

* Timeline navigation by year and month
* Memory gallery
* Full-screen image and video viewer
* Flashbacks
* Source management
* Scan progress display

## Architecture

```text
React frontend
    ↓
Vite development proxy
    ↓
Spring Boot REST API
    ↓
SQLite metadata index
    ↓
Configured Snapchat export folders
    ↓
Original files on USB drive or local storage
```

The frontend runs on:

```text
http://localhost:5173
```

The backend runs on:

```text
http://127.0.0.1:8080
```

During development, Vite proxies `/api` requests to Spring Boot.

## Local storage model

Original Snapchat Memories are never copied into the repository.

The source folder picker is a local desktop convenience. It returns only the folder path selected by the user through the native picker; it must not upload, copy, move, or duplicate personal media. Manual path entry remains available for advanced users and for headless environments where native folder selection is unavailable.

SnapMemoria stores local application data under:

```text
~/.snapmemoria/
├── data/
│   └── snapmemoria.db
└── cache/
    └── thumbnails/
```

This includes:

* SQLite metadata database
* Flyway migration history
* Source configuration
* Indexed Memory metadata
* Generated image and video thumbnails

Do not commit these files.

## Snapchat export format

SnapMemoria currently supports filenames such as:

```text
2019-10-05_493C7A65-6059-48C0-81F1-9A7D3E068856-main.jpg
2019-10-05_493C7A65-6059-48C0-81F1-9A7D3E068856-main.mp4
2019-10-05_493C7A65-6059-48C0-81F1-9A7D3E068856-overlay.png
```

The scanner extracts:

```text
Captured date
External Snapchat identifier
Media type
Main media file path
Optional overlay path
File size
Last modified timestamp
```

A source should point to the parent folder containing all exported subfolders:

```text
snapchat-memories/
├── memories/
├── memories 2/
├── memories 3/
└── ...
```

## Prerequisites

Install:

* Java 21
* Node.js 22 or later
* npm
* FFmpeg for development video thumbnails, optional when video previews are not needed
* Git

On macOS, FFmpeg can be installed with:

```bash
brew install ffmpeg
```

Verify the installation:

```bash
ffmpeg -version
```

FFmpeg is used only for video thumbnail generation. Original video playback works through the media streaming endpoint even when FFmpeg is unavailable. During development, SnapMemoria resolves FFmpeg from `snapmemoria.ffmpeg.path` first, then a packaged app bundle location if present, then the system `PATH`.

## Initial setup

Clone the repository:

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/snapmemoria.git
cd snapmemoria
```

Install root tooling dependencies:

```bash
npm install
```

Install frontend dependencies:

```bash
npm --prefix frontend install
```

Create the local database directory:

```bash
mkdir -p ~/.snapmemoria/data
```

## Running locally

### Backend

```bash
./mvnw spring-boot:run
```

Health check:

```bash
curl http://127.0.0.1:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

### Frontend

In a separate terminal:

```bash
npm --prefix frontend run dev
```

Open:

```text
http://localhost:5173
```

## Backend modules

The backend is organized by feature.

```text
be.cnoupoue.snapmemoria/
├── config/          # Async execution and application configuration
├── indexing/        # Scanner, scan jobs, progress tracking
├── memory/          # Indexed Memories, gallery, timeline, flashbacks
├── source/          # Configured source folders
├── streaming/       # Secure media streaming endpoints
└── thumbnail/       # Image and FFmpeg video thumbnail generation
```

### `source`

Manages configured parent folders containing Snapchat exports.

Important responsibilities:

* Add source folders
* Open a local native folder picker for source selection when desktop APIs are available
* List configured sources
* Remove sources
* Track scan status
* Keep source configuration even when a USB drive is disconnected

### `indexing`

Scans a source asynchronously.

The scan lifecycle is:

```text
POST /api/sources/{sourceId}/scan
    ↓
Create MemoryScanJob with RUNNING status
    ↓
Count files
    ↓
Index supported main media files
    ↓
Associate sibling overlays
    ↓
Persist progress periodically
    ↓
Mark job COMPLETED or FAILED
```

Only one scan worker runs at a time by design. Scanning multiple large USB sources simultaneously would reduce performance and increase I/O contention.

### `memory`

Provides gallery and timeline data.

Important endpoints include:

```text
GET /api/memories
GET /api/memories/{id}
GET /api/timeline/years
GET /api/timeline/years/{year}/months
GET /api/flashbacks/today
GET /api/flashbacks?date=YYYY-MM-DD
```

Pagination is required for gallery endpoints. Never return an entire archive in a single response.

### `streaming`

Serves original media through controlled endpoints:

```text
GET /api/memories/{id}/media
GET /api/memories/{id}/overlay
```

The backend retrieves file paths from SQLite and validates that they remain inside the configured source directory.

Never add an endpoint that accepts an arbitrary filesystem path from the client.

The folder picker endpoint is the exception for source setup: it accepts no path input, opens a local native folder chooser on the machine running SnapMemoria, and returns only the user-selected folder. In headless or unsupported environments, it returns a structured `FOLDER_PICKER_UNAVAILABLE` error and the frontend keeps manual path entry available.

### `thumbnail`

Generates thumbnails lazily.

* Images are resized using Java image APIs.
* Image overlays are applied to image thumbnails.
* Video thumbnails are generated with FFmpeg when available.
* Generated previews are stored in the local thumbnail cache.

Original media files must never be modified.

If FFmpeg is unavailable, video thumbnail requests return `VIDEO_THUMBNAIL_UNAVAILABLE` and the frontend shows a video fallback while keeping the original video openable.

Packaged macOS Apple Silicon releases can bundle FFmpeg at `packaging/macos/ffmpeg/arm64/ffmpeg`. Public binary distribution requires a verified source, architecture, checksum, license configuration, and matching third-party notices. The packaging flow does not download FFmpeg automatically.

## Database migrations

Database changes use Flyway.

Migration files are located in:

```text
src/main/resources/db/migration/
```

Naming format:

```text
V{number}__short_description.sql
```

Examples:

```text
V1__create_memory_sources.sql
V5__create_memory_scan_jobs.sql
```

Rules:

1. Never modify a migration that has already been applied locally or in a release.
2. Create a new migration for every schema change.
3. Test migrations against a fresh database.
4. Keep SQLite compatibility in mind.
5. Use `BIGINT` for Java `long` fields.

## Testing

### Backend tests

Run:

```bash
./mvnw test
```

The test profile uses a temporary SQLite database and must never use your personal `~/.snapmemoria/data/snapmemoria.db`.

Backend tests should cover:

* Snapchat filename parsing
* Main media and overlay association
* Duplicate Snapchat identifiers
* Missing source folders
* Media access restricted to configured source paths
* Timeline queries
* Flashback queries
* Pagination
* Scan job lifecycle
* Scan failure handling

### Frontend tests

Run:

```bash
npm --prefix frontend run test
```

Use Vitest and React Testing Library.

Frontend tests should focus on user-visible behavior:

* Viewer opens and closes
* Error states are visible
* Flashbacks are grouped correctly
* Source scan progress is rendered
* Settings actions display expected status
* Gallery pagination appends results correctly

## Formatting and linting

Format all code:

```bash
npm run format
```

Run all checks:

```bash
npm run verify
```

The verification command runs:

```text
Backend formatting check
Frontend formatting check
Frontend linting
Backend tests
Frontend tests
Backend build
Frontend production build
```

Do not manually bypass formatting rules. Use the formatter first.

## Git hooks

The repository uses Husky hooks.

### Pre-commit hook

Before each commit, the repository runs:

```bash
npm run verify
```

A commit is blocked when formatting, linting, tests, or builds fail.

### Commit message hook

Commit messages are validated with Commitlint and Conventional Commits.

Valid examples:

```text
feat(scanner): add scan cancellation support
fix(viewer): display missing source message
test(flashbacks): cover empty result state
docs(readme): clarify setup instructions
chore(tooling): update quality checks
```

Invalid examples:

```text
changes
fix bug
Update stuff
new feature
```

## Branch naming

Use descriptive branch names:

```text
feature/favorites
feature/source-availability
fix/video-thumbnail-timeout
refactor/memory-scanner
test/media-streaming
docs/technical-guide
ci/pull-request-validation
```

Avoid vague names such as:

```text
test
changes
new
final
fix2
```

## Pull request expectations

Keep pull requests focused on one clear concern.

Before opening a pull request:

1. Ensure your branch is up to date with `main`.
2. Run `npm run verify`.
3. Add or update tests when behavior changes.
4. Add screenshots for meaningful UI changes.
5. Describe the user-visible impact.
6. Explain database migrations when applicable.
7. Do not include personal files, private paths, or Snapchat exports.

## Security and privacy rules

SnapMemoria handles private media.

Never commit:

```text
Snapchat exports
Photos or videos from a personal archive
SQLite databases
Thumbnail cache files
Local source paths
Environment files
Private screenshots
```

Do not expose local filesystem paths through API responses.

All media endpoints must use a Memory identifier and validate that the resolved file is inside a configured source folder.

## Adding a new feature

A typical feature workflow is:

```text
1. Create a focused branch
2. Add or update database migrations if needed
3. Implement backend behavior
4. Add backend tests
5. Implement frontend behavior
6. Add frontend tests
7. Run npm run format
8. Run npm run verify
9. Commit with Conventional Commits
10. Open a focused pull request
```

## Useful commands

```bash
# Format backend and frontend
npm run format

# Run all checks
npm run verify

# Run backend tests
./mvnw test

# Run frontend tests
npm --prefix frontend run test

# Run backend locally
./mvnw spring-boot:run

# Run frontend locally
npm --prefix frontend run dev

# Build backend
./mvnw package

# Build frontend
npm --prefix frontend run build
```
