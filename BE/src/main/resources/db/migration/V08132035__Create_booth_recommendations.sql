-- booth_recommendations: 개인화 추천 (필요시 유지)
CREATE TABLE booth_recommendations (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    booth_id BIGINT NOT NULL,
    recommended_booth_id BIGINT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMP NOT NULL,

    UNIQUE (user_id, booth_id, recommended_booth_id),

    FOREIGN KEY (user_id) REFERENCES members(id),
    FOREIGN KEY (booth_id) REFERENCES booths(id),
    FOREIGN KEY (recommended_booth_id) REFERENCES booths(id)
);

CREATE INDEX idx_booth_recommendations_user_id ON booth_recommendations(user_id);
CREATE INDEX idx_booth_recommendations_booth_id ON booth_recommendations(booth_id);
CREATE INDEX idx_booth_recommendations_score ON booth_recommendations(score DESC);
