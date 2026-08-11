ALTER TABLE booth_reservations
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;

UPDATE booth_reservations
SET created_at = COALESCE(created_at, reserved_at, updated_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE booth_reservations
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;
