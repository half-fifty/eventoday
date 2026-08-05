package com.min.edu.payment.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface PaymentDetailProjection {

    Long getPaymentId();

    Long getPaymentOrderId();

    String getOrderNo();

    Long getBuyerMemberId();

    Long getTicketOrderId();

    Long getEventId();

    String getEventName();

    String getPgProvider();

    String getMethod();

    BigDecimal getAmount();

    String getPaymentStatus();

    String getTicketOrderStatus();

    OffsetDateTime getRequestedAt();

    OffsetDateTime getApprovedAt();
}
