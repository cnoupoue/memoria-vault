CREATE TABLE memory_sources_without_unique_root_path (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    root_path TEXT NOT NULL,
    last_scan_at TEXT,
    last_scan_status TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);

INSERT INTO memory_sources_without_unique_root_path (
    id,
    name,
    root_path,
    last_scan_at,
    last_scan_status,
    created_at,
    updated_at
)
SELECT
    id,
    name,
    root_path,
    last_scan_at,
    last_scan_status,
    created_at,
    updated_at
FROM memory_sources;

DROP TABLE memory_sources;

ALTER TABLE memory_sources_without_unique_root_path
    RENAME TO memory_sources;

CREATE INDEX idx_memory_sources_root_path
    ON memory_sources(root_path);
