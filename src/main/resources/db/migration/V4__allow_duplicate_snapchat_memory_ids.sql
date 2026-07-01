CREATE TABLE memories_new (
                              id TEXT PRIMARY KEY,

                              source_id TEXT NOT NULL,
                              external_memory_id TEXT NOT NULL,

                              captured_at TEXT NOT NULL,
                              media_type TEXT NOT NULL,

                              main_path TEXT NOT NULL,
                              overlay_path TEXT,

                              file_size_bytes BIGINT NOT NULL,
                              last_modified_at TEXT NOT NULL,

                              created_at TEXT NOT NULL,
                              updated_at TEXT NOT NULL,

                              CONSTRAINT uq_memories_source_main_path
                                  UNIQUE (source_id, main_path),

                              CONSTRAINT fk_memories_source
                                  FOREIGN KEY (source_id)
                                      REFERENCES memory_sources(id)
);

INSERT INTO memories_new (
    id,
    source_id,
    external_memory_id,
    captured_at,
    media_type,
    main_path,
    overlay_path,
    file_size_bytes,
    last_modified_at,
    created_at,
    updated_at
)
SELECT
    id,
    source_id,
    external_memory_id,
    captured_at,
    media_type,
    main_path,
    overlay_path,
    file_size_bytes,
    last_modified_at,
    created_at,
    updated_at
FROM memories;

DROP TABLE memories;

ALTER TABLE memories_new RENAME TO memories;

CREATE INDEX idx_memories_source_id
    ON memories(source_id);

CREATE INDEX idx_memories_captured_at
    ON memories(captured_at);

CREATE INDEX idx_memories_source_captured_at
    ON memories(source_id, captured_at);

CREATE INDEX idx_memories_source_external_id
    ON memories(source_id, external_memory_id);