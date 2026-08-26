-- 부스 리뷰 악용 방지: 신고 누적/운영자 강제 숨김 처리용 컬럼
ALTER TABLE booth_reviews
    ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_reason VARCHAR(30),
    ADD COLUMN hidden_at TIMESTAMPTZ;

-- booth_review_reports: 리뷰 신고 (회원당 리뷰 하나에 신고 1회만 가능)
CREATE TABLE booth_review_reports (
    id BIGSERIAL PRIMARY KEY,
    booth_review_id BIGINT NOT NULL REFERENCES booth_reviews(id) ON DELETE CASCADE,
    reporter_member_id BIGINT NOT NULL,
    reason VARCHAR(200),
    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (booth_review_id, reporter_member_id)
);

CREATE INDEX idx_booth_review_reports_review_id ON booth_review_reports(booth_review_id);
