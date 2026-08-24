-- 부스 체크인 QR 인식 방식을 ExchangeCode(구매 수량당 1개, 평생 1회만 사용 가능한 교환코드)에서
-- AdmissionTicket.qrToken(게이트 입장 후에도 소모되지 않는 안정적인 "입장 QR")으로 변경한다.
-- 예전 방식은 어느 부스에서든 첫 체크인 순간 코드가 영구 REDEEMED 처리돼, 같은 코드로 다른
-- 부스에서는 다시 체크인할 수 없었다 (booth_qr_scans는 아직 실사용 데이터가 없어 안전하게 변경 가능).
ALTER TABLE booth_qr_scans RENAME COLUMN exchange_code_id TO admission_ticket_id;

-- "부스 하나당 같은 입장권으로는 한 번만 체크인 가능"을 DB 제약으로 강제한다.
-- 예전엔 재스캔을 duplicate=true로 허용해서 기록만 남겼지만, 이제는 두 번째 스캔 자체를 막는다.
ALTER TABLE booth_qr_scans DROP COLUMN duplicate;
ALTER TABLE booth_qr_scans ADD CONSTRAINT uk_booth_qr_scans_booth_ticket UNIQUE (booth_id, admission_ticket_id);
