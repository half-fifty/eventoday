ALTER TABLE advertisements
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_advertisements_organization_archived
    ON advertisements (applicant_organization_id, archived_at, created_at DESC);
