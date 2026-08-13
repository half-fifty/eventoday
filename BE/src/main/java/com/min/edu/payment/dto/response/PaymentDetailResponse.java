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
    private ConfirmPaymentResponse.VirtualAccountResponse virtualAccount;

    public PaymentDetailResponse(
            Long paymentId,
            String orderNo,
            Long ticketOrderId,
            Long eventId,
            String eventName,
            String pgProvider,
            String method,
            BigDecimal amount,
            String paymentStatus,
            String ticketOrderStatus,
            OffsetDateTime requestedAt,
            OffsetDateTime approvedAt) {
        this(
            paymentId,
            orderNo,
            ticketOrderId,
            eventId,
            eventName,
            pgProvider,
            method,
            amount,
            paymentStatus,
            ticketOrderStatus,
            requestedAt,
            approvedAt,
            null
        );
    }

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
            projection.getApprovedAt(),
            virtualAccount(projection)
        );
    }

    private static ConfirmPaymentResponse.VirtualAccountResponse virtualAccount(
            PaymentDetailProjection projection) {
        if (projection.getVirtualAccountNumber() == null) {
            return null;
        }

        return new ConfirmPaymentResponse.VirtualAccountResponse(
            projection.getVirtualAccountBankCode(),
            projection.getVirtualAccountNumber(),
            projection.getVirtualAccountCustomerName(),
            projection.getAmount(),
            projection.getVirtualAccountDueAt()
        );
    }
}
