CREATE TABLE IF NOT EXISTS producer_progress (
    type_id INTEGER PRIMARY KEY,
    last_processed_id INTEGER NOT NULL,
    updated_at TEXT NOT NULL
);
