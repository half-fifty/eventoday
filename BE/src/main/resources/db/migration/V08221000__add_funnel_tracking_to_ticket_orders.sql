-- 결제완료 시점에 funnel-actions로 COMPLETE_PAYMENT 이벤트를 발행하려면, 주문 생성 시점의
-- 퍼널 세션 정보가 결제 확정(PaymentFinalizer)까지 살아있어야 한다. 결제 확정은 별도 요청/웹훅/
-- 정산 배치에서 일어나 원래 요청 컨텍스트가 없으므로 ticket_orders에 저장해둔다.
ALTER TABLE ticket_orders
    ADD COLUMN funnel_session_id VARCHAR(80),
    ADD COLUMN funnel_anonymous_id VARCHAR(100);
