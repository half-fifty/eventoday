CREATE TABLE event_detail_images (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    file_id BIGINT NOT NULL,
    display_order INTEGER NOT NULL,
    alt_text VARCHAR(300),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_event_detail_images_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE,
    CONSTRAINT fk_event_detail_images_file
        FOREIGN KEY (file_id) REFERENCES file_assets (id),
    CONSTRAINT uk_event_detail_images_event_order
        UNIQUE (event_id, display_order),
    CONSTRAINT uk_event_detail_images_event_file
        UNIQUE (event_id, file_id),
    CONSTRAINT ck_event_detail_images_display_order
        CHECK (display_order >= 0)
);

CREATE INDEX idx_event_detail_images_event_order
    ON event_detail_images (event_id, display_order);
