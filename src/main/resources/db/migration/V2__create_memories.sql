CREATE TABLE memories (
                          id TEXT PRIMARY KEY,

                          source_id TEXT NOT NULL,
                          external_memory_id TEXT NOT NULL,

                          captured_at TEXT NOT NULL,
                          media_type TEXT NOT NULL,

                          main_path TEXT NOT NULL,
                          overlay_path TEXT,

                          file_size_bytes INTEGER NOT NULL,
                          last_modified_at TEXT NOT NULL,

                          created_at TEXT NOT NULL,
                          updated_at TEXT NOT NULL,

                          CONSTRAINT uq_memories_source_external_id
                              UNIQUE (source_id, external_memory_id),

                          CONSTRAINT fk_memories_source
                              FOREIGN KEY (source_id)
                                  REFERENCES memory_sources(id)
);

CREATE INDEX idx_memories_source_id
    ON memories(source_id);

CREATE INDEX idx_memories_captured_at
    ON memories(captured_at);

CREATE INDEX idx_memories_source_captured_at
    ON memories(source_id, captured_at);