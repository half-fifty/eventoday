package com.min.edu.payment.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.payment.repository.TicketOrderDetailProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class TicketOrderOperationQueryService {

    private final TicketOrderRepository ticketOrderRepository;
    private final EventOperationAccessService eventOperationAccessService;

    public TicketOrderOperationView getTicketOrderStatus(
            Long eventId,
            Long memberId,
            String orderNo) {
        eventOperationAccessService.requireOperationalAccess(eventId, memberId);
        if (orderNo == null || orderNo.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        TicketOrderDetailProjection order = ticketOrderRepository
            .findTicketOrderDetailByOrderNo(orderNo.trim())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.TICKET_ORDER_NOT_FOUND));
        if (!eventId.equals(order.getEventId())) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }
        return TicketOrderOperationView.from(order);
    }

    public record TicketOrderOperationView(
            String orderNo,
            String eventName,
            Integer quantity,
            BigDecimal totalAmount,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime expiresAt,
            OffsetDateTime confirmedAt) {

        static TicketOrderOperationView from(TicketOrderDetailProjection order) {
            return new TicketOrderOperationView(
                order.getOrderNo(),
                order.getEventName(),
                order.getQuantity(),
                order.getTotalAmount(),
                order.getPaymentOrderStatus(),
                order.getTicketOrderStatus(),
                order.getExpiresAt(),
                order.getConfirmedAt()
            );
        }
    }
}
