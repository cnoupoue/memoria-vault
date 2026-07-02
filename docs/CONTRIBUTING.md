# Contributing to Memoria Vault

Thank you for contributing to Memoria Vault.

Memoria Vault is a local-first application for browsing compatible exported memories locally. The project is designed to keep original media files outside the repository while indexing metadata and generating local cached previews.

Memoria Vault is an independent tool. Compatibility references, including supported Snapchat export formats, must remain descriptive and non-prominent.

## Prerequisites

* Java 21
* Node.js 22 or later
* npm
* Git
* FFmpeg for video thumbnail generation

## Local setup

Install the root tooling dependencies:

```bash
npm install
```

Install the frontend dependencies:

```bash
npm --prefix frontend install
```

Run the backend:

```bash
./mvnw spring-boot:run
```

Run the frontend in a separate terminal:

```bash
npm --prefix frontend run dev
```

## Required checks

Before opening a pull request, run:

```bash
npm run verify
```

This command checks formatting, linting, backend tests, frontend tests, and production builds.

## Formatting

Format the full project with:

```bash
npm run format
```

Java formatting is handled by Spotless and Google Java Format.

Frontend formatting is handled by Prettier.

## Commit convention

Memoria Vault uses Conventional Commits.

Examples:

```text
feat(scanner): add background scan progress
fix(viewer): handle unavailable USB sources
test(memory): add scanner parsing coverage
docs(readme): clarify local installation
chore(tooling): add repository quality checks
```

Use lowercase, imperative subjects without a final period.

## Branch naming

Use one of the following prefixes:

```text
feature/
fix/
refactor/
test/
docs/
chore/
ci/
```

Examples:

```text
feature/favorites
fix/video-thumbnail-timeout
test/memory-source-scanner
docs/contributing-guide
```

## Pull requests

Keep pull requests focused on one concern.

Before requesting review:

1. Rebase or merge the latest `main` branch when needed.
2. Run `npm run verify`.
3. Describe the user-visible impact.
4. Include screenshots for meaningful frontend changes.
5. Do not commit personal archive exports, SQLite databases, thumbnail caches, `.env` files, or local source paths.

## Privacy and data safety

Never add personal Memories, media exports, local database files, thumbnails, or screenshots containing private content to the repository.

Original exported files must remain outside the project directory and outside Git.
