package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.TicketOrder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateTicketOrderResponse {

    private String orderNo;
    private Long ticketOrderId;
    private Integer quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private Boolean paymentRequired;
    private String paymentOrderStatus;
    private String ticketOrderStatus;
    private OffsetDateTime expiresAt;
    private List<IssuedExchangeCodeResponse> exchangeCodes;

    public static CreateTicketOrderResponse paymentPending(
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder) {
        return new CreateTicketOrderResponse(
            paymentOrder.getOrderNo(),
            ticketOrder.getId(),
            ticketOrder.getTotalQuantity(),
            ticketOrder.getUnitPrice(),
            paymentOrder.getTotalAmount(),
            true,
            paymentOrder.getStatus(),
            null,
            paymentOrder.getExpiresAt(),
            null
        );
    }

    public static CreateTicketOrderResponse free(
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder,
            List<ExchangeCode> exchangeCodes) {
        return new CreateTicketOrderResponse(
            paymentOrder.getOrderNo(),
            ticketOrder.getId(),
            ticketOrder.getTotalQuantity(),
            ticketOrder.getUnitPrice(),
            paymentOrder.getTotalAmount(),
            false,
            null,
            ticketOrder.getStatus(),
            null,
            exchangeCodes.stream()
                .map(IssuedExchangeCodeResponse::from)
                .toList()
        );
    }
}
