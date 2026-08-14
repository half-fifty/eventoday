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
    private Long paymentId;
    private String orderNo;
    private Long eventId;
    private String eventName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Boolean paymentRequired;
    private String paymentOrderStatus;
    private String ticketOrderStatus;
    private String paymentMethod;
    private OffsetDateTime expiresAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private ConfirmPaymentResponse.VirtualAccountResponse virtualAccount;

    public TicketOrderListItemResponse(
            Long ticketOrderId,
            Long paymentId,
            String orderNo,
            Long eventId,
            String eventName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            Boolean paymentRequired,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime expiresAt,
            OffsetDateTime confirmedAt,
            OffsetDateTime createdAt) {
        this(
            ticketOrderId,
            paymentId,
            orderNo,
            eventId,
            eventName,
            quantity,
            unitPrice,
            totalAmount,
            paymentRequired,
            paymentOrderStatus,
            ticketOrderStatus,
            null,
            expiresAt,
            confirmedAt,
            createdAt,
            null
        );
    }

    public static TicketOrderListItemResponse from(TicketOrderListProjection projection) {
        return new TicketOrderListItemResponse(
            projection.getTicketOrderId(),
            projection.getPaymentId(),
            projection.getOrderNo(),
            projection.getEventId(),
            projection.getEventName(),
            projection.getQuantity(),
            projection.getUnitPrice(),
            projection.getTotalAmount(),
            projection.getTotalAmount().compareTo(BigDecimal.ZERO) > 0,
            projection.getPaymentOrderStatus(),
            projection.getTicketOrderStatus(),
            projection.getPaymentMethod(),
            projection.getExpiresAt(),
            projection.getConfirmedAt(),
            projection.getCreatedAt(),
            virtualAccount(projection)
        );
    }

    private static ConfirmPaymentResponse.VirtualAccountResponse virtualAccount(
            TicketOrderListProjection projection) {
        if (projection.getVirtualAccountNumber() == null) {
            return null;
        }

        return new ConfirmPaymentResponse.VirtualAccountResponse(
            projection.getVirtualAccountBankCode(),
            projection.getVirtualAccountNumber(),
            projection.getVirtualAccountCustomerName(),
            projection.getTotalAmount(),
            projection.getVirtualAccountDueAt()
        );
    }
}
