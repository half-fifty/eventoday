CREATE TABLE organization_signup_reviews (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMPTZ,
    rejection_reason VARCHAR(1000),
    business_registration_file_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_organization_signup_reviews_organization
        FOREIGN KEY (organization_id) REFERENCES organizations(id),
    CONSTRAINT fk_organization_signup_reviews_reviewer
        FOREIGN KEY (reviewed_by) REFERENCES members(id),
    CONSTRAINT fk_organization_signup_reviews_file
        FOREIGN KEY (business_registration_file_id) REFERENCES file_assets(id)
);

CREATE INDEX idx_organization_signup_reviews_status_submitted
    ON organization_signup_reviews (status, submitted_at);

CREATE INDEX idx_organization_signup_reviews_org_created
    ON organization_signup_reviews (organization_id, created_at DESC);
