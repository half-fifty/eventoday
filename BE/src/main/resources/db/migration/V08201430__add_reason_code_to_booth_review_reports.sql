-- 신고 사유를 자유 텍스트만으로 받으면 나중에 집계·우선순위 판단이 어려워, 정해진 사유 코드를 추가한다.
-- 기존 reason 컬럼은 reason_code가 OTHER일 때 적는 부가 설명으로 의미를 좁힌다.
-- 기존 행은 사유 코드를 알 수 없으므로 OTHER로 백필한다.
ALTER TABLE booth_review_reports
    ADD COLUMN reason_code VARCHAR(30) NOT NULL DEFAULT 'OTHER';
