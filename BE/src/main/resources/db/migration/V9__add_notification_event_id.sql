ALTER TABLE notifications
    ADD COLUMN event_id UUID;

UPDATE notifications
SET event_id = md5('legacy-notification:' || id::text)::uuid
WHERE event_id IS NULL;

ALTER TABLE notifications
    ALTER COLUMN event_id SET NOT NULL,
    ADD CONSTRAINT uk_notifications_event_id UNIQUE (event_id);
