CREATE TABLE memory_sources (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    root_path TEXT NOT NULL UNIQUE,
    last_scan_at TEXT,
    last_scan_status TEXT,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
);
