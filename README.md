# SnapMemoria

> A local-first app for rediscovering exported Snapchat Memories.

SnapMemoria helps you browse large Snapchat Memories exports without manually navigating thousands of files on a USB drive.

It indexes your exported photos, videos, and Snapchat overlays locally, then provides a faster way to explore them by year, month, and flashback date.

## Features

* Browse Memories by year and month
* View photos and videos in a full-screen viewer
* Display Snapchat overlays without modifying original files
* Generate cached thumbnails for images and videos
* Rediscover Memories through “On this day” flashbacks
* Manage multiple export sources from the Settings page
* Scan large folders in the background with live progress
* Keep original files on your USB drive or local folder
* Store only metadata, indexes, and thumbnail cache locally

## Privacy first

SnapMemoria is designed to be local-first.

* Your original Snapchat files stay where they are.
* Your media is not uploaded to a cloud service.
* The application runs on your computer by default.
* Local paths, SQLite databases, thumbnails, and personal exports should never be committed to Git.

## Getting started

### Requirements

* Java 21 or later
* Node.js 22 or later
* npm
* FFmpeg for video thumbnails
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

### Clone and install

```bash
git clone https://github.com/cameronnoupoue/snapmemoria.git
cd snapmemoria
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

Stop both services with:

```text
Ctrl + C
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

Or directly:

```bash
curl http://127.0.0.1:8080/actuator/health
```

### Add your Snapchat export

1. Open **Settings** in the application.
2. Add the parent folder containing your Snapchat export.
3. Select the folder that contains directories such as:

```text
snapchat-memories/
├── memories/
├── memories 2/
├── memories 3/
└── ...
```

4. Start a scan.
5. Browse your archive through the timeline.

Do not select an individual `memories` folder when your export contains multiple folders. Select the parent `snapchat-memories` folder instead.

## Local commands

SnapMemoria provides a `Makefile` for common development tasks.

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

# Build backend and frontend production artifacts
make build

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

## Contributing

Contributions, ideas, bug reports, and feature requests are welcome.

Before opening a pull request:

1. Create a focused branch.
2. Follow Conventional Commit messages.
3. Run `make verify`.
4. Add or update tests when behavior changes.
5. Do not include personal Memories, local databases, cached thumbnails, private paths, secrets, or exported media files.

Useful branch names:

```text
feature/favorites
feature/source-availability
fix/video-thumbnail-error
refactor/memory-scanner
test/flashback-api
docs/setup-guide
ci/pull-request-checks
```

When you open a pull request, complete the included template and add screenshots for meaningful interface changes.

## Support the project

SnapMemoria is built as an open-source project.

If it helps you rediscover meaningful memories, consider supporting its development:

[☕ Buy me a coffee](https://buymeacoffee.com/cnoupoue)

You can also support the project by:

* Starring the repository
* Sharing it with people who export Snapchat Memories
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

SnapMemoria is licensed under the [MIT License](LICENSE).
