-- V08241100이 admission_ticket_id로 컬럼명만 바꿨을 뿐, 예전 FK(fk_booth_qr_scans_exchange_code)는
-- 여전히 exchange_codes(id)를 참조하고 있었다 - admission_ticket_id 값이 exchange_codes.id와
-- 우연히 겹치지 않는 한 모든 INSERT가 FK 위반으로 실패한다. admission_tickets(id)를 참조하도록 교체한다.
ALTER TABLE booth_qr_scans DROP CONSTRAINT fk_booth_qr_scans_exchange_code;
ALTER TABLE booth_qr_scans ADD CONSTRAINT fk_booth_qr_scans_admission_ticket
    FOREIGN KEY (admission_ticket_id) REFERENCES admission_tickets (id);

-- uk_booth_review_summary_batches UNIQUE(booth_id, batch_index)가 이미 booth_id를 선행 컬럼으로
-- 하는 인덱스를 만들어주므로, V08241200에서 만든 별도 인덱스는 중복이다.
DROP INDEX idx_booth_review_summary_batches_booth;
