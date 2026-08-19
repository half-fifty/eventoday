# Refund Policy

## PAYMENT_NOT_PAID

결제 자체가 완료된 상태가 아니면 환불을 진행할 수 없습니다.
서버가 확인한 paymentStatus가 PAID가 아닌 경우이며, 환불 가능 여부는 Java RefundEligibilityPolicy의 refundable 값과 reasonCode를 기준으로 설명해야 합니다.

## PAYMENT_ORDER_NOT_PAID

결제 주문 상태가 결제 완료 상태가 아니면 환불을 진행할 수 없습니다.
서버가 확인한 paymentOrderStatus가 PAID가 아닌 경우이며, 결제 주문 상태가 먼저 정상적으로 완료되어야 환불 검토가 가능합니다.

## TICKET_ORDER_NOT_CONFIRMED

티켓 주문이 확정 상태가 아니면 환불을 진행할 수 없습니다.
서버가 확인한 ticketOrderStatus가 CONFIRMED가 아닌 경우이며, 주문 확정 여부는 Java 정책 결과를 기준으로 안내해야 합니다.

## REFUND_AMOUNT_NOT_POSITIVE

환불 가능한 금액이 0보다 크지 않으면 환불을 진행할 수 없습니다.
실제 환불 가능 금액은 서버가 계산한 refundAmount를 기준으로 하며, 문서가 별도의 금액 계산을 대신하지 않습니다.

## OPERATION_CUTOFF_PASSED

서버가 계산한 환불 가능 마감 시각(operationCutoffAt)이 지난 경우 환불이 제한됩니다.
마감 시각 계산은 Java EventOperationDeadlinePolicy와 RefundEligibilityPolicy의 결과를 따르며, 문서의 설명이 서버 계산값을 변경할 수 없습니다.

## EXCHANGE_CODE_ALREADY_REDEEMED

교환 코드가 이미 사용된 주문은 환불할 수 없습니다.
이미 사용된 교환 코드는 입장권 또는 현장 처리에 사용된 상태이므로, 서버가 exchangeCodeRedeemed를 true로 판단하면 환불 제한 사유로 설명해야 합니다.
