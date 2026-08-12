ALTER TABLE booth_reviews
    ADD COLUMN IF NOT EXISTS member_profile VARCHAR(500);