package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.repository.PaymentDetailProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PaymentDetailResponse {

    private Long paymentId;
    private String orderNo;
    private Long ticketOrderId;
    private Long eventId;
    private String eventName;
    private String pgProvider;
    private String method;
    private BigDecimal amount;
    private String paymentStatus;
    private String ticketOrderStatus;
    private OffsetDateTime requestedAt;
    private OffsetDateTime approvedAt;

    public static PaymentDetailResponse from(PaymentDetailProjection projection) {
        return new PaymentDetailResponse(
            projection.getPaymentId(),
            projection.getOrderNo(),
            projection.getTicketOrderId(),
            projection.getEventId(),
            projection.getEventName(),
            projection.getPgProvider(),
            projection.getMethod(),
            projection.getAmount(),
            projection.getPaymentStatus(),
            projection.getTicketOrderStatus(),
            projection.getRequestedAt(),
            projection.getApprovedAt()
        );
    }
}
