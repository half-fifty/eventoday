package com.min.edu.payment.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface RefundPaymentProjection {

    Long getPaymentId();

    Long getPaymentOrderId();

    String getPaymentKey();

    BigDecimal getPaymentAmount();

    String getPaymentStatus();

    default String getPaymentMethod() {
        return null;
    }

    String getOrderNo();

    Long getBuyerMemberId();

    BigDecimal getTotalAmount();

    String getPaymentOrderStatus();

    Long getTicketOrderId();

    Long getEventId();

    String getEventName();

    OffsetDateTime getEventStartAt();

    OffsetDateTime getEventEndAt();

    Integer getQuantity();

    String getTicketOrderStatus();
}
