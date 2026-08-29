CREATE TABLE IF NOT EXISTS authors (
    id    INTEGER PRIMARY KEY AUTOINCREMENT,
    name  TEXT    NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS sources (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         TEXT    NOT NULL,
    path         TEXT    NOT NULL,
    path_lower   TEXT    NOT NULL,
    content_hash TEXT    NOT NULL,
    file_format  TEXT    NOT NULL CHECK (file_format IN ('PDF', 'EPUB', 'MHTML')),
    author_id    INTEGER REFERENCES authors(id) ON DELETE SET NULL,
    year         INTEGER NULL,
    edition      TEXT    NULL,
    url          TEXT    NULL,
    created_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TEXT    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at   TEXT    NULL
);

CREATE TABLE IF NOT EXISTS tags (
    id   INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT    NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS source_tags (
    source_id INTEGER NOT NULL REFERENCES sources(id) ON DELETE CASCADE,
    tag_id    INTEGER NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
    PRIMARY KEY (source_id, tag_id)
);

CREATE INDEX IF NOT EXISTS idx_sources_path_lower    ON sources(path_lower);
CREATE INDEX IF NOT EXISTS idx_sources_content_hash  ON sources(content_hash);
CREATE INDEX IF NOT EXISTS idx_sources_deleted_at    ON sources(deleted_at);
CREATE INDEX IF NOT EXISTS idx_sources_author_id     ON sources(author_id);
CREATE INDEX IF NOT EXISTS idx_source_tags_source_id ON source_tags(source_id);
CREATE INDEX IF NOT EXISTS idx_source_tags_tag_id    ON source_tags(tag_id);
