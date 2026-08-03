package com.min.edu.payment.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.dto.response.TicketOrderDetailResponse;
import com.min.edu.payment.dto.response.MyTicketOrderListResponse;
import com.min.edu.payment.repository.TicketOrderDetailProjection;
import com.min.edu.payment.repository.TicketOrderListProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;

import java.util.List;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TicketOrderQueryService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TicketOrderRepository ticketOrderRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final OrderAccessTokenProvider orderAccessTokenProvider;

    @Transactional(readOnly = true)
    public MyTicketOrderListResponse getMyTicketOrders(
            Long memberId,
            int page,
            int size) {
        validateAuthenticated(memberId);
        validatePageRequest(page, size);

        Page<TicketOrderListProjection> orders =
            ticketOrderRepository.findMyTicketOrders(memberId, PageRequest.of(page, size));

        return MyTicketOrderListResponse.from(orders);
    }

    @Transactional(readOnly = true)
    public TicketOrderDetailResponse getTicketOrderDetail(
            String orderNo,
            Long memberId,
            String orderAccessToken) {
        TicketOrderDetailProjection order = ticketOrderRepository
            .findTicketOrderDetailByOrderNo(orderNo)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND));

        validateAccess(order, memberId, orderAccessToken);

        List<ExchangeCode> exchangeCodes =
            exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(order.getTicketOrderId());

        return TicketOrderDetailResponse.from(order, exchangeCodes);
    }

    private void validateAccess(
            TicketOrderDetailProjection order,
            Long memberId,
            String orderAccessToken) {
        if (order.getBuyerMemberId() != null) {
            validateMemberOrderAccess(order.getBuyerMemberId(), memberId);
            return;
        }

        validateGuestOrderAccess(order.getOrderNo(), orderAccessToken);
    }

    private void validateMemberOrderAccess(Long buyerMemberId, Long memberId) {
        if (memberId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        if (!buyerMemberId.equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private void validateGuestOrderAccess(String orderNo, String orderAccessToken) {
        if (orderAccessToken == null || orderAccessToken.isBlank()) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);
        }

        String tokenOrderNo = orderAccessTokenProvider.getOrderNo(orderAccessToken);

        if (!orderNo.equals(tokenOrderNo)) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }
    }

    private void validateAuthenticated(Long memberId) {
        if (memberId == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
