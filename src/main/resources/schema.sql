CREATE TABLE IF NOT EXISTS producer_progress (
    type_id INTEGER PRIMARY KEY,
    mode TEXT NOT NULL CHECK(mode IN ('BACKFILL', 'UPDATES')),
    last_processed_id INTEGER,
    hendelse_id INTEGER,
    backfill_start_time TEXT,
    backfill_completion_time TEXT,
    last_error TEXT,
    updated_at TEXT NOT NULL
);
