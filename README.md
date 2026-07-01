# SnapMemoria

SnapMemoria is a local application for browsing exported Snapchat Memories more comfortably than directly opening files from a USB drive.

The application is designed for large Snapchat exports containing many photos, videos, and overlays spread across folders such as `memories`, `memories 2`, and `memories 3`.

It will progressively provide:

* A gallery grouped by year and month
* Photo and video previews
* Snapchat overlay support
* “On this day” flashbacks from previous years
* Fast navigation through a local database index
* A local-first setup that keeps original memories on the USB drive

## Privacy

SnapMemoria is designed to run locally on your computer.

Your original Snapchat files are never copied into the Git repository. The application only stores local metadata and, later, cached thumbnails on your computer.

By default, the server runs on `127.0.0.1`, which means it is accessible only from the same computer.

## Current features

The current version supports:

* Registering one or more Snapchat Memories folders
* Listing configured folders
* Scanning a configured folder recursively
* Counting supported Snapchat media files:

    * Main images: `-main.jpg` and `-main.jpeg`
    * Main videos: `-main.mp4` and `-main.mov`
    * Overlays: `-overlay.png`

The application does not yet save individual Memories in the database. The current scan is only used to validate that the folder structure is detected correctly.

## Requirements

* Java 21
* Maven Wrapper included in the project
* A Snapchat Memories export folder

Example export structure:

```text
snapchat-memories/
├── memories/
│   ├── 2019-10-05_493C7A65-6059-48C0-81F1-9A7D3E068856-main.jpg
│   ├── 2019-10-05_493C7A65-6059-48C0-81F1-9A7D3E068856-overlay.png
│   └── ...
├── memories 2/
├── memories 3/
└── ...
```

When adding a source, select the parent `snapchat-memories` folder rather than an individual `memories` folder. The scanner automatically includes all subfolders.

## Running the application

Create the local SQLite data directory once:

```bash
mkdir -p ~/.snapmemoria/data
```

Start the application from the project root:

```bash
./mvnw spring-boot:run
```

Check that the application is running:

```bash
curl http://localhost:8080/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

## Configuring a Memories source

A source is the folder that contains the exported Snapchat Memories files.

### List configured sources

```bash
curl http://localhost:8080/api/sources
```

When no source has been registered yet, the response is:

```json
[]
```

### Add a source

Replace the example path with the actual path of your export folder.

```bash
curl -X POST http://localhost:8080/api/sources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Snapchat Memories USB",
    "rootPath": "/Volumes/MY_USB/snapchat-memories"
  }'
```

Example response:

```json
{
  "id": "example-source-id",
  "name": "Snapchat Memories USB",
  "rootPath": "/Volumes/MY_USB/snapchat-memories",
  "lastScanAt": null,
  "lastScanStatus": "NOT_SCANNED",
  "createdAt": "2026-07-01T10:00:00Z",
  "updatedAt": "2026-07-01T10:00:00Z"
}
```

On macOS, external USB drives are usually mounted under:

```text
/Volumes/DRIVE_NAME/
```

You can check available drives with:

```bash
ls /Volumes
```

### Scan a source

First, retrieve the source ID:

```bash
curl http://localhost:8080/api/sources
```

Then start a scan:

```bash
curl -X POST http://localhost:8080/api/sources/SOURCE_ID/scan
```

Example response:

```json
{
  "sourceId": "example-source-id",
  "sourcePath": "/Volumes/MY_USB/snapchat-memories",
  "status": "COMPLETED",
  "filesVisited": 15482,
  "mainImages": 7194,
  "mainVideos": 7821,
  "overlays": 467,
  "unsupportedFiles": 0,
  "startedAt": "2026-07-01T10:00:00Z",
  "completedAt": "2026-07-01T10:00:04Z"
}
```

### Change a source path

The current API does not update a source path directly.

To change the location of a Memories export:

1. List the configured sources.
2. Delete the old source.
3. Add the new source with the correct path.
4. Run a new scan.

Delete a source:

```bash
curl -X DELETE http://localhost:8080/api/sources/SOURCE_ID
```

Then add the replacement source:

```bash
curl -X POST http://localhost:8080/api/sources \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Snapchat Memories USB",
    "rootPath": "/Volumes/NEW_USB_NAME/snapchat-memories"
  }'
```

## Local data

The SQLite database is stored outside the repository:

```text
~/.snapmemoria/data/snapmemoria.db
```

This file contains application data such as configured sources and Flyway migration history.

Do not commit this database file to Git.

## Development roadmap

### Completed

* Spring Boot application setup
* SQLite configuration
* Flyway setup
* Memory source management
* Recursive folder scan and media classification

### Next steps

* Store scanned Memories in SQLite
* Parse the Snapchat filename format
* Link main files with overlays
* Add a gallery API with pagination
* Generate cached thumbnails
* Add year and month navigation
* Add flashbacks such as “1 year ago today”
* Build a React interface
* Package the application for local deployment
