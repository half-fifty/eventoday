ALTER TABLE events
    ADD COLUMN postal_code VARCHAR(10),
    ADD COLUMN address_detail VARCHAR(200),
    ADD COLUMN latitude NUMERIC(10,7),
    ADD COLUMN longitude NUMERIC(10,7),
    ADD COLUMN kakao_place_id VARCHAR(50);
