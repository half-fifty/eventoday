package com.min.edu.admission.dto;

import com.min.edu.admission.domain.ExchangeCodeRequestStatus;
import java.time.OffsetDateTime;

public interface ExchangeCodeRequestView {
    Long getRequestId();
    Long getEventId();
    String getEventName();
    Long getRequestedBy();
    String getRequesterNickname();
    Integer getRequestedQuantity();
    String getPurpose();
    ExchangeCodeRequestStatus getStatus();
    Long getReviewedBy();
    OffsetDateTime getReviewedAt();
    String getRejectionReason();
    OffsetDateTime getEmailedAt();
    OffsetDateTime getCreatedAt();
}
