ALTER TABLE booth_reviews
    ADD COLUMN IF NOT EXISTS member_name VARCHAR(255);
