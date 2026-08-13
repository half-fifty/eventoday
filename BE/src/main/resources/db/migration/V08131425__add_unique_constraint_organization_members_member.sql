-- 회원 1명이 여러 조직의 ACTIVE 멤버십을 동시에 가질 수 없도록 제약한다.
-- (BusinessLoginService/AuthService가 "가장 오래된 ACTIVE 멤버십"을 조회 기준으로 쓰는데,
--  멤버십이 1건으로 보장되면 이 조회가 항상 유일한 조직을 가리키게 된다.)
ALTER TABLE organization_members
    ADD CONSTRAINT uk_organization_members_member_id UNIQUE (member_id);
