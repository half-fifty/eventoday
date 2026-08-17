-- generated_at을 OffsetDateTime 엔티티 필드와 일치하도록 TIMESTAMPTZ로 수정
-- (ddl-auto=validate 환경에서 TIMESTAMP로는 스키마 검증이 실패한다)
ALTER TABLE booth_review_summaries
    ALTER COLUMN generated_at TYPE TIMESTAMPTZ USING generated_at AT TIME ZONE 'UTC';

-- 코멘트 리뷰 수만으로는 리뷰 수정/삭제+추가처럼 개수가 유지되는 변경을 감지하지 못해
-- 요약 캐시 무효화 판단에 쓸 최신 리뷰 변경 시각을 함께 저장한다
ALTER TABLE booth_review_summaries
    ADD COLUMN last_review_updated_at TIMESTAMPTZ;
