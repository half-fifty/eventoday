package com.min.edu.payment.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public interface TicketOrderDetailProjection {

    Long getTicketOrderId();

    Long getPaymentId();

    String getOrderNo();

    Long getBuyerMemberId();

    Long getEventId();

    String getEventName();

    Integer getQuantity();

    BigDecimal getUnitPrice();

    BigDecimal getTotalAmount();

    String getPaymentOrderStatus();

    String getTicketOrderStatus();

    default String getPaymentMethod() {
        return null;
    }

    default String getVirtualAccountBankCode() {
        return null;
    }

    default String getVirtualAccountNumber() {
        return null;
    }

    default String getVirtualAccountCustomerName() {
        return null;
    }

    default OffsetDateTime getVirtualAccountDueAt() {
        return null;
    }

    OffsetDateTime getExpiresAt();

    OffsetDateTime getConfirmedAt();

    OffsetDateTime getCreatedAt();
}
