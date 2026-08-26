-- booth_review_helpful_votes: 리뷰 "도움이 돼요" (회원당 리뷰 하나에 1회만 가능, 취소 가능)
CREATE TABLE booth_review_helpful_votes (
    id BIGSERIAL PRIMARY KEY,
    booth_review_id BIGINT NOT NULL REFERENCES booth_reviews(id) ON DELETE CASCADE,
    member_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (booth_review_id, member_id)
);

CREATE INDEX idx_booth_review_helpful_votes_review_id ON booth_review_helpful_votes(booth_review_id);
