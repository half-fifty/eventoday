package com.min.edu.payment.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.advertisement.domain.Advertisement;

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
    private String paymentMethod;
    private VirtualAccountResponse virtualAccount;

    public ConfirmPaymentResponse(
            Long paymentId,
            String orderNo,
            Long ticketOrderId,
            BigDecimal amount,
            String paymentStatus,
            String ticketOrderStatus,
            OffsetDateTime approvedAt) {
        this(
            paymentId,
            orderNo,
            ticketOrderId,
            amount,
            paymentStatus,
            ticketOrderStatus,
            approvedAt,
            null,
            null
        );
    }

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
            payment.getApprovedAt(),
            payment.getMethod(),
            null
        );
    }

    public static ConfirmPaymentResponse waitingForDeposit(
            Payment payment,
            String orderNo,
            TicketOrder ticketOrder,
            VirtualAccountResponse virtualAccount) {
        return new ConfirmPaymentResponse(
            payment.getId(),
            orderNo,
            ticketOrder.getId(),
            payment.getAmount(),
            payment.getStatus(),
            ticketOrder.getStatus(),
            payment.getApprovedAt(),
            payment.getMethod(),
            virtualAccount
        );
    }

    public static ConfirmPaymentResponse ofEventAd(
            Payment payment,
            String orderNo,
            Advertisement advertisement) {
        return new ConfirmPaymentResponse(
            payment.getId(),
            orderNo,
            null,
            payment.getAmount(),
            payment.getStatus(),
            advertisement.getStatus().name(),
            payment.getApprovedAt(),
            payment.getMethod(),
            null
        );
    }

    public static ConfirmPaymentResponse waitingForDepositEventAd(
            Payment payment,
            String orderNo,
            Advertisement advertisement,
            VirtualAccountResponse virtualAccount) {
        return new ConfirmPaymentResponse(
            payment.getId(),
            orderNo,
            null,
            payment.getAmount(),
            payment.getStatus(),
            advertisement.getStatus().name(),
            payment.getApprovedAt(),
            payment.getMethod(),
            virtualAccount
        );
    }

    @Getter
    @AllArgsConstructor
    public static class VirtualAccountResponse {

        private String bankCode;
        private String accountNumber;
        private String customerName;
        private BigDecimal amount;
        private OffsetDateTime dueAt;
    }
}
