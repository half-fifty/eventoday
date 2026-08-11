-- V18에서 취소된 예약이면 재예약을 허용하도록 부분 유니크 인덱스로 바꿨었지만,
-- 실제 의도는 한 번이라도 예약한(취소 포함) 부스는 같은 회원이 다시 예약할 수 없어야 하는 것이었다.
-- (member_id, booth_id) 전체에 대한 유니크 제약으로 되돌린다.
DROP INDEX uk_booth_reservations_active;

ALTER TABLE booth_reservations
    ADD CONSTRAINT uk_booth_reservations UNIQUE (member_id, booth_id);
