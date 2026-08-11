-- 취소된 예약이 남아있어도 같은 부스를 재예약할 수 있도록,
-- (member_id, booth_id) 전체가 아니라 RESERVED 상태에서만 중복을 막는다.
ALTER TABLE booth_reservations
    DROP CONSTRAINT uk_booth_reservations;

CREATE UNIQUE INDEX uk_booth_reservations_active
    ON booth_reservations (member_id, booth_id)
    WHERE status = 'RESERVED';
