package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.repository.RefundDetailProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefundDetailResponse {

    private Long refundId;
    private String refundStatus;
    private BigDecimal refundAmount;
    private String refundReason;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;
    private Long paymentId;
    private String orderNo;
    private Long ticketOrderId;
    private Long eventId;
    private String eventName;
    private String paymentMethod;
    private String ticketOrderStatus;

    public static RefundDetailResponse from(RefundDetailProjection projection) {
        return new RefundDetailResponse(
            projection.getRefundId(),
            projection.getRefundStatus(),
            projection.getRefundAmount(),
            projection.getRefundReason(),
            projection.getRequestedAt(),
            projection.getCompletedAt(),
            projection.getPaymentId(),
            projection.getOrderNo(),
            projection.getTicketOrderId(),
            projection.getEventId(),
            projection.getEventName(),
            projection.getPaymentMethod(),
            projection.getTicketOrderStatus()
        );
    }
}
