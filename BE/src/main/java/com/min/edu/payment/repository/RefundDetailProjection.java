package com.min.edu.payment.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface RefundDetailProjection {

    Long getRefundId();

    Long getPaymentId();

    Long getPaymentOrderId();

    String getOrderNo();

    Long getBuyerMemberId();

    Long getTicketOrderId();

    Long getEventId();

    String getEventName();

    BigDecimal getRefundAmount();

    String getRefundReason();

    String getRefundStatus();

    OffsetDateTime getRequestedAt();

    OffsetDateTime getCompletedAt();

    String getPaymentMethod();

    String getTicketOrderStatus();
}
