-- 퍼널 분석 배치가 계산한 파생 데이터 저장용 테이블.
-- 원본 이벤트(FunnelAction)는 Elasticsearch에 저장되고, 이 테이블들은 배치가
-- 그 원본을 집계한 결과만 담는다 (docs/funnel-ai-diagnosis/technical-design.md 참고).

CREATE TABLE visitor_profile (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    visitor_key VARCHAR(100) NOT NULL,
    anonymous_id VARCHAR(100) NOT NULL,
    user_id BIGINT,
    first_seen_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_visitor_profile_visitor_key UNIQUE (visitor_key)
);

CREATE TABLE funnel_session (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    event_id BIGINT NOT NULL,
    visitor_key VARCHAR(100) NOT NULL,
    max_step_reached VARCHAR(50) NOT NULL,
    is_dropped BOOLEAN NOT NULL,
    is_returning_visitor BOOLEAN NOT NULL,
    is_booth_explored BOOLEAN NOT NULL,
    is_step_skipped BOOLEAN NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    last_action_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_funnel_session_session_id UNIQUE (session_id),
    CONSTRAINT fk_funnel_session_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);

CREATE INDEX idx_funnel_session_event_started_at
    ON funnel_session (event_id, started_at);

CREATE TABLE funnel_diagnosis_report (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id BIGINT NOT NULL,
    report_date DATE NOT NULL,
    step_conversion_rates JSONB NOT NULL,
    anomaly_detected BOOLEAN NOT NULL,
    statistically_significant BOOLEAN NOT NULL,
    ai_comment TEXT,
    baseline_avg_rate DOUBLE PRECISION,
    sample_size INTEGER NOT NULL,
    session_window_minutes INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_funnel_diagnosis_report_event_date UNIQUE (event_id, report_date),
    CONSTRAINT fk_funnel_diagnosis_report_event
        FOREIGN KEY (event_id) REFERENCES events (id) ON DELETE CASCADE
);
