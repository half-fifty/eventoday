ALTER TABLE members
    ALTER COLUMN oauth_provider DROP NOT NULL,
    ALTER COLUMN oauth_subject DROP NOT NULL,
    ADD COLUMN password_hash VARCHAR(255);

ALTER TABLE members
    ADD CONSTRAINT ck_members_authentication_type
    CHECK (
        (
            oauth_provider IS NOT NULL
            AND oauth_subject IS NOT NULL
            AND password_hash IS NULL
        )
        OR
        (
            oauth_provider IS NULL
            AND oauth_subject IS NULL
            AND password_hash IS NOT NULL
        )
    );
