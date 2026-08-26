-- V16에서 이미 created_at을 백필했으므로 여기서는 다시 채우지 않는다.
-- SET NOT NULL이 테이블 전체를 다시 스캔하지 않도록, 먼저 NOT VALID로 제약을 걸고 별도로 검증한 뒤
-- NOT NULL을 적용한다 (유효한 CHECK 제약이 있으면 PostgreSQL이 SET NOT NULL 시 전체 스캔을 건너뛴다).
ALTER TABLE booth_reservations
    ADD CONSTRAINT booth_reservations_created_at_not_null CHECK (created_at IS NOT NULL) NOT VALID;

ALTER TABLE booth_reservations
    VALIDATE CONSTRAINT booth_reservations_created_at_not_null;

ALTER TABLE booth_reservations
    ALTER COLUMN created_at SET NOT NULL;

ALTER TABLE booth_reservations
    DROP CONSTRAINT booth_reservations_created_at_not_null;
