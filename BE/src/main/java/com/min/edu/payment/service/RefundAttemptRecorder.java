package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundAttemptRecorder {

    private final PaymentRefundRepository paymentRefundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentAuditLogWriter auditLogWriter;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PaymentRefund prepare(
            RefundPaymentProjection payment,
            Long requesterMemberId,
            CreateRefundRequest request,
            OffsetDateTime refundAttemptedAt) {
        PaymentRefund existingRefund = paymentRefundRepository
            .findByPaymentId(payment.getPaymentId())
            .orElse(null);

        if (existingRefund != null) {
            if (existingRefund.isCompleted()) {
                return existingRefund;
            }

            if (existingRefund.isFailed()) {
                String fromStatus = existingRefund.getStatus().name();
                existingRefund.retry(
                    requesterMemberId,
                    payment.getPaymentAmount(),
                    request.getReason(),
                    refundAttemptedAt
                );
                auditLogWriter.append(
                    payment.getPaymentOrderId(),
                    payment.getPaymentId(),
                    existingRefund.getId(),
                    PaymentAuditEventType.REFUND_REQUESTED,
                    fromStatus,
                    existingRefund.getStatus().name(),
                    PaymentAuditSource.REFUND,
                    "RETRY_AFTER_FAILED",
                    requesterMemberId,
                    null,
                    refundAttemptedAt
                );
                return existingRefund;
            }

            throw new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
        }

        PaymentRefund refund = PaymentRefund.requested(
            payment.getPaymentId(),
            requesterMemberId,
            payment.getPaymentAmount(),
            request.getReason(),
            refundAttemptedAt
        );
        PaymentRefund savedRefund = paymentRefundRepository.saveAndFlush(refund);
        auditLogWriter.append(
            payment.getPaymentOrderId(),
            payment.getPaymentId(),
            savedRefund.getId(),
            PaymentAuditEventType.REFUND_REQUESTED,
            null,
            savedRefund.getStatus().name(),
            PaymentAuditSource.REFUND,
            null,
            requesterMemberId,
            null,
            refundAttemptedAt
        );
        return savedRefund;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long refundId) {
        PaymentRefund refund = paymentRefundRepository.findById(refundId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_NOT_FOUND));
        String fromStatus = refund.getStatus().name();
        refund.fail(OffsetDateTime.now());
        Long paymentOrderId = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND))
            .getPaymentOrderId();
        auditLogWriter.append(
            paymentOrderId,
            refund.getPaymentId(),
            refund.getId(),
            PaymentAuditEventType.REFUND_FAILED,
            fromStatus,
            refund.getStatus().name(),
            PaymentAuditSource.REFUND,
            null,
            refund.getRequesterMemberId(),
            null,
            refund.getCompletedAt()
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAmbiguous(Long refundId, String reasonCode) {
        PaymentRefund refund = paymentRefundRepository.findById(refundId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_NOT_FOUND));
        Long paymentOrderId = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND))
            .getPaymentOrderId();
        auditLogWriter.append(
            paymentOrderId,
            refund.getPaymentId(),
            refund.getId(),
            PaymentAuditEventType.REFUND_AMBIGUOUS,
            refund.getStatus().name(),
            refund.getStatus().name(),
            PaymentAuditSource.REFUND,
            reasonCode,
            refund.getRequesterMemberId(),
            null,
            OffsetDateTime.now()
        );
    }

    public CreateRefundResponse completedResponse(PaymentRefund refund, String orderNo) {
        return CreateRefundResponse.of(refund, orderNo);
    }
}
