ALTER TABLE members
    ALTER COLUMN oauth_provider DROP NOT NULL,
    ALTER COLUMN oauth_subject DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'ck_members_authentication_type'
          AND conrelid = 'members'::regclass
    ) THEN
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
    END IF;
END
$$;
