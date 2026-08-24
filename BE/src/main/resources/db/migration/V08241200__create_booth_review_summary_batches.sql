-- 부스 리뷰 AI 요약이 항상 "최신 50개"만 재료로 쓰면서, 리뷰가 50개를 넘어가는 순간부터
-- 오래된 리뷰의 의견이 요약에서 영구히 빠지는 문제가 있었다. 리뷰를 review id 기준으로 50개씩
-- 묶은 "닫힌 배치" 단위로 한 번만 요약해 영구 캐싱하고(오래된 리뷰가 삭제/수정되지 않는 한
-- 다시 요약할 필요 없음), 최종 요약은 이 배치 요약들 + 아직 배치가 안 찬 최신 리뷰(tail)만
-- 다시 LLM에 넘겨 합친다. 리뷰가 아무리 많아져도 매번 재생성 시 LLM 호출 수가 리뷰 총량에
-- 비례해 늘지 않고, 새로 닫히는 배치가 있을 때만 소폭 늘어난다.
CREATE TABLE booth_review_summary_batches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booth_id BIGINT NOT NULL,
    batch_index INT NOT NULL,
    from_review_id BIGINT NOT NULL,
    to_review_id BIGINT NOT NULL,
    review_count INT NOT NULL,
    summary TEXT NOT NULL,
    last_review_updated_at TIMESTAMPTZ,
    generated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_booth_review_summary_batches UNIQUE (booth_id, batch_index),
    CONSTRAINT fk_booth_review_summary_batches_booth FOREIGN KEY (booth_id) REFERENCES booths(id) ON DELETE CASCADE
);
