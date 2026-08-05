package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.domain.PaymentRefund;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CreateRefundResponse {

    private Long refundId;
    private Long paymentId;
    private String orderNo;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundReason;
    private OffsetDateTime requestedAt;
    private OffsetDateTime completedAt;

    public static CreateRefundResponse of(PaymentRefund refund, String orderNo) {
        return new CreateRefundResponse(
            refund.getId(),
            refund.getPaymentId(),
            orderNo,
            refund.getRefundAmount(),
            refund.getStatus().name(),
            refund.getReason(),
            refund.getRequestedAt(),
            refund.getCompletedAt()
        );
    }
}
