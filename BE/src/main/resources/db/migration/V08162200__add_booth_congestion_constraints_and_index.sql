-- booth_congestion: 값 범위 제약 및 조회 성능용 복합 인덱스
ALTER TABLE booth_congestion
    ADD CONSTRAINT ck_booth_congestion_wait_time_non_negative
        CHECK (estimated_wait_time IS NULL OR estimated_wait_time >= 0),
    ADD CONSTRAINT ck_booth_congestion_capacity_rate_range
        CHECK (predicted_capacity_rate IS NULL OR predicted_capacity_rate BETWEEN 0 AND 100);

CREATE INDEX idx_booth_congestion_booth_id_recorded_at
    ON booth_congestion (booth_id, recorded_at DESC);

-- booth_congestion_forecast: 값 범위 제약
ALTER TABLE booth_congestion_forecast
    ADD CONSTRAINT ck_booth_congestion_forecast_hour_range
        CHECK (forecast_hour BETWEEN 0 AND 23),
    ADD CONSTRAINT ck_booth_congestion_forecast_wait_time_non_negative
        CHECK (predicted_wait_time IS NULL OR predicted_wait_time >= 0),
    ADD CONSTRAINT ck_booth_congestion_forecast_capacity_rate_range
        CHECK (predicted_capacity_rate IS NULL OR predicted_capacity_rate BETWEEN 0 AND 100);
