package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.repository.TicketOrderListProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketOrderListItemResponse {

    private Long ticketOrderId;
    private String orderNo;
    private Long eventId;
    private String eventName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Boolean paymentRequired;
    private String paymentOrderStatus;
    private String ticketOrderStatus;
    private OffsetDateTime expiresAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;

    public static TicketOrderListItemResponse from(TicketOrderListProjection projection) {
        return new TicketOrderListItemResponse(
            projection.getTicketOrderId(),
            projection.getOrderNo(),
            projection.getEventId(),
            projection.getEventName(),
            projection.getQuantity(),
            projection.getUnitPrice(),
            projection.getTotalAmount(),
            projection.getTotalAmount().compareTo(BigDecimal.ZERO) > 0,
            projection.getPaymentOrderStatus(),
            projection.getTicketOrderStatus(),
            projection.getExpiresAt(),
            projection.getConfirmedAt(),
            projection.getCreatedAt()
        );
    }
}
