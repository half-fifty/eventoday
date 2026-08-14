ALTER TABLE organizations ADD COLUMN postal_code VARCHAR(10);
ALTER TABLE organizations ADD COLUMN address_line1 VARCHAR(300);
ALTER TABLE organizations ADD COLUMN address_line2 VARCHAR(300);
ALTER TABLE organizations ADD COLUMN homepage_url VARCHAR(500);
ALTER TABLE organizations ADD COLUMN introduction VARCHAR(1000);
ALTER TABLE organizations ADD COLUMN logo_file_id BIGINT;

ALTER TABLE organizations
    ADD CONSTRAINT fk_organizations_logo_file
    FOREIGN KEY (logo_file_id) REFERENCES file_assets(id);
