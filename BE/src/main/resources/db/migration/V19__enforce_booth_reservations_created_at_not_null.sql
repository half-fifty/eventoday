-- V16에서 created_at을 백필했으니, 이제 NOT NULL을 강제한다.
-- (예약 생성 경로는 항상 createdAt을 채우므로 앞으로도 null이 들어올 일이 없다.)
UPDATE booth_reservations
SET created_at = COALESCE(created_at, reserved_at, updated_at, CURRENT_TIMESTAMP)
WHERE created_at IS NULL;

ALTER TABLE booth_reservations
    ALTER COLUMN created_at SET NOT NULL;
