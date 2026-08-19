-- 리뷰 사진 첨부 (리뷰 하나에 최대 5장, 애플리케이션 레벨에서 제한). 파일 업로드 자체는
-- 기존 공통 파일 API(POST /v1/files)를 그대로 쓰고, 여기서는 review-file 연결만 관리한다.
CREATE TABLE booth_review_photos (
    id BIGSERIAL PRIMARY KEY,
    booth_review_id BIGINT NOT NULL REFERENCES booth_reviews(id) ON DELETE CASCADE,
    file_id BIGINT NOT NULL REFERENCES file_assets(id),
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,

    UNIQUE (booth_review_id, file_id)
);

CREATE INDEX idx_booth_review_photos_review_id ON booth_review_photos(booth_review_id, sort_order);
