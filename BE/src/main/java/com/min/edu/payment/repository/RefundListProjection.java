package com.min.edu.payment.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface RefundListProjection {

    Long getRefundId();

    Long getPaymentId();

    String getOrderNo();

    Long getEventId();

    String getEventName();

    BigDecimal getRefundAmount();

    String getRefundReason();

    String getRefundStatus();

    OffsetDateTime getRequestedAt();

    OffsetDateTime getCompletedAt();
}
