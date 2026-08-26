-- V18에서 취소된 예약이면 재예약을 허용하도록 부분 유니크 인덱스로 바꿨었지만,
-- 실제 의도는 한 번이라도 예약한(취소 포함) 부스는 같은 회원이 다시 예약할 수 없어야 하는 것이었다.
-- (member_id, booth_id) 전체에 대한 유니크 제약으로 되돌린다.
--
-- V18이 적용된 이후에는 취소 후 재예약이 실제로 가능했기 때문에, 이 마이그레이션이 적용되는 환경에는
-- 이미 같은 (member_id, booth_id) 조합으로 중복 행이 쌓여 있을 수 있다. 정리하지 않으면 아래
-- ADD CONSTRAINT가 실패해 마이그레이션 전체가 중단되므로, 각 조합에서 가장 먼저 예약한 행(reserved_at
-- 기준, 동률이면 id 기준)만 남기고 나머지는 제거한다. "한 번이라도 예약하면 그 이후는 재예약 불가"라는
-- 이번 의도와 일치하게 최초 예약 이력을 보존하는 방향으로 정리한다.
DELETE FROM booth_reservations br
    USING booth_reservations dup
    WHERE br.member_id = dup.member_id
      AND br.booth_id = dup.booth_id
      AND (br.reserved_at, br.id) > (dup.reserved_at, dup.id);

DROP INDEX uk_booth_reservations_active;

ALTER TABLE booth_reservations
    ADD CONSTRAINT uk_booth_reservations UNIQUE (member_id, booth_id);
