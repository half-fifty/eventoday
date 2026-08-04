package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.TicketOrder;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ConfirmPaymentResponse {

    private Long paymentId;
    private String orderNo;
    private Long ticketOrderId;
    private BigDecimal amount;
    private String paymentStatus;
    private String ticketOrderStatus;
    private OffsetDateTime approvedAt;

    public static ConfirmPaymentResponse of(
            Payment payment,
            String orderNo,
            TicketOrder ticketOrder) {
        return new ConfirmPaymentResponse(
            payment.getId(),
            orderNo,
            ticketOrder.getId(),
            payment.getAmount(),
            payment.getStatus(),
            ticketOrder.getStatus(),
            payment.getApprovedAt()
        );
    }
}
