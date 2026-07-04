# Common packaging notes

This directory is for packaging guidance and assets shared across future operating-system
targets.

Platform-specific paths, icons, bundled executables, and installer scripts belong in the
matching platform directory:

- `packaging/macos`
- `packaging/windows`
- `packaging/linux`

Do not place private local paths, usernames, machine names, or downloaded binaries in packaging
metadata.
