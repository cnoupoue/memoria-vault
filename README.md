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

### Run the backend

```bash
./mvnw spring-boot:run
```

The backend starts on:

```text
http://127.0.0.1:8080
```

You can verify it with:

```bash
curl http://127.0.0.1:8080/actuator/health
```

### Run the frontend

In a second terminal:

```bash
cd frontend
npm install
npm run dev
```

Then open:

```text
http://localhost:5173
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

## Development

Run all local quality checks:

```bash
npm run verify
```

The build compiles the backend with Java 21 compatibility. If you have several JDKs installed, any active `JAVA_HOME` pointing to Java 21 or later is accepted.

This validates:

* Java formatting with Spotless
* Frontend formatting with Prettier
* ESLint checks
* Backend tests
* Frontend tests
* Backend build
* Frontend production build

Format the complete project:

```bash
npm run format
```

For technical architecture, development workflow, testing, and contribution guidance, see:

* [Technical contribution guide](docs/technical-contribution-guide.md)
* [Contributing guide](docs/CONTRIBUTING.md)

## Contributing

Contributions, ideas, bug reports, and feature requests are welcome.

Before opening a pull request:

1. Create a focused branch.
2. Follow Conventional Commit messages.
3. Run `npm run verify`.
4. Do not include personal Memories, local databases, cached thumbnails, or private paths in your changes.

Useful branch names:

```text
feature/favorites
fix/video-thumbnail-error
refactor/memory-scanner
test/flashback-api
docs/setup-guide
```

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
