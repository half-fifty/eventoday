ALTER TABLE events
    ADD COLUMN detail_display_type VARCHAR(30) NOT NULL DEFAULT 'IMAGE_GALLERY',
    ADD COLUMN official_website_url VARCHAR(1000);

ALTER TABLE events
    ADD CONSTRAINT chk_events_detail_display_type
    CHECK (detail_display_type IN ('IMAGE_GALLERY', 'RICH_TEXT', 'EXTERNAL_SITE'));
