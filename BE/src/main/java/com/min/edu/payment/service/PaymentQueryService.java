package com.min.edu.payment.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.PaymentDetailResponse;
import com.min.edu.payment.repository.PaymentDetailProjection;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {

    private final PaymentRepository paymentRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;

    @Transactional(readOnly = true)
    public PaymentDetailResponse getPaymentDetail(
            Long memberId,
            String orderAccessToken,
            Long paymentId) {
        PaymentDetailProjection projection = paymentRepository
            .findPaymentDetailById(paymentId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_NOT_FOUND));

        validateAccess(memberId, orderAccessToken, projection);

        return PaymentDetailResponse.from(projection);
    }

    private void validateAccess(
            Long memberId,
            String orderAccessToken,
            PaymentDetailProjection projection) {
        if (projection.getBuyerMemberId() != null) {
            if (memberId == null) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }

            if (!projection.getBuyerMemberId().equals(memberId)) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_ACCESS_DENIED);
            }

            return;
        }

        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!projection.getOrderNo().equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_ACCESS_DENIED);
        }
    }
}
