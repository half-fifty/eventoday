# Admission Policy

## EVENT_MISMATCH

입장권의 교환 코드가 요청된 행사와 일치하지 않으면 입장이 제한됩니다.
요청 행사와 교환 코드의 행사 식별자는 Java AdmissionEligibilityPolicy가 비교하며, 문서는 이 판단을 변경하지 않습니다.

## EVENT_NOT_PUBLISHED

행사가 공개 상태가 아니면 입장이 제한됩니다.
서버가 확인한 eventStatus가 PUBLISHED가 아닌 경우이며, 공개 상태 전환 여부는 운영자가 실제 행사 상태를 확인해야 합니다.

## EVENT_ENDED

행사가 이미 종료된 경우 입장이 제한됩니다.
종료 여부는 서버가 확인한 eventEndAt과 평가 시각을 기준으로 Java AdmissionEligibilityPolicy가 판단합니다.

## ALREADY_USED

이미 사용 처리된 입장권은 중복 입장에 사용할 수 없습니다.
서버가 ticketStatus를 USED로 판단하고 usedAt이 존재할 수 있으며, 이 경우 현장 staff 또는 행사 운영자가 실제 사용 이력을 확인해야 합니다.

## TICKET_NOT_ISSUED

입장권이 발급 완료 상태가 아니면 입장이 제한됩니다.
서버가 확인한 ticketStatus가 ISSUED가 아닌 경우이며, 발급 상태나 주문 처리 상태를 운영자가 확인해야 합니다.
