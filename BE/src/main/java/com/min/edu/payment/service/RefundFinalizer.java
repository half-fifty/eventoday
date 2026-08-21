package com.min.edu.payment.service;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.policy.RefundEligibilityPolicy;
import com.min.edu.payment.policy.RefundEligibilityPolicy.RefundEligibilityInput;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.toss.dto.TossCancelResponse;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundFinalizer {

    private static final String TOSS_CANCELED_STATUS = "CANCELED";

    private final EntityManager entityManager;
    private final PaymentFinalizationProperties properties;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final TicketOrderRepository ticketOrderRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final TicketInventoryGateway ticketInventoryGateway;
    private final RefundEligibilityPolicy refundEligibilityPolicy;
    private final PaymentAuditLogWriter auditLogWriter;

    @Transactional
    public CreateRefundResponse finalizeRefund(
            Long refundId,
            Long paymentId,
            Long requesterMemberId,
            CreateRefundRequest request,
            TossCancelResponse tossResponse) {
        setLocalLockTimeout(properties.getFinalizationLockTimeoutMs());

        RefundPaymentProjection projection = paymentRepository
            .findRefundPaymentById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));

        PaymentOrder paymentOrder = paymentOrderRepository
            .findByOrderNoForUpdate(projection.getOrderNo())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND));

        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));

        TicketOrder ticketOrder = ticketOrderRepository
            .findByPaymentOrderId(paymentOrder.getId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));

        validateDataConsistency(projection, payment, paymentOrder, ticketOrder);

        PaymentRefund refund = paymentRefundRepository.findById(refundId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_NOT_FOUND));
        if (!refund.getPaymentId().equals(paymentId)) {
            throw new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT);
        }
        if (refund.isCompleted()) {
            return CreateRefundResponse.of(refund, paymentOrder.getOrderNo());
        }

        validateRefundable(projection, payment, paymentOrder, ticketOrder, refund);
        validateTossCancelResponse(projection, tossResponse);

        OffsetDateTime now = OffsetDateTime.now();
        String paymentFromStatus = payment.getStatus();
        String refundFromStatus = refund.getStatus().name();
        List<ExchangeCode> exchangeCodes =
            exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId());
        if (exchangeCodes.size() != ticketOrder.getTotalQuantity()) {
            throw new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT);
        }

        for (ExchangeCode exchangeCode : exchangeCodes) {
            exchangeCode.cancel(now);
        }

        if (!ticketInventoryGateway.release(
                ticketOrder.getEventId(),
                ticketOrder.getTotalQuantity())) {
            throw new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT);
        }

        payment.markRefunded(now);
        paymentOrder.markRefunded(now);
        ticketOrder.refund(now);

        refund.complete(cancelTransactionKey(tossResponse, payment.getAmount()), now);
        auditLogWriter.append(
            paymentOrder.getId(),
            payment.getId(),
            refund.getId(),
            PaymentAuditEventType.PAYMENT_REFUNDED,
            paymentFromStatus,
            payment.getStatus(),
            PaymentAuditSource.REFUND,
            null,
            PaymentAuditActorType.MEMBER,
            requesterMemberId,
            null,
            now
        );
        auditLogWriter.append(
            paymentOrder.getId(),
            payment.getId(),
            refund.getId(),
            PaymentAuditEventType.REFUND_COMPLETED,
            refundFromStatus,
            refund.getStatus().name(),
            PaymentAuditSource.REFUND,
            null,
            PaymentAuditActorType.MEMBER,
            requesterMemberId,
            null,
            now
        );

        PaymentRefund savedRefund = paymentRefundRepository.saveAndFlush(refund);
        return CreateRefundResponse.of(savedRefund, paymentOrder.getOrderNo());
    }

    private void validateDataConsistency(
            RefundPaymentProjection projection,
            Payment payment,
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder) {
        if (!payment.getPaymentOrderId().equals(paymentOrder.getId())
                || !paymentOrder.getId().equals(projection.getPaymentOrderId())
                || !ticketOrder.getId().equals(projection.getTicketOrderId())) {
            throw new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT);
        }
    }

    private void validateRefundable(
            RefundPaymentProjection projection,
            Payment payment,
            PaymentOrder paymentOrder,
            TicketOrder ticketOrder,
            PaymentRefund refund) {
        boolean exchangeCodeRedeemed = exchangeCodeRepository.existsByTicketOrderIdAndStatus(
                ticketOrder.getId(),
                ExchangeCodeStatus.REDEEMED);
        refundEligibilityPolicy.requireRefundable(refundEligibilityPolicy.evaluate(
            new RefundEligibilityInput(
                payment.getStatus(),
                paymentOrder.getStatus(),
                ticketOrder.getStatus(),
                payment.getAmount(),
                projection.getEventEndAt(),
                exchangeCodeRedeemed,
                refund.getRequestedAt()
            )
        ));
    }

    private void validateTossCancelResponse(
            RefundPaymentProjection projection,
            TossCancelResponse response) {
        if (response == null
                || !projection.getPaymentKey().equals(response.paymentKey())
                || !projection.getOrderNo().equals(response.orderId())
                || response.totalAmount() == null
                || response.totalAmount().compareTo(projection.getPaymentAmount()) != 0
                || !TOSS_CANCELED_STATUS.equals(response.status())) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private String cancelTransactionKey(
            TossCancelResponse response,
            BigDecimal refundAmount) {
        if (response.cancels() == null) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }

        return response.cancels().stream()
            .filter(cancel -> cancel.cancelAmount() != null
                && cancel.cancelAmount().compareTo(refundAmount) == 0
                && cancel.transactionKey() != null
                && !cancel.transactionKey().isBlank())
            .reduce((first, second) -> second)
            .map(TossCancelResponse.Cancel::transactionKey)
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
            ));
    }

    private void setLocalLockTimeout(long lockTimeoutMs) {
        entityManager
            .createNativeQuery("select set_config('lock_timeout', :timeout, true)")
            .setParameter("timeout", lockTimeoutMs + "ms")
            .getSingleResult();
    }
}
