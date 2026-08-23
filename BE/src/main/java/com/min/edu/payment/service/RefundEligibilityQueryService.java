package com.min.edu.payment.service;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.policy.RefundEligibilityPolicy;
import com.min.edu.payment.policy.RefundEligibilityPolicy.RefundEligibilityInput;
import com.min.edu.payment.policy.RefundEligibilityResult;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RefundEligibilityQueryService {

    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;
    private final RefundEligibilityPolicy refundEligibilityPolicy;

    public RefundEligibilityQueryService(
            PaymentRepository paymentRepository,
            PaymentRefundRepository paymentRefundRepository,
            ExchangeCodeRepository exchangeCodeRepository,
            OrderAccessTokenProvider orderAccessTokenProvider,
            RefundEligibilityPolicy refundEligibilityPolicy) {
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.orderAccessTokenProvider = orderAccessTokenProvider;
        this.refundEligibilityPolicy = refundEligibilityPolicy;
    }

    public RefundEligibilityView evaluate(
            Long memberId,
            String orderAccessToken,
            Long paymentId) {
        RefundPaymentProjection payment = paymentRepository
            .findRefundPaymentById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));
        validateAccess(payment, memberId, orderAccessToken);

        OffsetDateTime evaluatedAt = OffsetDateTime.now();
        boolean exchangeCodeRedeemed = exchangeCodeRepository.existsByTicketOrderIdAndStatus(
            payment.getTicketOrderId(),
            ExchangeCodeStatus.REDEEMED
        );
        RefundEligibilityResult eligibility = refundEligibilityPolicy.evaluate(
            new RefundEligibilityInput(
                payment.getPaymentStatus(),
                payment.getPaymentOrderStatus(),
                payment.getTicketOrderStatus(),
                payment.getPaymentAmount(),
                payment.getEventEndAt(),
                exchangeCodeRedeemed,
                evaluatedAt
            )
        );
        String refundStatus = paymentRefundRepository.findByPaymentId(paymentId)
            .map(PaymentRefund::getStatus)
            .map(Enum::name)
            .orElse(null);
        return new RefundEligibilityView(
            payment.getPaymentId(),
            payment.getTicketOrderId(),
            payment.getEventId(),
            payment.getEventName(),
            payment.getPaymentMethod(),
            refundStatus,
            payment.getPaymentAmount(),
            eligibility,
            evaluatedAt
        );
    }

    private void validateAccess(
            RefundPaymentProjection payment,
            Long memberId,
            String orderAccessToken) {
        if (payment.getBuyerMemberId() != null) {
            if (memberId == null) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }
            if (!payment.getBuyerMemberId().equals(memberId)) {
                throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
            }
            return;
        }

        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }
        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!payment.getOrderNo().equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
        }
    }
}
