-- booth_review_summaries: 부스 리뷰 코멘트 AI 요약 (부스당 1건, 재생성 시 갱신)
CREATE TABLE booth_review_summaries (
                                         booth_id BIGINT PRIMARY KEY,
                                         summary TEXT NOT NULL,
                                         review_count_at_summary INT NOT NULL,
                                         generated_at TIMESTAMP NOT NULL,

                                         FOREIGN KEY (booth_id) REFERENCES booths(id) ON DELETE CASCADE
);
