package com.min.edu.payment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.MyRefundListResponse;
import com.min.edu.payment.dto.response.RefundDetailResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.RefundDetailProjection;
import com.min.edu.payment.repository.RefundListProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefundQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PaymentRefundRepository paymentRefundRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;

    @Transactional(readOnly = true)
    public MyRefundListResponse getMyRefunds(Long memberId, int page, int size) {
        if (memberId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        validatePageRequest(page, size);

        Page<RefundListProjection> refunds =
            paymentRefundRepository.findMyRefunds(memberId, PageRequest.of(page, size));
        return MyRefundListResponse.from(refunds);
    }

    @Transactional(readOnly = true)
    public RefundDetailResponse getRefundDetail(
            Long memberId,
            String orderAccessToken,
            Long refundId) {
        RefundDetailProjection refund = paymentRefundRepository
            .findRefundDetailById(refundId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_NOT_FOUND));

        validateAccess(memberId, orderAccessToken, refund);

        return RefundDetailResponse.from(refund);
    }

    private void validateAccess(
            Long memberId,
            String orderAccessToken,
            RefundDetailProjection refund) {
        if (refund.getBuyerMemberId() != null) {
            if (memberId == null) {
                throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
            }

            if (!refund.getBuyerMemberId().equals(memberId)) {
                throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
            }

            return;
        }

        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);
        if (!refund.getOrderNo().equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.REFUND_ACCESS_DENIED);
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
