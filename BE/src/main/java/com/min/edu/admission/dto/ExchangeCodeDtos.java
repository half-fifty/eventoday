package com.min.edu.admission.dto;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import java.time.OffsetDateTime;

public final class ExchangeCodeDtos {
    private ExchangeCodeDtos() {}

    public enum Source {
        TICKET_ORDER,
        EXTERNAL_REQUEST
    }

    public record EventListResponse(
            Long exchangeCodeId,
            Long eventId,
            String eventName,
            String maskedCode,
            Source source,
            ExchangeCodeStatus status,
            String holderNickname,
            OffsetDateTime expiresAt,
            OffsetDateTime redeemedAt,
            OffsetDateTime createdAt) {}

    public record MyListResponse(
            Long exchangeCodeId,
            Long eventId,
            String eventName,
            String code,
            Source source,
            ExchangeCodeStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime redeemedAt,
            OffsetDateTime createdAt) {}

    public record GuestOrderResponse(
            Long exchangeCodeId,
            Long eventId,
            String eventName,
            String code,
            ExchangeCodeStatus status,
            OffsetDateTime expiresAt,
            OffsetDateTime redeemedAt,
            OffsetDateTime createdAt) {}
}
