-- 플랫폼(사이트 전체) 공지사항
-- 행사 단위 공지는 event_contents가 담당하고, 이 테이블은 행사와 무관한 사이트 공지만 저장한다.
CREATE TABLE platform_notices (
                                  id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                                  author_member_id BIGINT NOT NULL,
                                  title VARCHAR(200) NOT NULL,
                                  content TEXT,
                                  pinned BOOLEAN NOT NULL DEFAULT FALSE,
                                  published_at TIMESTAMPTZ NOT NULL,
                                  created_at TIMESTAMPTZ NOT NULL,
                                  updated_at TIMESTAMPTZ NOT NULL,
                                  CONSTRAINT fk_platform_notices_author FOREIGN KEY (author_member_id) REFERENCES members(id)
);

-- 목록 조회 정렬 기준(상단 고정 우선, 최신순)에 맞춘 인덱스
CREATE INDEX idx_platform_notices_pinned_published_at
    ON platform_notices (pinned DESC, published_at DESC, id DESC);