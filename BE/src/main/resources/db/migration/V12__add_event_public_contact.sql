ALTER TABLE events
    ADD COLUMN contact_email VARCHAR(255),
    ADD COLUMN contact_phone VARCHAR(30);

COMMENT ON COLUMN events.contact_email IS '행사 공개 문의 이메일';
COMMENT ON COLUMN events.contact_phone IS '행사 공개 문의 연락처';
