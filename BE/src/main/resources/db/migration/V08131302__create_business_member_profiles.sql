CREATE TABLE business_member_profiles (
    member_id BIGINT PRIMARY KEY,
    contact_name VARCHAR(50) NOT NULL,
    contact_phone VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_business_member_profiles_member
        FOREIGN KEY (member_id) REFERENCES members(id)
);
