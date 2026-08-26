package com.min.edu.admission.dto;

import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import java.time.OffsetDateTime;

public interface AdmissionTicketView {
    Long getAdmissionTicketId();
    Long getEventId();
    String getEventName();
    Long getMemberId();
    String getMemberNickname();
    AdmissionTicketStatus getStatus();
    ExchangeCodeStatus getExchangeCodeStatus();
    OffsetDateTime getIssuedAt();
    OffsetDateTime getUsedAt();
    OffsetDateTime getCancelledAt();
    String getQrToken();
}
