-- funnel_session.session_id는 원본 세션 UUID(36자)에 event_id를 ":"로 이어붙여 저장한다
-- (FunnelSessionReconstructionService.reconstructSession 참고). VARCHAR(36)로는 항상 넘쳐서
-- 재구성 배치의 모든 INSERT가 실패했다.
ALTER TABLE funnel_session
    ALTER COLUMN session_id TYPE VARCHAR(80);
