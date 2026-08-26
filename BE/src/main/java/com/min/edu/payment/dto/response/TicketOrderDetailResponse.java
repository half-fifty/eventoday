package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.payment.repository.TicketOrderDetailProjection;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class TicketOrderDetailResponse {

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
    private List<TicketOrderExchangeCodeResponse> exchangeCodes;

    public TicketOrderDetailResponse(
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
            OffsetDateTime createdAt,
            List<TicketOrderExchangeCodeResponse> exchangeCodes) {
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
            null,
            exchangeCodes
        );
    }

    public static TicketOrderDetailResponse from(
            TicketOrderDetailProjection projection,
            List<ExchangeCode> exchangeCodes) {
        return new TicketOrderDetailResponse(
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
            virtualAccount(projection),
            exchangeCodes.stream()
                .map(TicketOrderExchangeCodeResponse::from)
                .toList()
        );
    }

    private static ConfirmPaymentResponse.VirtualAccountResponse virtualAccount(
            TicketOrderDetailProjection projection) {
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
