package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.repository.RefundListProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RefundListItemResponse {

    private Long refundId;
    private Long paymentId;
    private String orderNo;
    private Long eventId;
    private String eventName;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundReason;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;

    public static RefundListItemResponse from(RefundListProjection projection) {
        return new RefundListItemResponse(
            projection.getRefundId(),
            projection.getPaymentId(),
            projection.getOrderNo(),
            projection.getEventId(),
            projection.getEventName(),
            projection.getRefundAmount(),
            projection.getRefundStatus(),
            projection.getRefundReason(),
            projection.getRequestedAt(),
            projection.getCompletedAt()
        );
    }
}
