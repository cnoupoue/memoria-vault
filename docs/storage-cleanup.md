# Storage Cleanup Notes

Memoria Vault stores registered sources, indexed memories, scan jobs, and favorite state in the
local SQLite database. Source removal is intentionally scoped to the selected source ID.

Deleting a source removes:

- the `memory_sources` row for that source ID;
- indexed `memories` rows with the same `source_id`, including favorite flags for those rows;
- `memory_scan_jobs` rows with the same `source_id`;
- best-effort thumbnail cache files for memory IDs that belonged to that source.

Deleting a source does not remove:

- original user media files;
- unrelated sources that share the same `root_path`;
- memories, favorites, scan jobs, or caches for other source IDs.

## V7 Migration

`V7__allow_duplicate_source_paths.sql` removes the database-level unique constraint from
`memory_sources.root_path` and replaces it with a non-unique lookup index. This preserves existing
rows and allows users to clean up already-duplicated or stale records independently.

New duplicate source creation is still rejected by application validation for an equivalent
normalized path. The relaxed database constraint is for compatibility with existing local data, not
for intentionally creating duplicates.

Flyway Community does not provide automatic rollback. To roll back V7 manually, back up the
database first, remove or merge duplicate `root_path` rows, rebuild `memory_sources` with a unique
`root_path` constraint, and restore dependent rows by source ID. Prefer restoring a pre-upgrade
database backup if rollback is required.

## Favorites Lifecycle

Favorites are stored on indexed memory rows. Removing a source removes favorite state for memories
belonging to that source because those indexed rows are application-owned source metadata.
Favorites for unrelated source IDs are preserved.

Favorites Backup exports favorite identifiers and paths as JSON. Restore matches entries against
currently indexed memories for the selected target source. Missing media or unmatched records are
skipped and reported in the restore summary; restore does not recreate deleted sources.

## Playback Cache Follow-Up

Compatibility playback files are generated cache files under the application cache directory.
Their filenames are derived from source ID plus original media file attributes. When the original
media is unavailable, old playback files cannot always be mapped back to a removed source with the
current data model.

Follow-up issue: add a bounded playback cache cleanup policy, such as maximum age or maximum total
cache size, with diagnostics showing how much space was reclaimed. Generated playback files are
recreatable, so an age or size based policy is safe if it only touches files inside the configured
application playback cache directory.
