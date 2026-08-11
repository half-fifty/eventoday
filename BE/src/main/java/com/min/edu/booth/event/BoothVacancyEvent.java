package com.min.edu.booth.event;

import org.springframework.context.ApplicationEvent;

/**
 * 부스 예약 취소 시 빈자리 알림 이벤트
 * - 커밋 후 비동기로 발행
 * - idempotencyKey로 재처리 시 중복 방지
 * - eventTimestamp로 결정적 ID 생성 가능
 */
public class BoothVacancyEvent extends ApplicationEvent {

    private final Long boothId;
    private final Long slotId;
    private final Long idempotencyKey;      // 재처리 시 중복 방지
    private final String displayName;
    private final Long eventTimestamp;      // 이벤트 발생 시간 (결정적 ID 생성용)

    public BoothVacancyEvent(Object source, Long boothId, Long slotId, Long idempotencyKey, String displayName) {
        super(source);
        this.boothId = boothId;
        this.displayName = displayName;
        this.slotId = slotId;
        this.idempotencyKey = idempotencyKey;
        this.eventTimestamp = System.currentTimeMillis();  // 생성 시점 자동 기록
    }

    public BoothVacancyEvent(Object source, Long boothId, Long slotId, Long idempotencyKey, String displayName, Long eventTimestamp) {
        super(source);
        this.boothId = boothId;
        this.displayName = displayName;
        this.slotId = slotId;
        this.idempotencyKey = idempotencyKey;
        this.eventTimestamp = eventTimestamp;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Long getBoothId() {
        return boothId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public Long getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getEventTimestamp() {
        return eventTimestamp;
    }
}