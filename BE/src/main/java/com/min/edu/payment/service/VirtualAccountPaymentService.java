package com.min.edu.payment.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentVirtualAccount;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.PaymentVirtualAccountRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.PaymentSecretHasher;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VirtualAccountPaymentService {

    private final PaymentOrderRepository paymentOrderRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentVirtualAccountRepository virtualAccountRepository;

    @Transactional
    public ConfirmPaymentResponse getWaitingForDeposit(
            PaymentOrder paymentOrder,
            String paymentKey) {
        PaymentOrder lockedOrder = paymentOrderRepository
            .findByOrderNoForUpdate(paymentOrder.getOrderNo())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));
        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        Payment payment = paymentRepository.findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
        if (!lockedOrder.isWaitingForDeposit()
                || !payment.isWaitingForDeposit()
                || !paymentKey.equals(payment.getPaymentKey())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        return waitingResponse(payment, lockedOrder, ticketOrder);
    }

    @Transactional
    public ConfirmPaymentResponse saveWaitingForDeposit(
            PaymentOrder paymentOrder,
            String paymentKey,
            TossConfirmResponse tossResponse) {
        PaymentOrder lockedOrder = paymentOrderRepository
            .findByOrderNoForUpdate(paymentOrder.getOrderNo())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(lockedOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        if (lockedOrder.isWaitingForDeposit()) {
            Payment payment = paymentRepository.findByPaymentOrderId(lockedOrder.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));
            if (!paymentKey.equals(payment.getPaymentKey())) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_ALREADY_PROCESSED);
            }
            return waitingResponse(payment, lockedOrder, ticketOrder);
        }

        if (!lockedOrder.isPending() || !ticketOrder.isPendingPayment()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_INVALID_STATE);
        }

        if (paymentRepository.findByPaymentOrderId(lockedOrder.getId()).isPresent()) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
        }

        validateVirtualAccountResponse(tossResponse);

        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = paymentRepository.saveAndFlush(Payment.waitingForDeposit(
            lockedOrder.getId(),
            PaymentProvider.TOSS_PAYMENTS,
            paymentKey,
            tossResponse.method(),
            tossResponse.totalAmount(),
            tossResponse.requestedAt(),
            now
        ));

        lockedOrder.markWaitingForDeposit(now);
        PaymentVirtualAccount virtualAccount = virtualAccountRepository.save(
            PaymentVirtualAccount.create(
                payment.getId(),
                tossResponse.virtualAccount().bankCode(),
                tossResponse.virtualAccount().accountNumber(),
                tossResponse.virtualAccount().customerName(),
                tossResponse.virtualAccount().dueDate(),
                PaymentSecretHasher.sha256(tossResponse.secret()),
                tossResponse.status(),
                now
            )
        );

        return ConfirmPaymentResponse.waitingForDeposit(
            payment,
            lockedOrder.getOrderNo(),
            ticketOrder,
            new ConfirmPaymentResponse.VirtualAccountResponse(
                virtualAccount.getBankCode(),
                virtualAccount.getAccountNumber(),
                virtualAccount.getCustomerName(),
                payment.getAmount(),
                virtualAccount.getDueAt()
            )
        );
    }

    private ConfirmPaymentResponse waitingResponse(
            Payment payment,
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder) {
        PaymentVirtualAccount virtualAccount = virtualAccountRepository
            .findByPaymentId(payment.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT));

        return ConfirmPaymentResponse.waitingForDeposit(
            payment,
            paymentOrder.getOrderNo(),
            ticketOrder,
            new ConfirmPaymentResponse.VirtualAccountResponse(
                virtualAccount.getBankCode(),
                virtualAccount.getAccountNumber(),
                virtualAccount.getCustomerName(),
                payment.getAmount(),
                virtualAccount.getDueAt()
            )
        );
    }

    private void validateVirtualAccountResponse(TossConfirmResponse tossResponse) {
        if (tossResponse == null) {
            throw invalidVirtualAccountResponse("RESPONSE_MISSING", null);
        }
        if (tossResponse.secret() == null || tossResponse.secret().isBlank()) {
            throw invalidVirtualAccountResponse("SECRET_MISSING", tossResponse);
        }
        if (tossResponse.virtualAccount() == null) {
            throw invalidVirtualAccountResponse("VIRTUAL_ACCOUNT_MISSING", tossResponse);
        }
        if (tossResponse.virtualAccount().accountNumber() == null
                || tossResponse.virtualAccount().accountNumber().isBlank()) {
            throw invalidVirtualAccountResponse("ACCOUNT_NUMBER_MISSING", tossResponse);
        }
        if (tossResponse.virtualAccount().bankCode() == null
                || tossResponse.virtualAccount().bankCode().isBlank()) {
            throw invalidVirtualAccountResponse("BANK_CODE_MISSING", tossResponse);
        }
        if (tossResponse.virtualAccount().dueDate() == null) {
            throw invalidVirtualAccountResponse("DUE_DATE_MISSING", tossResponse);
        }
    }

    private BusinessException invalidVirtualAccountResponse(
            String reason,
            TossConfirmResponse tossResponse) {
        log.warn(
            "Invalid Toss virtual account response: reason={}, orderId={}, status={}, method={}",
            reason,
            tossResponse == null ? null : tossResponse.orderId(),
            tossResponse == null ? null : tossResponse.status(),
            tossResponse == null ? null : tossResponse.method()
        );
        return new BusinessException(GlobalErrorCode.VIRTUAL_ACCOUNT_REQUIRED);
    }
}
