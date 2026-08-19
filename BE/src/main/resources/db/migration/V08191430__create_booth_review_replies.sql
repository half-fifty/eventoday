-- 부스 담당자(운영자)가 리뷰에 공식 답글을 다는 기능. 리뷰 하나에 답글 하나(1:1).
CREATE TABLE booth_review_replies (
    id BIGSERIAL PRIMARY KEY,
    booth_review_id BIGINT NOT NULL UNIQUE REFERENCES booth_reviews(id) ON DELETE CASCADE,
    manager_member_id BIGINT NOT NULL,
    content VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
