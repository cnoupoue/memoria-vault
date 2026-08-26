CREATE INDEX idx_memories_captured_at_id
    ON memories(captured_at, id);

CREATE INDEX idx_memories_favorite_captured_at_id
    ON memories(is_favorite, captured_at, id);
