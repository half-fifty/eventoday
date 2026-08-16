-- booth_congestion: 실시간 혼잡도 (현황)
CREATE TABLE booth_congestion (
                                  id BIGSERIAL PRIMARY KEY,
                                  booth_id BIGINT NOT NULL,
                                  congestion_level VARCHAR(20) NOT NULL DEFAULT 'MEDIUM',
                                  estimated_wait_time INT,
                                  predicted_capacity_rate DECIMAL(5, 2),
                                  recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                  FOREIGN KEY (booth_id) REFERENCES booths(id) ON DELETE CASCADE
);

CREATE INDEX idx_booth_congestion_booth_id ON booth_congestion(booth_id);
CREATE INDEX idx_booth_congestion_recorded_at ON booth_congestion(recorded_at DESC);

-- booth_congestion_forecast: 시간대별 혼잡도 예측
CREATE TABLE booth_congestion_forecast (
                                           id BIGSERIAL PRIMARY KEY,
                                           booth_id BIGINT NOT NULL,
                                           forecast_hour INT NOT NULL,              -- 0~23시
                                           forecast_date DATE NOT NULL,             -- 예측 날짜
                                           predicted_congestion_level VARCHAR(20),  -- LOW, MEDIUM, HIGH
                                           predicted_wait_time INT,                 -- 예상 대기시간
                                           predicted_capacity_rate DECIMAL(5, 2),   -- 예상 수용율
                                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                           FOREIGN KEY (booth_id) REFERENCES booths(id) ON DELETE CASCADE,
                                           UNIQUE (booth_id, forecast_date, forecast_hour)
);

CREATE INDEX idx_booth_congestion_forecast_booth_id ON booth_congestion_forecast(booth_id);
CREATE INDEX idx_booth_congestion_forecast_date ON booth_congestion_forecast(forecast_date);
CREATE INDEX idx_booth_congestion_forecast_hour ON booth_congestion_forecast(forecast_hour);

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