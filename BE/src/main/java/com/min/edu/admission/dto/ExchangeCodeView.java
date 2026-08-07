package com.min.edu.admission.dto;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import java.time.OffsetDateTime;

public interface ExchangeCodeView {
    Long getExchangeCodeId();
    Long getEventId();
    String getEventName();
    String getCode();
    Long getTicketOrderId();
    Long getExchangeCodeRequestId();
    ExchangeCodeStatus getStatus();
    String getHolderNickname();
    OffsetDateTime getExpiresAt();
    OffsetDateTime getRedeemedAt();
    OffsetDateTime getCreatedAt();
}
