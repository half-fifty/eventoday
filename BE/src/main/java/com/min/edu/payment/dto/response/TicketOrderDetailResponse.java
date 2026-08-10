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
    private OffsetDateTime expiresAt;
    private OffsetDateTime confirmedAt;
    private OffsetDateTime createdAt;
    private List<TicketOrderExchangeCodeResponse> exchangeCodes;

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
            projection.getExpiresAt(),
            projection.getConfirmedAt(),
            projection.getCreatedAt(),
            exchangeCodes.stream()
                .map(TicketOrderExchangeCodeResponse::from)
                .toList()
        );
    }
}
