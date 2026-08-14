package com.min.edu.payment.service;

import java.time.OffsetDateTime;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RefundAttemptRecorder {

    private final PaymentRefundRepository paymentRefundRepository;

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
                existingRefund.retry(
                    requesterMemberId,
                    payment.getPaymentAmount(),
                    request.getReason(),
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
        return paymentRefundRepository.saveAndFlush(refund);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long refundId) {
        PaymentRefund refund = paymentRefundRepository.findById(refundId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_NOT_FOUND));
        refund.fail(OffsetDateTime.now());
    }

    public CreateRefundResponse completedResponse(PaymentRefund refund, String orderNo) {
        return CreateRefundResponse.of(refund, orderNo);
    }
}
