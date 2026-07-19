ALTER TABLE memories
    ADD COLUMN is_favorite BOOLEAN NOT NULL DEFAULT 0;

ALTER TABLE memories
    ADD COLUMN favorited_at TEXT;

CREATE INDEX idx_memories_favorites
    ON memories(is_favorite, favorited_at DESC);
